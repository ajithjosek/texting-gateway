package com.etg.common.web;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> root() {
    return Map.of("service", "enterprise-texting-gateway", "status", "ok");
  }
}
