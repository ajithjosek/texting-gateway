package com.etg.template;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

  @Mock TemplateRepository repo;
  @InjectMocks TemplateService service;

  @Test
  void render_substitutesVars() {
    when(repo.findByTemplateKeyAndLocaleAndActiveTrue("payment_due", "en")).thenReturn(
        Optional.of(new MessageTemplate("payment_due", "en", 1, "Hi {{first_name}}, due {{due_date}}.", true)));
    assertThat(service.render("payment_due", "en", Map.of("first_name", "Ana", "due_date", "Dec 1")))
        .isEqualTo("Hi Ana, due Dec 1.");
  }

  @Test
  void render_fallsBackToEnglishLocale() {
    when(repo.findByTemplateKeyAndLocaleAndActiveTrue("payment_due", "es")).thenReturn(Optional.empty());
    when(repo.findByTemplateKeyAndLocaleAndActiveTrue("payment_due", "en")).thenReturn(
        Optional.of(new MessageTemplate("payment_due", "en", 1, "Hi {{first_name}}.", true)));
    assertThat(service.render("payment_due", "es", Map.of("first_name", "Ana")))
        .isEqualTo("Hi Ana.");
  }

  @Test
  void render_missingTemplate_throws404() {
    when(repo.findByTemplateKeyAndLocaleAndActiveTrue(anyString(), anyString()))
        .thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.render("nope", "en", Map.of()))
        .isInstanceOf(TemplateService.TemplateNotFoundException.class);
  }

  @Test
  void createVersion_deactivatesPrevious_andIncrements() {
    MessageTemplate v1 = new MessageTemplate("k", "en", 1, "old", true);
    when(repo.findByTemplateKeyAndLocaleOrderByVersionDesc("k", "en")).thenReturn(List.of(v1));
    when(repo.findByTemplateKeyAndLocaleAndActiveTrue("k", "en")).thenReturn(Optional.of(v1));
    when(repo.save(any(MessageTemplate.class))).thenAnswer(i -> i.getArgument(0));

    MessageTemplate v2 = service.createVersion("k", "en", "new");
    assertThat(v2.getVersion()).isEqualTo(2);
    assertThat(v2.isActive()).isTrue();
    assertThat(v1.isActive()).isFalse();
  }
}
