package com.etg.consent;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {
  Optional<Consent> findByPhoneE164AndTopic(String phoneE164, String topic);
}
