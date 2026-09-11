package com.etg.api;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.etg.claimcenter.StubClaimCenterClient;
import com.etg.claims.ClaimAttachmentRepository;
import com.etg.media.MediaDownloader;
import com.etg.media.MediaDownloader.DownloadedMedia;
import com.etg.media.MediaStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** MMS intake contract with mocked download/store; real attachment rows + ClaimCenter attach. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClaimPhotoApiTest {

  @Autowired MockMvc mvc;
  @Autowired ClaimAttachmentRepository attachments;
  @Autowired StubClaimCenterClient claims;
  @MockBean MediaDownloader downloader;
  @MockBean MediaStore store;

  @Test
  void photoWithClaimRef_storedAndAttached() throws Exception {
    when(downloader.download(any())).thenReturn(new DownloadedMedia(new byte[]{1}, "image/jpeg"));
    when(store.store(any(), eq("image/jpeg"), eq("CLM-1001"))).thenReturn("/m/1.jpg");

    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550018101").param("Body", "damage CLM-1001")
            .param("NumMedia", "1")
            .param("MediaUrl0", "https://api.twilio.com/m/1")
            .param("MediaContentType0", "image/jpeg"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("CLM-1001")));

    assertThat(attachments.findByClaimNumberOrderByCreatedAtAsc("CLM-1001")).hasSize(1);
    assertThat(claims.attaches()).extracting("claimNumber").contains("CLM-1001");
  }

  @Test
  void photoWithoutRef_storedUnlinked() throws Exception {
    when(downloader.download(any())).thenReturn(new DownloadedMedia(new byte[]{1}, "image/png"));
    when(store.store(any(), eq("image/png"), eq("unlinked"))).thenReturn("/m/2.png");

    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550018102").param("Body", "see attached")
            .param("NumMedia", "1")
            .param("MediaUrl0", "https://api.twilio.com/m/2")
            .param("MediaContentType0", "image/png"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("claim number")));
    assertThat(attachments.findByPhoneE164OrderByCreatedAtAsc("+15550018102")).hasSize(1);
  }

  @Test
  void unsupportedType_rejectedReply() throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550018103").param("Body", "CLM-1001")
            .param("NumMedia", "1")
            .param("MediaUrl0", "https://api.twilio.com/m/3")
            .param("MediaContentType0", "video/mp4"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("couldn't save")));
    assertThat(attachments.findByPhoneE164OrderByCreatedAtAsc("+15550018103")).isEmpty();
  }
}
