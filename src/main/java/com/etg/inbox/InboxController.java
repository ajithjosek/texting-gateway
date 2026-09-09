package com.etg.inbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/inbox")
public class InboxController {
  private final InboxService inbox;

  public InboxController(InboxService inbox) { this.inbox = inbox; }

  public record AssignRequest(@NotBlank String agentId) {}
  public record ReplyRequest(@NotBlank String agentId, @NotBlank String body) {}
  public record NoteRequest(@NotBlank String agentId, @NotBlank String body) {}
  public record CsatRequest(@Min(1) @Max(5) int score) {}

  @GetMapping
  public List<Conversation> queue(@RequestParam(value = "status", required = false) String status,
                                  @RequestParam(value = "assignee", required = false) String assignee) {
    return inbox.queue(status, assignee);
  }

  @GetMapping("/breached")
  public List<Conversation> breached() {
    return inbox.breached();
  }

  @GetMapping("/{id}/transcript")
  public List<ConversationMessage> transcript(@PathVariable Long id) {
    return inbox.transcript(id);
  }

  @PostMapping("/{id}/assign")
  public Conversation assign(@PathVariable Long id, @Valid @RequestBody AssignRequest r) {
    return inbox.assign(id, r.agentId());
  }

  @PostMapping("/{id}/reply")
  public Conversation reply(@PathVariable Long id, @Valid @RequestBody ReplyRequest r) {
    return inbox.reply(id, r.agentId(), r.body());
  }

  @PostMapping("/{id}/notes")
  public ConversationMessage note(@PathVariable Long id, @Valid @RequestBody NoteRequest r) {
    return inbox.note(id, r.agentId(), r.body());
  }

  @PostMapping("/{id}/close")
  public Conversation close(@PathVariable Long id) {
    return inbox.close(id);
  }

  @PostMapping("/{id}/csat")
  public Conversation csat(@PathVariable Long id, @Valid @RequestBody CsatRequest r) {
    return inbox.csat(id, r.score());
  }

  // Twilio Conversations sync lands here in S3 (two-way thread mirror); v1 threads live in ETG.
  @GetMapping("/sync-status")
  public Map<String, String> syncStatus() {
    return Map.of("twilioConversations", "not-connected", "sourceOfTruth", "etg");
  }
}
