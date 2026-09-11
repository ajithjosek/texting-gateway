package com.etg.claims;

import com.etg.claimcenter.ClaimCenterClient;
import com.etg.inbox.ConversationRepository;
import com.etg.inbox.Conversation;
import com.etg.media.FileMediaStore;
import com.etg.media.InboundMedia;
import com.etg.media.MediaDownloader;
import com.etg.media.MediaStore;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MMS claim-photo intake: download → type/size validate → store → link to a claim
 * (from the message text, else the caller's open claims thread) → attach in ClaimCenter.
 * Unvalidated or failed items are skipped and counted in the reply; intake never throws.
 */
@Service
public class PhotoIntakeService {
  private static final Logger log = LoggerFactory.getLogger(PhotoIntakeService.class);
  private static final Pattern CLAIM_REF = Pattern.compile("CLM-\\d+", Pattern.CASE_INSENSITIVE);

  private final MediaDownloader downloader;
  private final MediaStore store;
  private final ClaimAttachmentRepository attachments;
  private final ConversationRepository conversations;
  private final ClaimCenterClient claims;

  public PhotoIntakeService(MediaDownloader downloader, MediaStore store,
                            ClaimAttachmentRepository attachments,
                            ConversationRepository conversations,
                            ClaimCenterClient claims) {
    this.downloader = downloader;
    this.store = store;
    this.attachments = attachments;
    this.conversations = conversations;
    this.claims = claims;
  }

  @Transactional
  public String handle(String from, String body, List<InboundMedia> media) {
    String claimNumber = claimRefFrom(body)
        .or(() -> openClaimsThread(from).map(Conversation::getRefId))
        .filter(ref -> ref != null && !ref.isBlank())
        .map(String::toUpperCase)
        .orElse(null);

    int stored = 0, skipped = 0;
    for (InboundMedia m : media) {
      try {
        if (!FileMediaStore.supported(m.contentType())) {
          skipped++;
          continue;
        }
        var dl = downloader.download(m);
        if (!FileMediaStore.supported(dl.contentType())) {
          skipped++;
          continue;
        }
        String ref = store.store(dl.bytes(), dl.contentType(),
            claimNumber == null ? "unlinked" : claimNumber);
        attachments.save(new ClaimAttachment(from, claimNumber, ref, dl.contentType(), dl.bytes().length));
        if (claimNumber != null) {
          claims.attachPhoto(claimNumber, ref, dl.contentType());
        }
        stored++;
      } catch (Exception e) {
        log.warn("Photo intake skipped for {}: {}", from, e.getMessage());
        skipped++;
      }
    }

    if (stored == 0) {
      return "We couldn't save those files (only JPG/PNG/GIF/PDF under 10 MB). An agent will help shortly.";
    }
    String noun = stored == 1 ? "Photo" : stored + " photos";
    if (claimNumber != null) {
      return noun + " received for " + claimNumber + ". Reply STATUS " + claimNumber + " any time for updates."
          + (skipped == 0 ? "" : " (" + skipped + " file(s) skipped.)");
    }
    return noun + " saved. Reply with your claim number (e.g. STATUS CLM-1001) to link it."
        + (skipped == 0 ? "" : " (" + skipped + " file(s) skipped.)");
  }

  private static Optional<String> claimRefFrom(String body) {
    if (body == null) return Optional.empty();
    Matcher m = CLAIM_REF.matcher(body);
    return m.find() ? Optional.of(m.group()) : Optional.empty();
  }

  private Optional<Conversation> openClaimsThread(String phone) {
    return conversations.findFirstByCustomerPhoneAndTopicAndStatusNot(
        phone, "claims", Conversation.CLOSED);
  }
}
