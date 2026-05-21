---
name: commit-by-category
description: Analyze git changes and commit by conventional type (feat, fix, docs, etc) using Claude Haiku
model: haiku
---

# Commit Changes by Category

Analyzes your git changes using Claude Haiku, groups them by conventional commit type (feat, fix, docs, refactor, test, chore, perf, style), and creates one commit per category with auto-generated messages.

## Prerequisites

- PowerShell 5.1+
- Git
- `ANTHROPIC_API_KEY` environment variable set

## Build

No build needed. The driver is a standalone PowerShell script.

## Run (agent path)

```powershell
powershell -ExecutionPolicy Bypass -File .\.claude\skills\commit-by-category\driver.ps1
```

The script will:
1. Analyze staged and unstaged changes
2. Send the diff to Claude Haiku for categorization
3. Display detected categories and proposed commits
4. Prompt for confirmation (use `-NoPrompt` to skip)
5. Commit each category separately with conventional format

**Example output:**
```
📋 Analyzing changes...
✅ Found 5 changed file(s)

🤖 Calling Claude Haiku for categorization...

📂 Categories detected:

  [feat] add JWT authentication module (2 files)
  [docs] update API documentation (1 file)
  [refactor] extract error handling logic (2 files)

Proceed with commits? (y/n): y

📝 Committing changes...
  ✅ [feat] add JWT authentication module
  ✅ [docs] update API documentation
  ✅ [refactor] extract error handling logic

✨ Done! 3 commits created.

📋 Recent commits:
abc1234 refactor: extract error handling logic
def5678 docs: update API documentation
ghi9012 feat: add JWT authentication module
```

## Options

```powershell
# Dry run: analyze without committing
powershell -ExecutionPolicy Bypass -File .\.claude\skills\commit-by-category\driver.ps1 -DryRun

# Auto-commit without prompts
powershell -ExecutionPolicy Bypass -File .\.claude\skills\commit-by-category\driver.ps1 -NoPrompt

# Use a different Claude model
powershell -ExecutionPolicy Bypass -File .\.claude\skills\commit-by-category\driver.ps1 -Model claude-opus-4-7
```

## How it works

1. **Collect changes** — Reads `git diff HEAD` to get all uncommitted changes
2. **Send to Haiku** — Passes the diff to Claude Haiku with categorization prompt
3. **Parse response** — Haiku returns a JSON mapping of file → category + commit message
4. **Group files** — Groups files by category (feat, fix, docs, etc.)
5. **Commit per category** — Stages files for each category and creates a conventional commit

## Gotchas

- **Large diffs** — If changes exceed ~10K lines, Haiku may struggle. Use `-DryRun` to verify categorization first.
- **Ambiguous changes** — A file that both adds a feature and fixes a bug will be assigned to one category. Review the dry-run output.
- **Staged + unstaged** — The script commits ALL changes (staged and unstaged). To commit only staged: use `git add` before running, or modify `git diff HEAD` to `git diff --cached` in the script.
- **API quota** — Each run makes one API call. If you run multiple times in seconds, you may hit Anthropic rate limits.

## Troubleshooting

**"ANTHROPIC_API_KEY not set"**
```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."
```

**"No changes detected"**
```powershell
git status
git add <files>
```

**"API call failed: 401"**
Check that `ANTHROPIC_API_KEY` is set and valid:
```powershell
echo $env:ANTHROPIC_API_KEY
```

**"Failed to parse Claude response"**
The model returned malformed JSON. Check the git diff for binary files or very large changes. Try again with `-Model claude-opus-4-7` for better handling of complex diffs.

**"Commit failed"**
```powershell
git status
git log --oneline -5
```
Verify no merge conflicts or permission issues exist.

## Typical workflow

```powershell
# Make changes
echo "new auth code" >> src\Auth.java
echo "fix typo" >> README.md

# Add all changes
git add .

# Preview categorization
powershell -ExecutionPolicy Bypass -File .\.claude\skills\commit-by-category\driver.ps1 -DryRun

# Commit with auto-generated messages
powershell -ExecutionPolicy Bypass -File .\.claude\skills\commit-by-category\driver.ps1

# Verify
git log --oneline -5
```

Output:
```
abc1234 docs: fix README typo
def5678 feat: add authentication module
```
