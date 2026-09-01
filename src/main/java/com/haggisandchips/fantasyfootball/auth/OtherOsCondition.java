package com.haggisandchips.fantasyfootball.auth;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

// Guards FileTokenStore - true on Linux and anything else not specifically handled by
// WindowsCondition or MacCondition, so exactly one TokenStore bean is ever registered regardless
// of OS.
public class OtherOsCondition implements Condition {

  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {

    return !new WindowsCondition().matches(context, metadata) && !new MacCondition().matches(context, metadata);
  }
}
