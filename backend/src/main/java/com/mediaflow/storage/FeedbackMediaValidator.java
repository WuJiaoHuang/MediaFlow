package com.mediaflow.storage;

import com.mediaflow.common.BusinessException;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Bounded container inspection: WebM track types and ISO-BMFF media handlers. No transcoding. */
@Component
public class FeedbackMediaValidator {
  private final long audioLimit, videoLimit;
  private final Tika tika = new Tika();

  public FeedbackMediaValidator(
      @Value("${mediaflow.feedback-audio-max-bytes:10485760}") long audioLimit,
      @Value("${mediaflow.feedback-video-max-bytes:26214400}") long videoLimit) {
    this.audioLimit = audioLimit;
    this.videoLimit = videoLimit;
  }

  private BusinessException unsupported() {
    return new BusinessException(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "反馈文件内容、类型、MIME 或扩展名不匹配（音频 MP3/WAV/M4A/WebM；视频 MP4/WebM）");
  }

  public String detect(MultipartFile file, String kind) throws IOException {
    String name = MediaTypeValidator.validFilename(file);
    if (file.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "附件不能为空");
    long limit = kind.equals("AUDIO") ? audioLimit : videoLimit;
    if (file.getSize() > limit)
      throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "反馈附件超出大小上限：" + limit + " 字节");
    String actual;
    try (var in = new BufferedInputStream(file.getInputStream())) {
      actual = canonical(tika.detect(in));
    }
    byte[] prefix;
    try (var in = file.getInputStream()) {
      prefix = in.readNBytes(262144);
    }
    if (prefix.length >= 5
        && (prefix[0] & 255) == 0x1a
        && (prefix[1] & 255) == 0x45
        && (prefix[2] & 255) == 0xdf
        && (prefix[3] & 255) == 0xa3) {
      int tracks = webm(prefix);
      actual = tracks == 1 ? "audio/webm" : tracks == 3 ? "video/webm" : "invalid";
    } else if (prefix.length >= 12 && ascii(prefix, 4, 4).equals("ftyp")) {
      // Total bytes are capped before this bounded allocation. Large imported files fail above.
      byte[] bytes;
      try (var in = file.getInputStream()) {
        bytes = in.readNBytes((int) Math.min(limit + 1, Integer.MAX_VALUE));
      }
      String brand = ascii(bytes, 8, 4);
      if (!Set.of(
              "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "avc1", "mp41", "mp42", "M4V ",
              "M4A ", "dash")
          .contains(brand)) throw unsupported();
      int tracks = mp4(bytes, 0, bytes.length, 0);
      actual = (tracks & 2) != 0 ? "video/mp4" : (tracks & 1) != 0 ? "audio/mp4" : "invalid";
    }
    String declared =
        canonical(
            file.getContentType() == null
                ? ""
                : file.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT));
    var ext =
        Map.of(
            "audio/mpeg",
            "mp3",
            "audio/wav",
            "wav",
            "audio/mp4",
            "m4a",
            "audio/webm",
            "webm",
            "video/mp4",
            "mp4",
            "video/webm",
            "webm");
    String suffix = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    if (!ext.containsKey(actual)
        || !ext.get(actual).equals(suffix)
        || !actual.equals(declared)
        || !actual.startsWith(kind.toLowerCase(Locale.ROOT) + "/")) throw unsupported();
    return actual;
  }

  private String canonical(String s) {
    return switch (s) {
      case "audio/x-wav", "audio/vnd.wave", "audio/wave" -> "audio/wav";
      case "audio/x-m4a" -> "audio/mp4";
      case "audio/mp3" -> "audio/mpeg";
      default -> s;
    };
  }

  private static String ascii(byte[] b, int p, int n) {
    return new String(b, p, n, StandardCharsets.US_ASCII);
  }

  private static int vint(byte b) {
    for (int i = 1; i <= 8; i++) if (((b & 255) & (1 << (8 - i))) != 0) return i;
    return 0;
  }

  private static long number(byte[] b, int p, int n, boolean size) {
    long v = size ? b[p] & ((1 << (8 - n)) - 1) : b[p] & 255;
    for (int i = 1; i < n; i++) v = (v << 8) | (b[p + i] & 255);
    return v;
  }

  private int webm(byte[] b) {
    // The EBML header must explicitly declare webm; Matroska and arbitrary blobs are rejected.
    int headerLen = vint(b[4]);
    if (headerLen == 0 || 4 + headerLen > b.length) return 0;
    long headerSize = number(b, 4, headerLen, true);
    if (headerSize > b.length - 4 - headerLen) return 0;
    int headerEnd = 4 + headerLen + (int) headerSize;
    if (!docType(b, 4 + headerLen, headerEnd)) return 0;
    return elements(b, headerEnd, b.length, 0);
  }

  private boolean docType(byte[] b, int p, int end) {
    while (p < end) {
      int n = vint(b[p]);
      if (n == 0 || n > 4 || p + n >= end) return false;
      long id = number(b, p, n, false);
      p += n;
      int z = vint(b[p]);
      if (z == 0 || p + z > end) return false;
      long size = number(b, p, z, true);
      p += z;
      if (size > end - p) return false;
      if (id == 0x4282) return size == 4 && ascii(b, p, 4).equals("webm");
      p += (int) size;
    }
    return false;
  }

  private int elements(byte[] b, int p, int end, int depth) {
    if (depth > 4) return 0;
    int result = 0;
    while (p < end) {
      int n = vint(b[p]);
      if (n == 0 || n > 4 || p + n >= end) break;
      long id = number(b, p, n, false);
      p += n;
      int z = vint(b[p]);
      if (z == 0 || p + z > end) break;
      long len = number(b, p, z, true);
      p += z;
      // Never classify an incomplete Tracks/TrackEntry prefix as audio-only.
      if ((id == 0x1654ae6bL || id == 0xae) && len > end - p) return 0;
      int stop = (int) Math.min(end, (long) p + len);
      if (id == 0x18538067L || id == 0x1654ae6bL || id == 0xae)
        result |= elements(b, p, stop, depth + 1);
      else if (id == 0x83 && depth == 3 && len == 1 && p < end) {
        if (b[p] == 1) result |= 3;
        else if (b[p] == 2) result |= 1;
      }
      if (len > end - p) break;
      p = stop;
    }
    return result;
  }

  private int mp4(byte[] b, int p, int end, int depth) {
    if (depth > 4) return 0;
    int result = 0;
    while (p + 8 <= end) {
      long size = Integer.toUnsignedLong(ByteBuffer.wrap(b, p, 4).getInt());
      String type = ascii(b, p + 4, 4);
      int header = 8;
      if (size == 1) {
        if (p + 16 > end) return result;
        size = ByteBuffer.wrap(b, p + 8, 8).getLong();
        header = 16;
      }
      if (size == 0) size = end - p;
      if (size < header || size > end - p) return result;
      int stop = p + (int) size;
      if (Set.of("moov", "trak", "mdia").contains(type))
        result |= mp4(b, p + header, stop, depth + 1);
      if (type.equals("hdlr") && size >= header + 12) {
        String handler = ascii(b, p + header + 8, 4);
        if (handler.equals("soun")) result |= 1;
        if (handler.equals("vide")) result |= 2;
      }
      p = stop;
    }
    return result;
  }
}
