package com.etg.template;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/templates")
public class TemplateController {
  private final TemplateService service;
  private final TemplateRepository repo;

  public TemplateController(TemplateService service, TemplateRepository repo) {
    this.service = service; this.repo = repo;
  }

  public record CreateRequest(String key, String locale, String body) {}
  public record RenderRequest(String key, String locale, Map<String, Object> vars) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MessageTemplate create(@RequestBody CreateRequest r) {
    return service.createVersion(r.key(), r.locale() == null ? "en" : r.locale(), r.body());
  }

  @GetMapping
  public List<MessageTemplate> list(@RequestParam("key") String key) {
    return repo.findByTemplateKeyOrderByLocaleAscVersionDesc(key);
  }

  @PostMapping("/render")
  public Map<String, String> render(@RequestBody RenderRequest r) {
    return Map.of("body", service.render(r.key(), r.locale() == null ? "en" : r.locale(), r.vars()));
  }
}
