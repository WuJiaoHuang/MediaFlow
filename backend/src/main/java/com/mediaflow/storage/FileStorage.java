package com.mediaflow.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Unified internal storage boundary. Implementations must generate opaque keys, validate
 * content/size and prevent traversal.
 */
public interface FileStorage {
  record StoredObject(String key, long size, String contentType) {}

  StoredObject store(InputStream content, long size, String contentType) throws IOException;

  InputStream open(String key) throws IOException;

  org.springframework.core.io.Resource resource(String key) throws IOException;

  void delete(String key) throws IOException;
}
