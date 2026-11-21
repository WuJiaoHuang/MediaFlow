package com.mediaflow.storage;

import com.mediaflow.common.BusinessException;
import java.io.*;
import java.util.*;
import org.apache.tika.Tika;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MediaTypeValidator {
  private final Tika tika = new Tika();

  public record Detected(String mime, String mediaType) {}

  public Detected detect(MultipartFile file) throws IOException {
    String name = validFilename(file);
    String actual;
    try (var in = new BufferedInputStream(file.getInputStream())) {
      actual = canonical(tika.detect(in));
    }
    if (actual.equals("application/x-matroska") || actual.equals("video/x-matroska")) {
      try (var in = file.getInputStream()) {
        if (webmDocType(in.readNBytes(4096))) actual = "video/webm";
      }
    }
    if (actual.equals("video/quicktime")) {
      try (var in = file.getInputStream()) {
        byte[] header = in.readNBytes(16);
        if (header.length == 16
            && new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp")
            && Set.of(
                    "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "avc1", "mp41", "mp42", "M4V ",
                    "dash")
                .contains(new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)))
          actual = "video/mp4";
      }
    }
    String declared =
        canonical(
            file.getContentType() == null
                ? ""
                : file.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT));
    Set<String> supported =
        Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "audio/mpeg",
            "audio/wav",
            "audio/mp4",
            "video/mp4",
            "video/webm");
    if (!supported.contains(actual) || !actual.equals(declared))
      throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "文件内容与 MIME 不一致或格式不受支持");
    String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    Map<String, Set<String>> extensions =
        Map.of(
            "image/jpeg",
            Set.of("jpg", "jpeg"),
            "image/png",
            Set.of("png"),
            "image/webp",
            Set.of("webp"),
            "audio/mpeg",
            Set.of("mp3"),
            "audio/wav",
            Set.of("wav"),
            "audio/mp4",
            Set.of("m4a"),
            "video/mp4",
            Set.of("mp4"),
            "video/webm",
            Set.of("webm"));
    if (!extensions.get(actual).contains(ext))
      throw new BusinessException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "文件扩展名与内容不一致");
    return new Detected(
        actual,
        actual.startsWith("image/") ? "IMAGE" : actual.startsWith("audio/") ? "AUDIO" : "VIDEO");
  }

  public static String validFilename(MultipartFile file) {
    String name = file.getOriginalFilename();
    if (name == null
        || name.isBlank()
        || name.length() > 255
        || name.contains("/")
        || name.contains("\\")
        || name.contains("..")
        || name.codePoints().anyMatch(c -> c < 32 || c == 127))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "文件名不合法");
    return name;
  }

  private boolean webmDocType(byte[] data) {
    if (data.length < 5) return false;
    int sizeLength = vintLength(data[4]);
    if (sizeLength == 0 || data.length < 4 + sizeLength) return false;
    long size = vintValue(data, 4, sizeLength);
    int cursor = 4 + sizeLength;
    long end = Math.min(data.length, cursor + size);
    while (cursor < end) {
      int idLength = vintLength(data[cursor]);
      if (idLength == 0 || idLength > 4 || cursor + idLength >= end) return false;
      long id = 0;
      for (int i = 0; i < idLength; i++) id = (id << 8) | (data[cursor + i] & 255);
      cursor += idLength;
      int length = vintLength(data[cursor]);
      if (length == 0 || cursor + length > end) return false;
      long bytes = vintValue(data, cursor, length);
      cursor += length;
      if (bytes < 0 || bytes > end - cursor) return false;
      if (id == 0x4282)
        return bytes == 4
            && new String(data, cursor, 4, java.nio.charset.StandardCharsets.US_ASCII)
                .equals("webm");
      cursor += (int) bytes;
    }
    return false;
  }

  private int vintLength(byte value) {
    for (int i = 1; i <= 8; i++) if (((value & 255) & (1 << (8 - i))) != 0) return i;
    return 0;
  }

  private long vintValue(byte[] data, int offset, int length) {
    long result = data[offset] & ((1 << (8 - length)) - 1);
    for (int i = 1; i < length; i++) result = (result << 8) | (data[offset + i] & 255);
    return result;
  }

  private String canonical(String mime) {
    return switch (mime) {
      case "audio/x-wav", "audio/vnd.wave", "audio/wave" -> "audio/wav";
      case "audio/x-m4a" -> "audio/mp4";
      case "audio/mp3" -> "audio/mpeg";
      default -> mime;
    };
  }
}
