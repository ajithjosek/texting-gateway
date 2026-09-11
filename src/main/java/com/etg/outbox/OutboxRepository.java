package com.etg.outbox;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
  List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
  List<OutboxEvent> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
}
