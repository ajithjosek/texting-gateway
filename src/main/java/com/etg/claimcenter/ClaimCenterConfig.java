package com.etg.claimcenter;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClaimCenterConfig {

  @Bean
  @ConditionalOnProperty(name = "etg.guidewire.enabled", havingValue = "true")
  ClaimCenterClient restClaimCenterClient(RestTemplateBuilder builder,
                                          @Value("${etg.guidewire.base-url:}") String baseUrl,
                                          @Value("${etg.guidewire.api-key:}") String apiKey) {
    return new RestClaimCenterClient(builder.build(), baseUrl, apiKey);
  }

  @Bean
  @ConditionalOnMissingBean(ClaimCenterClient.class)
  ClaimCenterClient stubClaimCenterClient(Clock clock) {
    return new StubClaimCenterClient(clock);
  }
}
