package com.haggisandchips.fantasyfootball;

import com.haggisandchips.fantasyfootball.ui.FantasyFootballDesktopApp;
import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FantasyFootballApplication {

  // TODO Log INFO (results) to file as well as console

  // Live squad fetching (AuthenticatedSquadProvider) is the default - log in via the desktop UI's
  // Account menu (see ui.FplLoginDialog). Set FPL_MY_SQUAD_FILE to a squad JSON file's path (see
  // my-squad.example.json) to use FileSquadProvider's stub instead, with no FPL account involved.

  public static void main(String[] args) {

    Application.launch(FantasyFootballDesktopApp.class, args);
  }
}
