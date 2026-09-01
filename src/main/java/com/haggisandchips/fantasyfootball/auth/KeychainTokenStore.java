package com.haggisandchips.fantasyfootball.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

// Stores the bearer token in the macOS login Keychain via the `security` CLI - the same store
// Keychain Access.app shows, encrypted at rest and unlocked by the user's own login, the direct
// macOS equivalent of DpapiTokenStore's Windows Credential Manager.
//
// Unlike DpapiTokenStore (which pipes its secret over stdin so it never appears in a process
// listing), `security add-generic-password` has no stdin/read-from-file option for its -w
// (password) flag - the token has to be passed as a real command-line argument, so it's briefly
// visible to other processes belonging to the same local user via `ps` while this runs. That's a
// narrower exposure than a whole file sitting on disk, and there's no macOS Keychain API reachable
// from pure Java without a native/JNI dependency this project doesn't want to take on, so it's
// accepted here rather than worked around.
@Slf4j
@Component
@Conditional(MacCondition.class)
public class KeychainTokenStore implements TokenStore {

  private static final String SERVICE = "FantasyFootball";

  private static final String ACCOUNT = "fpl-token";

  @Override
  public Optional<String> load() {

    try {
      final Result result = runSecurity("find-generic-password", "-a", ACCOUNT, "-s", SERVICE, "-w");
      if (result.exitCode() != 0) {
        // Covers both "nothing saved yet" (errSecItemNotFound) and any genuine failure - either
        // way there's no usable token, same as DpapiTokenStore's own catch-all below.
        return Optional.empty();
      }

      return Optional.of(result.output().trim()).filter(token -> !token.isBlank());
    } catch (final Exception e) {
      log.warn("Could not read stored FPL token from Keychain ({}) - treating as logged out", e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public void save(final String token) {

    try {
      // -U updates the item in place if one already exists (e.g. re-logging in after a token
      // expired) instead of failing with "the specified item already exists in the keychain".
      final Result result = runSecurity("add-generic-password", "-a", ACCOUNT, "-s", SERVICE, "-w", token, "-U");
      if (result.exitCode() != 0) {
        log.warn("Could not persist FPL token to Keychain ({}) - you'll need to log in again next launch",
            result.errorOutput());
      }
    } catch (final Exception e) {
      log.warn("Could not persist FPL token to Keychain ({}) - you'll need to log in again next launch", e.getMessage());
    }
  }

  @Override
  public void clear() {

    try {
      runSecurity("delete-generic-password", "-a", ACCOUNT, "-s", SERVICE);
    } catch (final Exception e) {
      log.warn("Could not delete stored FPL token from Keychain: {}", e.getMessage());
    }
  }

  private record Result(int exitCode, String output, String errorOutput) {
  }

  private static Result runSecurity(final String... args) throws IOException, InterruptedException {

    final String[] command = new String[args.length + 1];
    command[0] = "security";
    System.arraycopy(args, 0, command, 1, args.length);

    final Process process = new ProcessBuilder(command).start();
    process.getOutputStream().close();

    final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    final String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();

    if (!process.waitFor(10, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IOException("security timed out");
    }

    return new Result(process.exitValue(), output, errorOutput);
  }
}
