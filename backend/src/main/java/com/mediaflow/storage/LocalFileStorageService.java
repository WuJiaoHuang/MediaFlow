package com.mediaflow.storage;

import com.mediaflow.common.BusinessException;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LocalFileStorageService implements FileStorage {
  private final Path root;
  private final long maxSize;

  public LocalFileStorageService(
      @Value("${mediaflow.storage-root}") String directory,
      @Value("${mediaflow.max-file-bytes}") long maxSize)
      throws IOException {
    this.root =
        Files.createDirectories(Path.of(directory).toAbsolutePath().normalize()).toRealPath();
    this.maxSize = maxSize;
  }

  private Path path(String key) throws IOException {
    if (key == null || !key.matches("[a-f0-9-]{36}\\.bin"))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "非法存储标识");
    Path p = root.resolve(key).normalize();
    if (!p.getParent().equals(root) || Files.isSymbolicLink(p))
      throw new BusinessException(HttpStatus.BAD_REQUEST, "非法存储路径");
    return p;
  }

  @Override
  public StoredObject store(InputStream content, long size, String type) throws IOException {
    if (size <= 0 || size > maxSize)
      throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "文件为空或超过上传大小限制");
    String key = UUID.randomUUID() + ".bin";
    Path p = path(key);
    // Only clean up a file successfully created by this operation, never a UUID collision.
    OutputStream created =
        Files.newOutputStream(p, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    try (OutputStream out = created) {
      byte[] buffer = new byte[65536];
      long total = 0;
      int n;
      while ((n = content.read(buffer)) != -1) {
        total += n;
        if (total > maxSize)
          throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "文件超过上传大小限制");
        out.write(buffer, 0, n);
      }
      if (total != size) throw new BusinessException(HttpStatus.BAD_REQUEST, "文件大小不一致");
      return new StoredObject(key, total, type);
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(p);
      throw e;
    }
  }

  @Override
  public InputStream open(String key) throws IOException {
    return Files.newInputStream(path(key));
  }

  @Override
  public Resource resource(String key) throws IOException {
    Path p = path(key);
    if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
      throw new BusinessException(HttpStatus.NOT_FOUND, "媒体文件不存在");
    return new FileSystemResource(p);
  }

  @Override
  public void delete(String key) throws IOException {
    Files.deleteIfExists(path(key));
  }
}
