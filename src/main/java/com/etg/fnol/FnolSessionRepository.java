package com.etg.fnol;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FnolSessionRepository extends JpaRepository<FnolSession, Long> {
  Optional<FnolSession> findByPhoneE164(String phoneE164);
}
