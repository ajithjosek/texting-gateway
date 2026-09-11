package com.etg.whatsapp;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsappTemplateServiceTest {

  @Mock ContentTemplateRepository repo;
  @Mock TwilioContentClient content;
  Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneId.of("UTC"));

  private WhatsappTemplateService service() {
    return new WhatsappTemplateService(repo, content, clock);
  }

  @Test
  void register_defaultsWhatsappChannel() {
    when(repo.save(any(ContentTemplate.class))).thenAnswer(i -> i.getArgument(0));
    ContentTemplate t = service().register("promo", null, "HX1");
    assertThat(t.getChannel()).isEqualTo("whatsapp");
    assertThat(t.getStatus()).isEqualTo("pending");
    assertThat(t.isActive()).isTrue();
  }

  @Test
  void syncStatuses_updatesChanged_only() {
    ContentTemplate pending = new ContentTemplate("a", "whatsapp", "HX1");
    ContentTemplate steady = new ContentTemplate("b", "whatsapp", "HX2");
    steady.setStatus("approved");
    when(repo.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(pending, steady));
    when(content.whatsappApproval("HX1")).thenReturn(Optional.of("approved"));
    when(content.whatsappApproval("HX2")).thenReturn(Optional.of("approved"));

    assertThat(service().syncStatuses()).isEqualTo(1);
    assertThat(pending.getStatus()).isEqualTo("approved");
    verify(repo).save(pending);
    verify(repo, never()).save(steady);
  }

  @Test
  void approvedFor_requiresApprovedAndActive() {
    ContentTemplate t = new ContentTemplate("a", "whatsapp", "HX1");
    t.setStatus("approved");
    when(repo.findFirstByContentSidAndActiveTrue("HX1")).thenReturn(Optional.of(t));
    assertThat(service().approvedFor("HX1")).contains(t);
    assertThat(service().approvedFor(null)).isEmpty();

    t.setStatus("pending");
    assertThat(service().approvedFor("HX1")).isEmpty();
  }
}
