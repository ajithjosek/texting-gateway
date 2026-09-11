package com.etg.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

  @Mock OutboxRepository outbox;
  @Mock RabbitTemplate rabbit;
  @Mock com.etg.outbox.FileWarehouseSink warehouse;

  private OutboxRelay relay() {
    return new OutboxRelay(outbox, rabbit, warehouse, "etg.events");
  }

  @Test
  void publishesPending_andMarksPublished() {
    OutboxEvent e = new OutboxEvent("message", "1", "message.created", "{}");
    when(outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(e));

    relay().publishPending();

    verify(rabbit).convertAndSend("etg.events", "message.created", "{}");
    assertThat(e.getPublishedAt()).isNotNull();
    verify(outbox).save(e);
  }

  @Test
  void brokerDown_leavesRowsUnpublished_forNextTick() {
    OutboxEvent e = new OutboxEvent("message", "2", "message.created", "{}");
    when(outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(e));
    doThrow(new AmqpConnectException(new RuntimeException("refused")))
        .when(rabbit).convertAndSend(anyString(), anyString(), anyString());

    relay().publishPending();

    assertThat(e.getPublishedAt()).isNull();
    verify(outbox, never()).save(any());
  }

  @Test
  void emptyOutbox_isNoop() {
    when(outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of());
    relay().publishPending();
    verifyNoInteractions(rabbit);
  }
}
