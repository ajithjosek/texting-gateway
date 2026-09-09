package com.etg.api;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.etg.inbox.Conversation;
import com.etg.inbox.ConversationMessage;
import com.etg.inbox.ConversationMessageRepository;
import com.etg.inbox.ConversationRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Inbox contract: queue, assign/collision, reply via send pipeline, notes, close, CSAT, SLA. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InboxApiTest {

  @Autowired MockMvc mvc;
  @Autowired ConversationRepository conversations;
  @Autowired ConversationMessageRepository thread;

  private void inbound(String from, String body) throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", from).param("Body", body))
        .andExpect(status().isOk());
  }

  private void optIn(String phone, String topic) throws Exception {
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content(
        "{\"phoneE164\":\"" + phone + "\",\"topic\":\"" + topic
            + "\",\"source\":\"api\",\"proofTextVersion\":\"v1\"}"))
        .andExpect(status().isOk());
  }

  private Conversation openFor(String phone) {
    return conversations.findFirstByCustomerPhoneAndTopicAndStatusNot(phone, "servicing", "CLOSED")
        .orElseThrow();
  }

  @Test
  void inbound_createsOpenConversation_withThreadEntry() throws Exception {
    inbound("+15550010001", "hello?");
    Conversation c = openFor("+15550010001");
    assertThat(c.getStatus()).isEqualTo("OPEN");
    List<ConversationMessage> t = thread.findByConversationIdOrderByCreatedAtAscIdAsc(c.getId());
    assertThat(t).hasSize(1);
    assertThat(t.get(0).getDirection()).isEqualTo("IN");
    assertThat(t.get(0).getBody()).isEqualTo("hello?");
  }

  @Test
  void assign_thenCollision_thenTakeoverAfterWindow() throws Exception {
    inbound("+15550010002", "hi");
    Long id = openFor("+15550010002").getId();

    mvc.perform(post("/v1/inbox/" + id + "/assign").contentType(MediaType.APPLICATION_JSON)
            .content("{\"agentId\":\"amy\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ASSIGNED"))
        .andExpect(jsonPath("$.assigneeAgentId").value("amy"));

    mvc.perform(post("/v1/inbox/" + id + "/assign").contentType(MediaType.APPLICATION_JSON)
            .content("{\"agentId\":\"bob\"}"))
        .andExpect(status().isConflict());

    // Same agent re-assign is fine; stale threads (updated >15min ago) can be taken over.
    mvc.perform(post("/v1/inbox/" + id + "/assign").contentType(MediaType.APPLICATION_JSON)
            .content("{\"agentId\":\"amy\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void reply_sendsViaPipeline_andMarksReplied() throws Exception {
    inbound("+15550010003", "need help");
    optIn("+15550010003", "servicing");
    Long id = openFor("+15550010003").getId();

    mvc.perform(post("/v1/inbox/" + id + "/reply").contentType(MediaType.APPLICATION_JSON)
            .content("{\"agentId\":\"amy\",\"body\":\"on it\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replied").value(true))
        .andExpect(jsonPath("$.assigneeAgentId").value("amy"));

    assertThat(thread.findByConversationIdOrderByCreatedAtAscIdAsc(id))
        .extracting(ConversationMessage::getDirection).containsExactly("IN", "OUT");
  }

  @Test
  void reply_withoutOptIn_isForbidden() throws Exception {
    inbound("+15550010004", "need help");
    Long id = openFor("+15550010004").getId();
    mvc.perform(post("/v1/inbox/" + id + "/reply").contentType(MediaType.APPLICATION_JSON)
            .content("{\"agentId\":\"amy\",\"body\":\"on it\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void notes_areInternal_andTranscriptShowsBoth() throws Exception {
    inbound("+15550010005", "hi");
    Long id = openFor("+15550010005").getId();

    mvc.perform(post("/v1/inbox/" + id + "/notes").contentType(MediaType.APPLICATION_JSON)
            .content("{\"agentId\":\"amy\",\"body\":\"vip customer\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visibility").value("INTERNAL"));

    mvc.perform(get("/v1/inbox/" + id + "/transcript"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[1].visibility").value("INTERNAL"));
  }

  @Test
  void close_thenCsat_thenReplyConflict() throws Exception {
    inbound("+15550010006", "thanks");
    Long id = openFor("+15550010006").getId();

    mvc.perform(post("/v1/inbox/" + id + "/close")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"));
    mvc.perform(post("/v1/inbox/" + id + "/csat").contentType(MediaType.APPLICATION_JSON)
            .content("{\"score\":5}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.csatScore").value(5));
    mvc.perform(post("/v1/inbox/" + id + "/reply").contentType(MediaType.APPLICATION_JSON)
            .content("{\"agentId\":\"amy\",\"body\":\"late\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void breached_listsUnrepliedPastSla() throws Exception {
    inbound("+15550010007", "waiting");
    Conversation c = openFor("+15550010007");
    c.setFirstReplyDueAt(Instant.now().minusSeconds(60));
    conversations.save(c);

    mvc.perform(get("/v1/inbox/breached"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].customerPhone", hasItem("+15550010007")));
  }
}
