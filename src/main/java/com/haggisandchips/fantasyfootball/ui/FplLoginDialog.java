package com.haggisandchips.fantasyfootball.ui;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.util.Optional;
import java.util.regex.Pattern;

// FPL's login page is a JS-driven identity provider (Ping Identity) with no public API or OAuth
// client registration, so this app can't perform the login itself. Automating it by embedding a
// real browser and capturing the resulting token was tried extensively - both JavaFX's own WebView
// (missing window.crypto.subtle/indexedDB, and no way to see past Datadome's anti-tampering JS) and
// JCEF/Chromium in both of its rendering modes (windowed mode renders but ignores all input;
// off-screen rendering needs a working OpenGL context via JOGL, which fails outright on the
// development machine) - every path hit an unresolved, machine-specific native issue. Rather than
// keep fighting embedded-browser compatibility, this just asks the user to paste the token: log in
// via their own working browser, find the "X-Api-Authorization" header FPL's frontend sends on any
// authenticated API call (e.g. a request to /api/me/) in dev tools' Network tab, and paste its
// value here.
public final class FplLoginDialog {

  // Loose on purpose: just "does this look like a bearer JWT", optionally prefixed with the
  // 'Bearer ' the X-Api-Authorization header (and the copy-to-clipboard bookmarklet) actually use.
  // Not trying to validate it's genuinely an FPL token - the real check is FPL's API accepting or
  // rejecting it once used.
  private static final Pattern LOOKS_LIKE_TOKEN =
      Pattern.compile("(?i)^(bearer\\s+)?[a-z0-9_-]+\\.[a-z0-9_-]+\\.[a-z0-9_-]+$");

  private FplLoginDialog() {
  }

  // Returns empty if the user cancels or pastes nothing.
  public static Optional<String> showAndCaptureToken(final Window owner) {

    final Dialog<String> dialog = new Dialog<>();
    dialog.initOwner(owner);
    dialog.setTitle("Log in to Fantasy Premier League");
    dialog.setHeaderText("Paste your FPL session token");

    final Label instructions = new Label(
        "1. Log in to fantasy.premierleague.com in your normal browser.\n"
            + "2. Open dev tools (F12) -> Network tab.\n"
            + "3. Find a request to /api/me/, /api/my-team/... or /api/entry/...\n"
            + "4. Copy the value of its X-Api-Authorization request header.\n"
            + "5. Paste it below.");
    instructions.setWrapText(true);

    final TextArea tokenField = new TextArea();
    tokenField.setPromptText("Bearer eyJhbGciOi...");
    tokenField.setWrapText(true);
    tokenField.setPrefRowCount(4);

    final String clipboardText = readClipboardText();
    if (clipboardText != null && LOOKS_LIKE_TOKEN.matcher(clipboardText.trim()).matches()) {
      tokenField.setText(clipboardText.trim());
      tokenField.selectAll();
    }

    final VBox content = new VBox(12, instructions, tokenField);
    content.setPadding(new Insets(12));
    dialog.getDialogPane().setContent(content);

    final ButtonType logInButtonType = new ButtonType("Log in", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(logInButtonType, ButtonType.CANCEL);

    dialog.setResultConverter(buttonType ->
        buttonType == logInButtonType ? tokenField.getText().trim() : null);

    return dialog.showAndWait().filter(token -> !token.isBlank());
  }

  // Best-effort only - clipboard access can fail for all sorts of reasons (nothing on it, another
  // app holding it, an unsupported flavor) and none of them should stop the dialog from opening.
  private static String readClipboardText() {

    try {
      final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
        return (String) clipboard.getData(DataFlavor.stringFlavor);
      }
    } catch (final Exception e) {
      // Ignored - falls through to an empty field, same as if nothing had been on the clipboard.
    }
    return null;
  }
}
