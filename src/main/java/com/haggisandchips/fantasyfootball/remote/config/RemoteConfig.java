package com.haggisandchips.fantasyfootball.remote.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;

public class RemoteConfig {

  @Bean
  private ObjectMapper objectMapper() {

    return new ObjectMapper();
  }
}
