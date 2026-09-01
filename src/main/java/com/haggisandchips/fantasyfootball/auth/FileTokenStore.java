package com.haggisandchips.fantasyfootball.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.Set;

// Used on Linux and anything else not specifically handled by WindowsCondition/DpapiTokenStore or
// MacCondition/KeychainTokenStore - there's no OS-native secure-storage equivalent reachable
// without a native-interop dependency, so this relies on POSIX file permissions instead: the token
// file (and its containing directory) are created readable/writable by the owning user only
// (rw-------/rwx------), the same protection a plaintext ~/.ssh/id_rsa or ~/.aws/credentials file
// gets. Not encryption - just enough to keep the token out of reach of any other local account,
// matching the other TokenStore implementations' own threat model (protection from other users on
// the same machine, not from someone with access to this user's own account).
@Slf4j
@Component
@Conditional(OtherOsCondition.class)
public class FileTokenStore implements TokenStore {

  private static final Set<PosixFilePermission> OWNER_ONLY_FILE = PosixFilePermissions.fromString("rw-------");

  private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY = PosixFilePermissions.fromString("rwx------");

  private static final Path TOKEN_FILE = tokenFile();

  // XDG Base Directory spec's data home (~/.local/share by default) - the conventional place for
  // this kind of small per-user application state on Linux.
  private static Path tokenFile() {

    final String xdgDataHome = System.getenv("XDG_DATA_HOME");
    final Path dataHome = xdgDataHome != null && !xdgDataHome.isBlank()
        ? Paths.get(xdgDataHome)
        : Paths.get(System.getProperty("user.home"), ".local", "share");

    return dataHome.resolve("FantasyFootball").resolve("fpl-token.dat");
  }

  @Override
  public Optional<String> load() {

    if (!Files.exists(TOKEN_FILE)) {
      return Optional.empty();
    }

    try {
      return Optional.of(Files.readString(TOKEN_FILE, StandardCharsets.UTF_8).trim())
          .filter(token -> !token.isBlank());
    } catch (final IOException e) {
      log.warn("Could not read stored FPL token ({}) - treating as logged out", e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public void save(final String token) {

    try {
      final Path directory = TOKEN_FILE.getParent();
      Files.createDirectories(directory);
      Files.setPosixFilePermissions(directory, OWNER_ONLY_DIRECTORY);

      Files.writeString(TOKEN_FILE, token, StandardCharsets.UTF_8);
      Files.setPosixFilePermissions(TOKEN_FILE, OWNER_ONLY_FILE);
    } catch (final IOException e) {
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
}
