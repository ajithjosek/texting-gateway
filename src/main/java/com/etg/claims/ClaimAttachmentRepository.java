package com.etg.claims;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimAttachmentRepository extends JpaRepository<ClaimAttachment, Long> {
  List<ClaimAttachment> findByPhoneE164OrderByCreatedAtAsc(String phoneE164);
  List<ClaimAttachment> findByClaimNumberOrderByCreatedAtAsc(String claimNumber);
}
