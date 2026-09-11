package com.etg.media;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileMediaStoreTest {

  @Test
  void storesUnderNamespace_withExtension(@TempDir Path dir) throws Exception {
    FileMediaStore store = new FileMediaStore(dir.toString());
    String ref = store.store(new byte[]{1}, "image/jpeg", "CLM-1001");
    Path file = Path.of(ref);
    assertThat(file.getFileName().toString()).endsWith(".jpg");
    assertThat(file.getParent().getFileName().toString()).isEqualTo("CLM-1001");
    assertThat(Files.readAllBytes(file)).containsExactly((byte) 1);
  }

  @Test
  void unknownType_usesBin(@TempDir Path dir) {
    FileMediaStore store = new FileMediaStore(dir.toString());
    assertThat(store.store(new byte[]{1}, "x/y", null)).endsWith(".bin");
    assertThat(FileMediaStore.supported("image/png")).isTrue();
    assertThat(FileMediaStore.supported("video/mp4")).isFalse();
  }
}
