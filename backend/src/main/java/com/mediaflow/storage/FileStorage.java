package com.mediaflow.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Future storage boundary; no upload API or file-library business in Stage 1. Implementations must
 * generate opaque keys, validate content/size and prevent traversal.
 */
public interface FileStorage {
  record StoredObject(String key, long size, String contentType) {}

  StoredObject store(InputStream content, long size, String contentType) throws IOException;

  InputStream open(String key) throws IOException;

  void delete(String key) throws IOException;
}
