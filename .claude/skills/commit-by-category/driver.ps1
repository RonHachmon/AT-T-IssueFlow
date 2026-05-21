#Requires -Version 5.1
<#
.SYNOPSIS
Analyzes git changes and commits them by conventional commit category using Claude Haiku.

.DESCRIPTION
Groups unstaged and staged changes by category (feat, fix, docs, refactor, test, chore, perf, style)
and creates one commit per category with auto-generated conventional commit messages.

.PARAMETER DryRun
Show categorization without committing.

.PARAMETER NoPrompt
Commit without confirmation prompts.

.PARAMETER Model
Claude model to use (default: claude-haiku-4-5-20251001)
#>
param(
    [switch]$DryRun,
    [switch]$NoPrompt,
    [string]$Model = "claude-haiku-4-5-20251001"
)

$ErrorActionPreference = "Stop"

# Verify API key
if (-not $env:ANTHROPIC_API_KEY) {
    Write-Error "ANTHROPIC_API_KEY not set. Set it with: `$env:ANTHROPIC_API_KEY = 'sk-ant-...'"
}

# Get git changes
Write-Host "📋 Analyzing changes..." -ForegroundColor Cyan

$stagedDiff = git diff --cached --stat 2>$null
$unstagedDiff = git diff --stat 2>$null

# Get full diff for Claude
$fullDiff = git diff HEAD 2>$null
if (-not $fullDiff) {
    $fullDiff = git diff 2>$null
}

if (-not $fullDiff) {
    Write-Host "⚠️  No changes detected. Stage or modify files first." -ForegroundColor Yellow
    exit 0
}

# Get changed file list
$changedFiles = @()
$gitStatus = git status --porcelain
foreach ($line in $gitStatus) {
    if ($line -match '^\s*[AMD?]{1,2}\s+(.+)$') {
        $changedFiles += $Matches[1]
    }
}

if ($changedFiles.Count -eq 0) {
    Write-Host "⚠️  No changes detected." -ForegroundColor Yellow
    exit 0
}

Write-Host "✅ Found $($changedFiles.Count) changed file(s)`n" -ForegroundColor Green

# Prepare prompt for Claude
$prompt = @"
Analyze these git changes and categorize them by conventional commit type.

Changed files and their diff:
\`\`\`diff
$fullDiff
\`\`\`

Group these changes into categories: feat, fix, docs, refactor, test, chore, perf, style.

Return ONLY a JSON object (no markdown, no explanation) with this exact format:
{
  "categories": {
    "feat": {
      "files": ["src/auth.ts"],
      "message": "add JWT authentication module"
    },
    "docs": {
      "files": ["README.md"],
      "message": "update authentication documentation"
    }
  }
}

Rules:
- Each category gets ONE commit
- message is lowercase, imperative, concise (max 50 chars)
- If no changes for a category, omit it
- Files must exist in the changed files list
- One file per category only
"@

# Call Claude API
Write-Host "🤖 Calling Claude Haiku for categorization..." -ForegroundColor Cyan

$apiPayload = @{
    model       = $Model
    max_tokens  = 1024
    messages    = @(
        @{
            role    = "user"
            content = $prompt
        }
    )
} | ConvertTo-Json -Depth 10

try {
    $response = Invoke-WebRequest `
        -Uri "https://api.anthropic.com/v1/messages" `
        -Method Post `
        -Headers @{
            "x-api-key"       = $env:ANTHROPIC_API_KEY
            "anthropic-version" = "2023-06-01"
            "content-type"    = "application/json"
        } `
        -Body $apiPayload `
        -ErrorAction Stop

    $responseBody = $response.Content | ConvertFrom-Json
    $categorization = $responseBody.content[0].text

    # Parse JSON response
    try {
        $categories = $categorization | ConvertFrom-Json
    }
    catch {
        Write-Error "Failed to parse Claude response: $categorization"
    }
}
catch {
    Write-Error "API call failed: $($_.Exception.Message)"
}

# Display categorization
Write-Host "`n📂 Categories detected:`n" -ForegroundColor Cyan
foreach ($cat in $categories.categories.PSObject.Properties) {
    $name = $cat.Name
    $data = $cat.Value
    $fileCount = @($data.files).Count
    Write-Host "  [$name] $($data.message) ($fileCount file$(if ($fileCount -ne 1) { 's' }))" -ForegroundColor Green
}

if ($DryRun) {
    Write-Host "`n✨ Dry run complete. Remove -DryRun to commit." -ForegroundColor Yellow
    exit 0
}

# Confirm before committing
if (-not $NoPrompt) {
    Write-Host "`n" -ForegroundColor White
    $response = Read-Host "Proceed with commits? (y/n)"
    if ($response -ne "y") {
        Write-Host "Cancelled." -ForegroundColor Yellow
        exit 0
    }
}

# Commit per category
Write-Host "`n📝 Committing changes..." -ForegroundColor Cyan
$commitCount = 0

foreach ($cat in $categories.categories.PSObject.Properties) {
    $category = $cat.Name
    $message = $cat.Value.message
    $files = $cat.Value.files

    # Stage files for this category
    foreach ($file in $files) {
        if (Test-Path $file) {
            git add $file 2>$null
        }
    }

    # Create commit
    $fullMessage = "$category`: $message"
    try {
        git commit -m $fullMessage 2>$null
        Write-Host "  ✅ [$category] $message" -ForegroundColor Green
        $commitCount++
    }
    catch {
        Write-Host "  ❌ [$category] Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "`n✨ Done! $commitCount commit$(if ($commitCount -ne 1) { 's' }) created." -ForegroundColor Green

# Show commit log
Write-Host "`n📋 Recent commits:" -ForegroundColor Cyan
git log --oneline -n $commitCount
