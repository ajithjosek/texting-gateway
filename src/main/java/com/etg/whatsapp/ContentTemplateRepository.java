package com.etg.whatsapp;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentTemplateRepository extends JpaRepository<ContentTemplate, Long> {
  List<ContentTemplate> findByActiveTrueOrderByIdAsc();
  Optional<ContentTemplate> findFirstByContentSidAndActiveTrue(String contentSid);
}
