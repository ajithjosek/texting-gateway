package com.etg.whatsapp;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/whatsapp")
public class WhatsappController {
  private final WhatsappTemplateService templates;
  private final ContentTemplateRepository repo;

  public WhatsappController(WhatsappTemplateService templates, ContentTemplateRepository repo) {
    this.templates = templates;
    this.repo = repo;
  }

  public record RegisterRequest(@NotBlank String key, String channel, @NotBlank String contentSid) {}

  @PostMapping("/templates")
  public ContentTemplate register(@RequestBody RegisterRequest r) {
    return templates.register(r.key(), r.channel(), r.contentSid());
  }

  @GetMapping("/templates")
  public List<ContentTemplate> list() {
    return repo.findByActiveTrueOrderByIdAsc();
  }

  @PostMapping("/sync")
  public Map<String, Integer> sync() {
    return Map.of("updated", templates.syncStatuses());
  }
}
