package com.etg.policycenter;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PolicyCenterConfig {

  @Bean
  @ConditionalOnProperty(name = "etg.policycenter.enabled", havingValue = "true")
  PolicyCenterClient restPolicyCenterClient(RestTemplateBuilder builder,
                                            @Value("${etg.policycenter.base-url:}") String baseUrl,
                                            @Value("${etg.policycenter.api-key:}") String apiKey) {
    return new RestPolicyCenterClient(builder.build(), baseUrl, apiKey);
  }

  @Bean
  @ConditionalOnMissingBean(PolicyCenterClient.class)
  PolicyCenterClient stubPolicyCenterClient(Clock clock,
                                            @Value("${etg.demo-data:false}") boolean demoData) {
    return new StubPolicyCenterClient(clock, demoData);
  }
}
