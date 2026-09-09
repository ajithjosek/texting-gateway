package com.etg.messaging;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
  Optional<Message> findByIdempotencyKey(String idempotencyKey);
  Optional<Message> findByTwilioSid(String twilioSid);
}
