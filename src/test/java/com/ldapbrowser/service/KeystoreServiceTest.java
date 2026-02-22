package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyStoreException;
import java.util.Set;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link KeystoreService}.
 *
 * <p>Each test redirects {@code user.home} to an isolated temp directory so that
 * the service operates on a fresh keystore and never modifies the real
 * {@code ~/.ldapbrowser} directory.
 */
@DisplayName("KeystoreService")
class KeystoreServiceTest {

  /** Per-test temp dir used as the fake {@code user.home}. */
  @TempDir
  Path tempDir;

  KeystoreService service;

  @BeforeEach
  void setUp() {
    service = new KeystoreService(tempDir.resolve(".ldapbrowser").toString());
  }

  // ---------------------------------------------------------------------------
  // Before initialization
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("before initialization")
  class BeforeInit {

    @Test
    @DisplayName("isInitialized returns false when no files exist")
    void isInitializedFalseBeforeInit() {
      assertThat(service.isInitialized()).isFalse();
    }

    @Test
    @DisplayName("getSettingsDir points inside the fake user.home")
    void settingsDirInsideUserHome() {
      assertThat(service.getSettingsDir().toString())
          .startsWith(tempDir.toString());
    }

    @Test
    @DisplayName("getKeystorePassword throws IOException when PIN file is absent")
    void getPasswordThrowsWhenPinMissing() {
      assertThatThrownBy(() -> service.getKeystorePassword())
          .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("getKeystoreStats returns 'not initialized' when keystore is absent")
    void statsReturnsNotInitializedWhenAbsent() throws KeyStoreException {
      assertThat(service.getKeystoreStats()).containsIgnoringCase("not initialized");
    }
  }

  // ---------------------------------------------------------------------------
  // Initialization
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("initialization")
  class Initialization {

    @Test
    @DisplayName("initializeKeystoreIfNeeded creates keystore and PIN files")
    void initCreatesKeystoreAndPinFiles() throws Exception {
      service.initializeKeystoreIfNeeded();

      Path settingsDir = service.getSettingsDir();
      assertThat(Files.exists(settingsDir.resolve("keystore.pfx"))).isTrue();
      assertThat(Files.exists(settingsDir.resolve("keystore.pin"))).isTrue();
    }

    @Test
    @DisplayName("isInitialized returns true after initialization")
    void isInitializedTrueAfterInit() throws Exception {
      service.initializeKeystoreIfNeeded();

      assertThat(service.isInitialized()).isTrue();
    }

    @Test
    @DisplayName("calling init a second time does not throw")
    void initIsIdempotent() throws Exception {
      service.initializeKeystoreIfNeeded();
      service.initializeKeystoreIfNeeded();
    }

    @Test
    @DisplayName("PIN file has owner-only read/write permissions on POSIX systems")
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void pinFileHasRestrictivePermissions() throws Exception {
      service.initializeKeystoreIfNeeded();

      Path pinFile = service.getSettingsDir().resolve("keystore.pin");
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(pinFile);

      assertThat(perms).doesNotContain(
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.GROUP_WRITE,
          PosixFilePermission.OTHERS_READ,
          PosixFilePermission.OTHERS_WRITE
      );
    }
  }

  // ---------------------------------------------------------------------------
  // Encryption key
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("encryption key")
  class EncryptionKey {

    @Test
    @DisplayName("getEncryptionKey returns an AES 256-bit key")
    void getEncryptionKeyIsAes256() throws Exception {
      SecretKey key = service.getEncryptionKey();

      assertThat(key.getAlgorithm()).isEqualTo("AES");
      // AES-256 encoded key is 32 bytes
      assertThat(key.getEncoded()).hasSize(32);
    }

    @Test
    @DisplayName("getEncryptionKey returns the same key on repeated calls")
    void getEncryptionKeyIsStable() throws Exception {
      SecretKey first = service.getEncryptionKey();
      SecretKey second = service.getEncryptionKey();

      assertThat(first.getEncoded()).isEqualTo(second.getEncoded());
    }

    @Test
    @DisplayName("rotateKey returns the original key; new key differs from original")
    void rotateKeyReturnsDifferentKey() throws Exception {
      service.initializeKeystoreIfNeeded();
      SecretKey original = service.getEncryptionKey();

      SecretKey returnedOld = service.rotateKey();
      SecretKey current = service.getEncryptionKey();

      assertThat(returnedOld.getEncoded()).isEqualTo(original.getEncoded());
      assertThat(current.getEncoded()).isNotEqualTo(original.getEncoded());
    }
  }

  // ---------------------------------------------------------------------------
  // Corrupt keystore file
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("corrupt keystore file")
  class CorruptFile {

    @Test
    @DisplayName("getEncryptionKey throws KeyStoreException when keystore contains garbage bytes")
    void getEncryptionKeyThrowsOnCorruptFile() throws Exception {
      service.initializeKeystoreIfNeeded();
      Files.write(service.getSettingsDir().resolve("keystore.pfx"),
          new byte[]{0x00, 0x01, 0x02, 0x03});

      assertThatThrownBy(() -> service.getEncryptionKey())
          .isInstanceOf(java.security.KeyStoreException.class);
    }

    @Test
    @DisplayName("getKeystoreStats returns 'not initialized' when keystore file is deleted")
    void statsNotInitializedAfterFileDeletion() throws Exception {
      service.initializeKeystoreIfNeeded();
      Files.delete(service.getSettingsDir().resolve("keystore.pfx"));
      Files.delete(service.getSettingsDir().resolve("keystore.pin"));

      assertThat(service.getKeystoreStats()).containsIgnoringCase("not initialized");
    }
  }

  // ---------------------------------------------------------------------------
  // Keystore stats
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("keystore stats")
  class KeystoreStats {

    @Test
    @DisplayName("getKeystoreStats after init contains algorithm and key size")
    void statsAfterInitContainsKeyInfo() throws Exception {
      service.initializeKeystoreIfNeeded();

      String stats = service.getKeystoreStats();

      assertThat(stats).contains("AES");
      assertThat(stats).contains("256");
    }

    @Test
    @DisplayName("getKeystoreStats includes the keystore location after init")
    void statsAfterInitContainsLocation() throws Exception {
      service.initializeKeystoreIfNeeded();

      String stats = service.getKeystoreStats();

      assertThat(stats).contains(tempDir.toString());
    }
  }
}
