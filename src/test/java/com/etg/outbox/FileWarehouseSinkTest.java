package com.etg.outbox;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileWarehouseSinkTest {

  ObjectMapper json = new ObjectMapper();

  @Test
  void append_writesJsonLines(@TempDir Path dir) throws Exception {
    FileWarehouseSink sink = new FileWarehouseSink(json, dir.toString(), true);
    OutboxEvent e = new OutboxEvent("message", "1", "message.created", "{\"to\":\"+1\"}");
    sink.append(e);

    List<Path> files;
    try (var s = Files.list(dir)) {
      files = s.toList();
    }
    assertThat(files).hasSize(1);
    assertThat(files.get(0).getFileName().toString()).startsWith("events-").endsWith(".ndjson");
    String line = Files.readString(files.get(0)).trim();
    assertThat(json.readTree(line).path("eventType").asText()).isEqualTo("message.created");
    assertThat(json.readTree(line).path("payload").path("to").asText()).isEqualTo("+1");
  }

  @Test
  void disabled_writesNothing(@TempDir Path dir) throws Exception {
    FileWarehouseSink sink = new FileWarehouseSink(json, dir.toString(), false);
    sink.append(new OutboxEvent("message", "1", "message.created", "{}"));
    try (var s = Files.list(dir)) {
      assertThat(s.toList()).isEmpty();
    }
  }

  @Test
  void unparseablePayload_neverThrows(@TempDir Path dir) {
    // Payload is always valid JSON from our writers, but the sink must not blow up the relay.
    FileWarehouseSink sink = new FileWarehouseSink(json, dir.resolve("nope").toString(), true);
    assertThatCode(() -> sink.append(new OutboxEvent("x", "1", "t", "{")))
        .doesNotThrowAnyException();
  }
}
