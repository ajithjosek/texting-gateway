package com.etg.media;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

/** Filesystem store (local VM). S3/MinIO replaces it in cloud profiles. */
public class FileMediaStore implements MediaStore {
  private static final Map<String, String> EXTENSIONS = Map.of(
      "image/jpeg", "jpg", "image/png", "png", "image/gif", "gif",
      "application/pdf", "pdf");

  private final Path root;

  public FileMediaStore(@Value("${etg.media.dir:./media}") String root) {
    this.root = Path.of(root);
  }

  @Override
  public String store(byte[] bytes, String contentType, String namespace) {
    try {
      String ext = EXTENSIONS.getOrDefault(contentType, "bin");
      String safeNs = namespace == null ? "unlinked" : namespace.replaceAll("[^A-Za-z0-9-]", "_");
      Path dir = root.resolve(safeNs);
      Files.createDirectories(dir);
      String name = UUID.randomUUID() + "." + ext;
      Files.write(dir.resolve(name), bytes);
      return dir.resolve(name).toString();
    } catch (Exception e) {
      throw new IllegalStateException("Media store failed", e);
    }
  }

  public static boolean supported(String contentType) {
    return EXTENSIONS.containsKey(contentType);
  }
}
