package com.etg.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

/**
 * Read-only warehouse export: cursor-paginated outbox stream for batch loaders
 * (Snowflake COPY, BigQuery load). Relay owns published marking; this never mutates.
 */
@RestController
@RequestMapping("/v1/exports")
public class ExportController {
  private final OutboxRepository outbox;
  private final ObjectMapper json;

  public ExportController(OutboxRepository outbox, ObjectMapper json) {
    this.outbox = outbox;
    this.json = json;
  }

  @GetMapping("/events")
  public Map<String, Object> events(@RequestParam(value = "sinceId", defaultValue = "0") long sinceId,
                                    @RequestParam(value = "limit", defaultValue = "100") int limit) {
    List<OutboxEvent> rows = outbox.findByIdGreaterThanOrderByIdAsc(
        sinceId, PageRequest.of(0, Math.min(Math.max(limit, 1), 1000)));
    List<Map<String, Object>> mapped = rows.stream().map(e -> {
      Map<String, Object> m = new TreeMap<>();
      m.put("id", e.getId());
      m.put("aggregateType", e.getAggregateType());
      m.put("aggregateId", e.getAggregateId());
      m.put("eventType", e.getEventType());
      m.put("payload", parse(e.getPayload()));
      m.put("createdAt", e.getCreatedAt().toString());
      m.put("publishedAt", e.getPublishedAt() == null ? null : e.getPublishedAt().toString());
      return m;
    }).toList();
    long next = rows.isEmpty() ? sinceId : rows.get(rows.size() - 1).getId();
    return Map.of("events", mapped, "nextCursor", next);
  }

  private Object parse(String payload) {
    try {
      JsonNode n = json.readTree(payload);
      return json.convertValue(n, Object.class);
    } catch (Exception e) {
      return payload;
    }
  }
}
