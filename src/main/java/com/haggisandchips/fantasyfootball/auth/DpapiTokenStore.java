package com.haggisandchips.fantasyfootball.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

// Encrypts the bearer token with Windows DPAPI before writing it to disk, so the file on its own
// is useless without the current Windows user's login - the same protection Windows Credential
// Manager relies on. Shells out to PowerShell's ConvertTo/From-SecureString (a thin wrapper over
// CryptProtectData/CryptUnprotectData) rather than pulling in a native-interop dependency just for
// this; the token itself is piped over stdin, never passed as a process argument, so it never shows
// up in a process listing.
//
// Stored under LOCALAPPDATA, not the roaming APPDATA - DPAPI keys are tied to the local user/
// machine, and a roamed copy of this file can fail to decrypt on a different machine unless
// credential roaming is explicitly configured (it isn't, by default), so roaming it would be
// actively misleading.
//
// Windows-only (see WindowsCondition) - powershell.exe doesn't exist elsewhere, so without this
// gate every load/save/clear would just fail there (silently, since the catch blocks below treat
// any failure as "nothing to load"/"couldn't persist"), meaning the token would never actually
// survive a relaunch on any other OS. See KeychainTokenStore (macOS) and FileTokenStore (Linux/
// everything else) for the equivalents used there.
@Slf4j
@Component
@Conditional(WindowsCondition.class)
public class DpapiTokenStore implements TokenStore {

  private static final Path TOKEN_FILE = Paths.get(
      System.getenv().getOrDefault("LOCALAPPDATA", System.getProperty("user.home")),
      "FantasyFootball", "fpl-token.dat");

  private static final String ENCRYPT_SCRIPT =
      "$token = [Console]::In.ReadToEnd(); "
          + "$secure = ConvertTo-SecureString -String $token -AsPlainText -Force; "
          + "ConvertFrom-SecureString $secure";

  private static final String DECRYPT_SCRIPT =
      "$encrypted = [Console]::In.ReadToEnd(); "
          + "$secure = ConvertTo-SecureString -String $encrypted; "
          + "$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure); "
          + "try { [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr) } "
          + "finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }";

  @Override
  public Optional<String> load() {

    if (!Files.exists(TOKEN_FILE)) {
      return Optional.empty();
    }

    try {
      final String encrypted = Files.readString(TOKEN_FILE, StandardCharsets.UTF_8).trim();
      return Optional.ofNullable(runPowerShell(DECRYPT_SCRIPT, encrypted));
    } catch (final Exception e) {
      log.warn("Could not read stored FPL token ({}) - treating as logged out", e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public void save(final String token) {

    try {
      final String encrypted = runPowerShell(ENCRYPT_SCRIPT, token);
      Files.createDirectories(TOKEN_FILE.getParent());
      Files.writeString(TOKEN_FILE, encrypted, StandardCharsets.UTF_8);
    } catch (final Exception e) {
      log.warn("Could not persist FPL token to disk ({}) - you'll need to log in again next launch", e.getMessage());
    }
  }

  @Override
  public void clear() {

    try {
      Files.deleteIfExists(TOKEN_FILE);
    } catch (final IOException e) {
      log.warn("Could not delete stored FPL token file {}: {}", TOKEN_FILE, e.getMessage());
    }
  }

  private static String runPowerShell(final String script, final String stdin) throws IOException, InterruptedException {

    final Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
        .start();

    process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
    process.getOutputStream().close();

    final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    final String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();

    if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
      throw new IOException("powershell exited abnormally: " + errorOutput);
    }

    return output.isBlank() ? null : output;
  }
}
