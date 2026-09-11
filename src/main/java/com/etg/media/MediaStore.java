package com.etg.media;

/** Media storage port. File store is default; S3 activates with {@code etg.media.s3-enabled=true}. */
public interface MediaStore {
  /** Stores bytes, returns the reference (path or URI) recorded on the attachment. */
  String store(byte[] bytes, String contentType, String namespace);
}
