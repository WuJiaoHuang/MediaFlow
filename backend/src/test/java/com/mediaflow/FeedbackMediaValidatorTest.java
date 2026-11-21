package com.mediaflow;

import static org.junit.jupiter.api.Assertions.*;

import com.mediaflow.common.BusinessException;
import com.mediaflow.storage.FeedbackMediaValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FeedbackMediaValidatorTest {
  private final FeedbackMediaValidator validator =
      new FeedbackMediaValidator(1024 * 1024, 2 * 1024 * 1024);

  @Test
  void rejectsTruncatedEbmlAndWrongContainer() throws Exception {
    for (String hex : new String[] {"1a45dfa3", "1a45dfa3ff", "1a45dfa3874282847765626d"})
      assertThrows(
          BusinessException.class,
          () ->
              validator.detect(
                  new MockMultipartFile(
                      "file", "bad.webm", "audio/webm", java.util.HexFormat.of().parseHex(hex)),
                  "AUDIO"));
  }

  @Test
  void distinguishesActualRecordedTrackTypes() throws Exception {
    for (String kind : new String[] {"audio", "video"})
      try (var in = getClass().getResourceAsStream("/feedback-fixtures/" + kind + ".webm")) {
        var file =
            new MockMultipartFile(
                "file", "feedback.webm", kind + "/webm;codecs=opus", in.readAllBytes());
        assertEquals(kind + "/webm", validator.detect(file, kind.toUpperCase()));
      }
  }

  @Test
  void isoBmffRequiresMediaHandlerAndRejectsQuicktime() throws Exception {
    for (String brand : new String[] {"isom", "qt  "}) {
      byte[] bytes = new byte[24];
      java.nio.ByteBuffer.wrap(bytes)
          .putInt(24)
          .put("ftyp".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
          .put(brand.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      assertThrows(
          BusinessException.class,
          () ->
              validator.detect(
                  new MockMultipartFile("file", "x.mp4", "video/mp4", bytes), "VIDEO"));
    }
  }

  @Test
  void distinctLimitsAndEmptyFiles() {
    assertThrows(
        BusinessException.class,
        () ->
            validator.detect(
                new MockMultipartFile("file", "x.wav", "audio/wav", new byte[1048577]), "AUDIO"));
    assertThrows(
        BusinessException.class,
        () ->
            validator.detect(
                new MockMultipartFile("file", "x.webm", "video/webm", new byte[2097153]), "VIDEO"));
    assertThrows(
        BusinessException.class,
        () ->
            validator.detect(
                new MockMultipartFile("file", "x.wav", "audio/wav", new byte[0]), "AUDIO"));
  }

  @Test
  void incompleteTracksCannotHideVideoAfterAudioPrefix() {
    // Valid webm DocType, unknown-sized Segment; truncated declared Tracks must not classify.
    String prefix = "1a45dfa3874282847765626d18538067ff1654ae6b9fae83838102";
    assertThrows(
        BusinessException.class,
        () ->
            validator.detect(
                new MockMultipartFile(
                    "file", "hidden.webm", "audio/webm", java.util.HexFormat.of().parseHex(prefix)),
                "AUDIO"));
  }
}
