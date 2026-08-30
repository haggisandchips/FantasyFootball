package com.haggisandchips.fantasyfootball;

import com.haggisandchips.fantasyfootball.ui.FantasyFootballDesktopApp;
import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FantasyFootballApplication {

  // TODO Log INFO (results) to file as well as console

  // Real squad fetching (AuthenticatedSquadProvider) needs FPL_AUTH_ENABLED=true plus a captured
  // FPL_API_AUTHORIZATION bearer token (see FplSessionAuthClient); otherwise FileSquadProvider's
  // my-squad.json stub is used.

  static void main(String[] args) {

    Application.launch(FantasyFootballDesktopApp.class, args);
  }
}
