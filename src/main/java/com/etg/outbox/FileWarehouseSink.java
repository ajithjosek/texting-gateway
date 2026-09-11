package com.etg.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * NDJSON file sink: mirrors the published event stream to dated files
 * ({@code events-YYYY-MM-DD.ndjson}) for warehouse batch loaders.
 * Best-effort — I/O failures are logged, never thrown.
 */
@Component
public class FileWarehouseSink {
  private static final Logger log = LoggerFactory.getLogger(FileWarehouseSink.class);

  private final ObjectMapper json;
  private final Path dir;
  private final boolean enabled;

  public FileWarehouseSink(ObjectMapper json,
                           @Value("${etg.warehouse.dir:./warehouse}") String dir,
                           @Value("${etg.warehouse.file-enabled:true}") boolean enabled) {
    this.json = json;
    this.dir = Path.of(dir);
    this.enabled = enabled;
  }

  public synchronized void append(OutboxEvent e) {
    if (!enabled) return;
    try {
      Files.createDirectories(dir);
      Map<String, Object> m = new TreeMap<>();
      m.put("id", e.getId());
      m.put("aggregateType", e.getAggregateType());
      m.put("aggregateId", e.getAggregateId());
      m.put("eventType", e.getEventType());
      m.put("payload", json.readTree(e.getPayload()));
      m.put("createdAt", e.getCreatedAt().toString());
      m.put("publishedAt", e.getPublishedAt() == null ? null : e.getPublishedAt().toString());
      Path file = dir.resolve("events-" + LocalDate.now() + ".ndjson");
      Files.writeString(file, json.writeValueAsString(m) + "\n",
          StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (Exception ex) {
      log.warn("Warehouse file append failed: {}", ex.getMessage());
    }
  }
}
