package com.etg.ui;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * UI contract mirror (runs in {@code mvn test}, no browser needed): asserts the static frontend
 * served by the nginx container contains the markers the Playwright specs check.
 * If this fails, {@code frontend/tests/inbox.spec.ts} will fail too — fix index.html once.
 */
class FrontendStaticTest {

  private String indexHtml() throws Exception {
    Path p = Path.of("frontend", "index.html");
    assertThat(p).exists();
    return Files.readString(p);
  }

  @Test
  void indexHasProductIdentity() throws Exception {
    String html = indexHtml();
    assertThat(html).contains("Enterprise Texting Gateway");
    assertThat(html).contains("<title>ETG Inbox");
  }

  @Test
  void indexDocumentsApiHintsCheckedByUiSpecs() throws Exception {
    String html = indexHtml();
    assertThat(html).contains("POST /v1/messages");
    assertThat(html).contains("GET /actuator/health");
  }
}
