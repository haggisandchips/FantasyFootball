package com.haggisandchips.fantasyfootball.auth;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Locale;

// True only on macOS - guards KeychainTokenStore, which shells out to the macOS-only `security`
// CLI and would simply fail there on every other OS. See WindowsCondition/DpapiTokenStore and
// OtherOsCondition/FileTokenStore for the equivalents used elsewhere.
public class MacCondition implements Condition {

  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {

    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
  }
}
