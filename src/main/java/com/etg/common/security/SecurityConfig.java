package com.etg.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Scaffold security: public API + Twilio webhooks (signature-validated in controller).
 * Phase-1 SSO story will switch this to Keycloak JWT validation per docs/02-implementation-plan.md S4-S5.
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers("/twilio/**", "/v1/**", "/actuator/**"))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable);
    return http.build();
  }
}
