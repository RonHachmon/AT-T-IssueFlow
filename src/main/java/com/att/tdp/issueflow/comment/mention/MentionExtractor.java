package com.att.tdp.issueflow.comment.mention;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Parses comment text and extracts the set of {@code @username} handles that appear in it.
 *
 * <p>The handle character set ({@code [A-Za-z0-9._-]}) matches the username validation in {@code
 * CreateUserRequest}. The pattern additionally requires the handle to start and end with an
 * alphanumeric character — this lets the regex stop before a sentence-terminating {@code .}, {@code
 * !}, etc., so {@code "Hey @jdoe."} matches {@code jdoe} rather than {@code jdoe.}. A negative
 * lookbehind keeps email-like fragments such as {@code alice@example.com} from producing false
 * mentions and rejects double-{@code @} prefixes such as {@code @@jdoe}.
 *
 * <p>Returned handles are lower-cased so callers can dedup case-insensitively. The original casing
 * of the user record is recovered downstream via {@code findByUsernameIgnoreCase}.
 */
@Component
public final class MentionExtractor {

  private static final Pattern PATTERN =
      Pattern.compile("(?<![A-Za-z0-9._@-])@([A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?)");

  /**
   * Extracts the unique {@code @username} handles from a comment body in the order they first
   * appear in the text. The returned list is lower-cased and deduped.
   *
   * @param content the comment body; may be {@code null} or blank
   * @return the unique handles, lower-cased, in order of first appearance; empty when the content
   *     has no mentions or is blank
   */
  public List<String> extract(String content) {
    if (content == null || content.isBlank()) {
      return List.of();
    }
    LinkedHashSet<String> handles = new LinkedHashSet<>();
    Matcher matcher = PATTERN.matcher(content);
    while (matcher.find()) {
      handles.add(matcher.group(1).toLowerCase(Locale.ROOT));
    }
    return List.copyOf(handles);
  }
}
