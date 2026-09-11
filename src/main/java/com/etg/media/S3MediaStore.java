package com.etg.media;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3/MinIO store. Active only when {@code etg.media.s3-enabled=true} (see MediaConfig). */
public class S3MediaStore implements MediaStore {
  private final S3Client s3;
  private final String bucket;

  public S3MediaStore(S3Client s3, String bucket) {
    this.s3 = s3;
    this.bucket = bucket;
  }

  @Override
  public String store(byte[] bytes, String contentType, String namespace) {
    String safeNs = namespace == null ? "unlinked" : namespace.replaceAll("[^A-Za-z0-9-]", "_");
    String key = safeNs + "/" + java.util.UUID.randomUUID()
        + extensionFor(contentType);
    s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
        .contentType(contentType).contentLength((long) bytes.length).build(),
        RequestBody.fromBytes(bytes));
    return "s3://" + bucket + "/" + key;
  }

  private static String extensionFor(String contentType) {
    return switch (contentType) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/gif" -> ".gif";
      case "application/pdf" -> ".pdf";
      default -> ".bin";
    };
  }
}
