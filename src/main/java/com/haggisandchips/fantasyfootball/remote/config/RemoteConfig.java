package com.haggisandchips.fantasyfootball.remote.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RemoteConfig {

  @Bean
  ObjectMapper objectMapper() {

    return new ObjectMapper();
  }
}
