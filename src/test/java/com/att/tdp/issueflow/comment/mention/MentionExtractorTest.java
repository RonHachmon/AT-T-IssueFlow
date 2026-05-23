package com.att.tdp.issueflow.comment.mention;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MentionExtractorTest {

  private final MentionExtractor extractor = new MentionExtractor();

  @Test
  void extractsSingleMention() {
    assertThat(extractor.extract("@jdoe hi")).containsExactly("jdoe");
  }

  @Test
  void extractsMultipleMentionsInOrder() {
    assertThat(extractor.extract("@bob then @alice")).containsExactly("bob", "alice");
  }

  @Test
  void dedupesRepeatedMentionsKeepingFirstOccurrence() {
    assertThat(extractor.extract("@jdoe and @jdoe")).containsExactly("jdoe");
  }

  @Test
  void ignoresEmailLikeAtSign() {
    assertThat(extractor.extract("reach alice@example.com")).isEmpty();
  }

  @Test
  void ignoresDoubleAtPrefix() {
    assertThat(extractor.extract("@@jdoe")).isEmpty();
  }

  @Test
  void acceptsPunctuationAroundMention() {
    assertThat(extractor.extract("(@jdoe), @jdoe!, @jdoe.")).containsExactly("jdoe");
  }

  @Test
  void lowercasesHandlesInOutput() {
    assertThat(extractor.extract("@JDoe")).containsExactly("jdoe");
  }

  @Test
  void returnsEmptyListForNullContent() {
    assertThat(extractor.extract(null)).isEmpty();
  }

  @Test
  void returnsEmptyListForBlankContent() {
    assertThat(extractor.extract("   ")).isEmpty();
  }

  @Test
  void returnsEmptyListForEmptyContent() {
    assertThat(extractor.extract("")).isEmpty();
  }

  @Test
  void mentionCharsRespectUsernamePattern() {
    assertThat(extractor.extract("@a.b_c-d test")).containsExactly("a.b_c-d");
  }

  @Test
  void extractsMentionAtStartOfText() {
    assertThat(extractor.extract("@first then nothing")).containsExactly("first");
  }

  @Test
  void extractsMixedKnownAndUnknownStillReturnsAll() {
    assertThat(extractor.extract("@bob and @alice and @charlie"))
        .containsExactly("bob", "alice", "charlie");
  }
}
