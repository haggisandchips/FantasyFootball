package com.haggisandchips.fantasyfootball.auth;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

// True unless FPL_MY_SQUAD_FILE is set, in which case a stub squad is read from that file instead
// (see squad.FileSquadProvider) - no live FPL account involved, so none of the real API/auth
// machinery this condition guards should be wired up. The exact inverse of FileSquadProvider's own
// @ConditionalOnProperty.
public class LiveFplCondition implements Condition {

  @Override
  public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {

    final String squadFile = context.getEnvironment().getProperty("fpl.my-squad-file");
    return squadFile == null || squadFile.isBlank();
  }
}
