package com.etg.consent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentEventRepository extends JpaRepository<ConsentEvent, Long> {
  List<ConsentEvent> findByPhoneE164AndTopicOrderByCreatedAtAsc(String phoneE164, String topic);
}
