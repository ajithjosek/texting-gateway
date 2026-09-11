package com.etg.claims;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etg.claimcenter.ClaimCenterClient;
import com.etg.inbox.Conversation;
import com.etg.inbox.ConversationRepository;
import com.etg.media.InboundMedia;
import com.etg.media.MediaDownloader;
import com.etg.media.MediaDownloader.DownloadedMedia;
import com.etg.media.MediaStore;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoIntakeServiceTest {

  @Mock MediaDownloader downloader;
  @Mock MediaStore store;
  @Mock ClaimAttachmentRepository attachments;
  @Mock ConversationRepository conversations;
  @Mock ClaimCenterClient claims;
  @InjectMocks PhotoIntakeService photos;

  private InboundMedia media(String type) {
    return new InboundMedia("https://api.twilio.com/m/1", type);
  }

  @Test
  void linkedViaBody_attachesToClaim() {
    when(downloader.download(any())).thenReturn(new DownloadedMedia(new byte[]{1}, "image/jpeg"));
    when(store.store(any(), eq("image/jpeg"), eq("CLM-1001"))).thenReturn("/m/1.jpg");
    when(claims.attachPhoto("CLM-1001", "/m/1.jpg", "image/jpeg")).thenReturn(true);

    String reply = photos.handle("+15550018001", "damage CLM-1001", List.of(media("image/jpeg")));

    assertThat(reply).contains("CLM-1001").contains("Photo received");
    verify(attachments).save(argThat(a ->
        "CLM-1001".equals(a.getClaimNumber()) && "/m/1.jpg".equals(a.getMediaRef())));
    verify(claims).attachPhoto("CLM-1001", "/m/1.jpg", "image/jpeg");
  }

  @Test
  void linkedViaOpenClaimsThread() {
    Conversation thread = new Conversation("+15550018002", "claims", java.time.Instant.now());
    when(conversations.findFirstByCustomerPhoneAndTopicAndStatusNot(
        "+15550018002", "claims", "CLOSED")).thenReturn(Optional.of(thread));
    when(downloader.download(any())).thenReturn(new DownloadedMedia(new byte[]{1}, "image/png"));
    when(store.store(any(), eq("image/png"), anyString())).thenReturn("/m/2.png");

    // Thread carries no refId yet: falls back to unlinked, still stored.
    String reply = photos.handle("+15550018002", "see attached", List.of(media("image/png")));
    assertThat(reply).contains("saved").contains("claim number");
    verify(attachments).save(argThat(a -> a.getClaimNumber() == null));
    verifyNoInteractions(claims);
  }

  @Test
  void unsupportedType_skipped_allSkippedReply() {
    String reply = photos.handle("+15550018003", "CLM-1001", List.of(media("video/mp4")));
    assertThat(reply).contains("couldn't save");
    verifyNoInteractions(downloader, store, claims);
    verifyNoInteractions(attachments);
  }

  @Test
  void downloadFailure_skipped_countsInReply() {
    when(downloader.download(any())).thenThrow(new RuntimeException("timeout"));
    String reply = photos.handle("+15550018004", "CLM-1001",
        List.of(media("image/jpeg"), media("image/jpeg")));
    assertThat(reply).contains("couldn't save");
    verifyNoInteractions(store);
  }
}
