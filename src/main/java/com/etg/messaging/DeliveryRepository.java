package com.etg.messaging;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
  List<Delivery> findByMessageSidOrderByCreatedAtAsc(String messageSid);
}
