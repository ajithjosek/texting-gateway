package com.etg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EtgApplication {
  public static void main(String[] args) {
    SpringApplication.run(EtgApplication.class, args);
  }
}
