package com.etg.template;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<MessageTemplate, Long> {
  Optional<MessageTemplate> findByTemplateKeyAndLocaleAndActiveTrue(String key, String locale);
  List<MessageTemplate> findByTemplateKeyAndLocaleOrderByVersionDesc(String key, String locale);
  List<MessageTemplate> findByTemplateKeyOrderByLocaleAscVersionDesc(String key);
}
