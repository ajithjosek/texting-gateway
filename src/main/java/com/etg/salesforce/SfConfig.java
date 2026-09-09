package com.etg.salesforce;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SfConfig {

  @Bean
  @ConditionalOnProperty(name = "etg.salesforce.enabled", havingValue = "true")
  SalesforceClient restSalesforceClient(RestTemplateBuilder builder,
                                        @Value("${etg.salesforce.login-url:https://login.salesforce.com}") String loginUrl,
                                        @Value("${etg.salesforce.client-id:}") String clientId,
                                        @Value("${etg.salesforce.client-secret:}") String clientSecret,
                                        @Value("${etg.salesforce.username:}") String username,
                                        @Value("${etg.salesforce.password:}") String password) {
    return new RestSalesforceClient(builder.build(), loginUrl, clientId, clientSecret, username, password);
  }

  @Bean
  @ConditionalOnMissingBean(SalesforceClient.class)
  SalesforceClient stubSalesforceClient() {
    return new StubSalesforceClient();
  }
}
