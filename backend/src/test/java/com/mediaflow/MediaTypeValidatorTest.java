package com.mediaflow;

import static org.junit.jupiter.api.Assertions.*;

import com.mediaflow.common.BusinessException;
import com.mediaflow.storage.MediaTypeValidator;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class MediaTypeValidatorTest {
  byte[] ftyp(String brand) {
    return ByteBuffer.allocate(24)
        .putInt(24)
        .put("ftyp".getBytes(StandardCharsets.US_ASCII))
        .put(brand.getBytes(StandardCharsets.US_ASCII))
        .putInt(0)
        .put(brand.getBytes(StandardCharsets.US_ASCII))
        .put("isom".getBytes(StandardCharsets.US_ASCII))
        .array();
  }

  @Test
  void recognizesIsoMp4InsteadOfGenericQuicktime() throws Exception {
    var result =
        new MediaTypeValidator()
            .detect(new MockMultipartFile("file", "sample.mp4", "video/mp4", ftyp("isom")));
    assertEquals("VIDEO", result.mediaType());
    assertEquals("video/mp4", result.mime());
  }

  @Test
  void recognizesM4aAudio() throws Exception {
    var result =
        new MediaTypeValidator()
            .detect(new MockMultipartFile("file", "sample.m4a", "audio/mp4", ftyp("M4A ")));
    assertEquals("AUDIO", result.mediaType());
  }

  @Test
  void doesNotTreatQuicktimeAsMp4() {
    assertThrows(
        BusinessException.class,
        () ->
            new MediaTypeValidator()
                .detect(new MockMultipartFile("file", "renamed.mp4", "video/mp4", ftyp("qt  "))));
  }
}
