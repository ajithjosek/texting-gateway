package com.etg.campaign;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, Long> {
  List<CampaignRecipient> findByCampaignIdOrderByIdAsc(Long campaignId);
}
