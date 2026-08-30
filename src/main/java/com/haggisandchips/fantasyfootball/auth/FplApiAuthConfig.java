package com.haggisandchips.fantasyfootball.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Bound from FPL_API_AUTHORIZATION - the raw X-Api-Authorization request header value copied from
// a genuinely logged-in browser. This is the bearer-token credential behind Premier League's Ping
// Identity login (see FplSessionAuthClient) - no session cookie is needed alongside it.
@Component
@ConfigurationProperties(prefix = "fpl.api")
@Data
public class FplApiAuthConfig {

  private String authorization;
}
