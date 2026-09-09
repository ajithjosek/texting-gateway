package com.etg.template;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Template lifecycle + Liquid rendering. New versions deactivate the previous
 * active row for the same (key, locale); render falls back to {@code en} locale.
 */
@Service
public class TemplateService {
  static final String FALLBACK_LOCALE = "en";
  private final TemplateRepository repo;

  public TemplateService(TemplateRepository repo) { this.repo = repo; }

  @Transactional
  public MessageTemplate createVersion(String key, String locale, String body) {
    int next = repo.findByTemplateKeyAndLocaleOrderByVersionDesc(key, locale).stream()
        .mapToInt(MessageTemplate::getVersion).max().orElse(0) + 1;
    repo.findByTemplateKeyAndLocaleAndActiveTrue(key, locale)
        .ifPresent(t -> { t.setActive(false); repo.save(t); });
    return repo.save(new MessageTemplate(key, locale, next, body, true));
  }

  public String render(String key, String locale, Map<String, Object> vars) {
    MessageTemplate t = repo.findByTemplateKeyAndLocaleAndActiveTrue(key, locale)
        .or(() -> repo.findByTemplateKeyAndLocaleAndActiveTrue(key, FALLBACK_LOCALE))
        .orElseThrow(() -> new TemplateNotFoundException("TEMPLATE_NOT_FOUND: " + key + "/" + locale));
    return liqp.TemplateParser.DEFAULT.parse(t.getBody()).render(vars == null ? Map.of() : vars);
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  public static class TemplateNotFoundException extends RuntimeException {
    public TemplateNotFoundException(String message) { super(message); }
  }
}
