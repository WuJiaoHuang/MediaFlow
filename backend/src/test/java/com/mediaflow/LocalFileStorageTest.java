package com.mediaflow;

import static org.junit.jupiter.api.Assertions.*;

import com.mediaflow.common.BusinessException;
import com.mediaflow.storage.LocalFileStorageService;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {
  @TempDir Path root;

  @Test
  void nameCollisionPreservesExistingFile() throws Exception {
    var storage = new LocalFileStorageService(root.toString(), 10);
    var fixed = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
    var existing = root.resolve(fixed + ".bin");
    Files.writeString(existing, "keep");
    try (var ids = org.mockito.Mockito.mockStatic(java.util.UUID.class)) {
      ids.when(java.util.UUID::randomUUID).thenReturn(fixed);
      assertThrows(
          FileAlreadyExistsException.class,
          () -> storage.store(new ByteArrayInputStream(new byte[] {1}), 1, "image/png"));
    }
    assertEquals("keep", Files.readString(existing));
  }

  @Test
  void opaqueNamesDoNotOverwrite() throws Exception {
    var storage = new LocalFileStorageService(root.toString(), 10);
    var first = storage.store(new ByteArrayInputStream(new byte[] {1, 2}), 2, "image/png");
    var second = storage.store(new ByteArrayInputStream(new byte[] {1, 2}), 2, "image/png");
    assertNotEquals(first.key(), second.key());
    assertArrayEquals(new byte[] {1, 2}, storage.open(first.key()).readAllBytes());
  }

  @Test
  void traversalKeysRejected() throws Exception {
    var storage = new LocalFileStorageService(root.toString(), 10);
    assertThrows(BusinessException.class, () -> storage.resource("../secret"));
    assertThrows(BusinessException.class, () -> storage.delete("C:/secret"));
  }

  @Test
  void interruptedWriteCleansPartialFile() throws Exception {
    var storage = new LocalFileStorageService(root.toString(), 10);
    InputStream broken =
        new InputStream() {
          public int read() throws IOException {
            throw new IOException("simulated");
          }
        };
    assertThrows(IOException.class, () -> storage.store(broken, 2, "image/png"));
    try (var paths = Files.list(root)) {
      assertEquals(0, paths.count());
    }
  }

  @Test
  void dishonestLengthAndOversizeCleanPartialFile() throws Exception {
    var storage = new LocalFileStorageService(root.toString(), 10);
    assertThrows(
        BusinessException.class,
        () -> storage.store(new ByteArrayInputStream(new byte[11]), 2, "image/png"));
    assertThrows(
        BusinessException.class,
        () -> storage.store(new ByteArrayInputStream(new byte[1]), 2, "image/png"));
    try (var paths = Files.list(root)) {
      assertEquals(0, paths.count());
    }
  }
}
