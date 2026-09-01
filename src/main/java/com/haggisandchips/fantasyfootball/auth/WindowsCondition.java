package com.haggisandchips.fantasyfootball.auth;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Locale;

// True only on Windows - guards DpapiTokenStore, which shells out to Windows-only PowerShell
// cmdlets (ConvertTo/From-SecureString) and would simply fail there (silently, since TokenStore's
// own callers already treat any failure as "not logged in") on every other OS. See
// MacCondition/KeychainTokenStore and OtherOsCondition/FileTokenStore for the equivalents used
// elsewhere.
public class WindowsCondition implements Condition {

  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {

    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
  }
}
