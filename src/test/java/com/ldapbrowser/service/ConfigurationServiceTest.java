package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.EncryptionService.EncryptionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for ConfigurationService.
 * Uses a temporary directory as user.home to isolate test file I/O.
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

  @TempDir
  Path tempHome;

  @Mock
  private EncryptionService encryptionService;

  private String originalUserHome;
  private ConfigurationService service;

  @BeforeEach
  void setUp() {
    originalUserHome = System.getProperty("user.home");
    System.setProperty("user.home", tempHome.toString());
    // Encryption disabled by default for most tests; use lenient to avoid
    // UnnecessaryStubbing warnings in tests that change this behavior.
    lenient().when(encryptionService.isEncryptionEnabled()).thenReturn(false);
    service = new ConfigurationService(encryptionService);
  }

  @AfterEach
  void tearDown() {
    System.setProperty("user.home", originalUserHome);
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /** Builds a minimal server config with predictable field values. */
  private LdapServerConfig makeConfig(String name) {
    return new LdapServerConfig(
        name,
        "ldap.example.com",
        389,
        "dc=example,dc=com",
        "cn=admin,dc=example,dc=com",
        "secret",
        false,
        false
    );
  }

  // -------------------------------------------------------------------------
  // loadConfigurations
  // -------------------------------------------------------------------------

  @Nested
  class LoadConfigurations {

    @Test
    void emptyListWhenFileNotPresent() {
      List<LdapServerConfig> result = service.loadConfigurations();
      assertThat(result).isEmpty();
    }

    @Test
    void loadsMultipleConfigs() throws IOException {
      LdapServerConfig a = makeConfig("ServerA");
      LdapServerConfig b = makeConfig("ServerB");
      b.setHost("other.com");
      service.saveConfigurations(List.of(a, b));

      List<LdapServerConfig> loaded = service.loadConfigurations();
      assertThat(loaded).hasSize(2);
      assertThat(loaded).extracting(LdapServerConfig::getName)
          .containsExactlyInAnyOrder("ServerA", "ServerB");
    }

    @Test
    void returnsEmptyListOnCorruptJson() throws IOException {
      Path configPath = service.getConfigPath();
      Files.writeString(configPath, "{ not valid json !!!}");

      List<LdapServerConfig> result = service.loadConfigurations();
      assertThat(result).isEmpty();
    }

    @Test
    void preservesAllFieldsOnLoad() throws IOException {
      LdapServerConfig cfg = makeConfig("Fields");
      cfg.setPort(636);
      cfg.setUseSsl(true);
      cfg.setUseStartTls(false);
      cfg.setValidateCertificate(true);
      service.saveConfigurations(List.of(cfg));

      List<LdapServerConfig> loaded = service.loadConfigurations();
      assertThat(loaded).hasSize(1);
      LdapServerConfig got = loaded.get(0);
      assertThat(got.getName()).isEqualTo("Fields");
      assertThat(got.getHost()).isEqualTo("ldap.example.com");
      assertThat(got.getPort()).isEqualTo(636);
      assertThat(got.getBaseDn()).isEqualTo("dc=example,dc=com");
      assertThat(got.getBindDn()).isEqualTo("cn=admin,dc=example,dc=com");
      assertThat(got.getBindPassword()).isEqualTo("secret");
      assertThat(got.isUseSsl()).isTrue();
      assertThat(got.isUseStartTls()).isFalse();
      assertThat(got.isValidateCertificate()).isTrue();
    }
  }

  // -------------------------------------------------------------------------
  // saveConfigurations
  // -------------------------------------------------------------------------

  @Nested
  class SaveConfigurations {

    @Test
    void createsJsonFileOnSave() throws IOException {
      service.saveConfigurations(List.of(makeConfig("S1")));
      assertThat(service.getConfigPath()).exists();
    }

    @Test
    void savesEmptyListWithoutError() throws IOException {
      service.saveConfigurations(List.of());
      List<LdapServerConfig> loaded = service.loadConfigurations();
      assertThat(loaded).isEmpty();
    }

    @Test
    void roundTripPreservesName() throws IOException {
      LdapServerConfig cfg = makeConfig("MyServer");
      service.saveConfigurations(List.of(cfg));

      List<LdapServerConfig> loaded = service.loadConfigurations();
      assertThat(loaded).hasSize(1);
      // copy() appends " (Copy)" but saveConfigurations restores original name
      assertThat(loaded.get(0).getName()).isEqualTo("MyServer");
    }
  }

  // -------------------------------------------------------------------------
  // saveConfiguration (single)
  // -------------------------------------------------------------------------

  @Nested
  class SaveSingleConfiguration {

    @Test
    void addsNewConfigWhenNotExists() throws IOException {
      service.saveConfiguration(makeConfig("NewServer"));

      List<LdapServerConfig> loaded = service.loadConfigurations();
      assertThat(loaded).hasSize(1);
      assertThat(loaded.get(0).getName()).isEqualTo("NewServer");
    }

    @Test
    void updatesExistingConfigByName() throws IOException {
      LdapServerConfig original = makeConfig("Shared");
      service.saveConfiguration(original);

      LdapServerConfig updated = makeConfig("Shared");
      updated.setPort(636);
      service.saveConfiguration(updated);

      List<LdapServerConfig> loaded = service.loadConfigurations();
      assertThat(loaded).hasSize(1);
      assertThat(loaded.get(0).getPort()).isEqualTo(636);
    }

    @Test
    void accumulatesMultipleDistinctConfigs() throws IOException {
      service.saveConfiguration(makeConfig("Alpha"));
      service.saveConfiguration(makeConfig("Beta"));
      service.saveConfiguration(makeConfig("Gamma"));

      assertThat(service.loadConfigurations()).hasSize(3);
    }
  }

  // -------------------------------------------------------------------------
  // deleteConfiguration
  // -------------------------------------------------------------------------

  @Nested
  class DeleteConfiguration {

    @Test
    void removesConfigByName() throws IOException {
      service.saveConfigurations(List.of(makeConfig("Keep"), makeConfig("Remove")));
      service.deleteConfiguration("Remove");

      List<LdapServerConfig> loaded = service.loadConfigurations();
      assertThat(loaded).hasSize(1);
      assertThat(loaded.get(0).getName()).isEqualTo("Keep");
    }

    @Test
    void silentlyIgnoresMissingName() throws IOException {
      service.saveConfigurations(List.of(makeConfig("Existing")));
      // Should not throw
      service.deleteConfiguration("DoesNotExist");
      assertThat(service.loadConfigurations()).hasSize(1);
    }
  }

  // -------------------------------------------------------------------------
  // getConfiguration / configurationExists
  // -------------------------------------------------------------------------

  @Nested
  class QueryMethods {

    @Test
    void getConfigFindsExistingByName() throws IOException {
      service.saveConfigurations(List.of(makeConfig("Target")));
      Optional<LdapServerConfig> result = service.getConfiguration("Target");
      assertThat(result).isPresent();
      assertThat(result.get().getName()).isEqualTo("Target");
    }

    @Test
    void getConfigReturnsEmptyForMissingName() {
      Optional<LdapServerConfig> result = service.getConfiguration("Ghost");
      assertThat(result).isEmpty();
    }

    @Test
    void configExistsTrueWhenPresent() throws IOException {
      service.saveConfigurations(List.of(makeConfig("Present")));
      assertThat(service.configurationExists("Present")).isTrue();
    }

    @Test
    void configExistsFalseWhenAbsent() {
      assertThat(service.configurationExists("Absent")).isFalse();
    }
  }

  // -------------------------------------------------------------------------
  // Path accessors
  // -------------------------------------------------------------------------

  @Nested
  class PathAccessors {

    @Test
    void configPathEndsWithConnectionsJson() {
      assertThat(service.getConfigPath().getFileName().toString()).isEqualTo("connections.json");
    }

    @Test
    void configPathIsUnderSettingsDir() {
      // Use Path.startsWith() (structural, no filesystem access) rather than
      // AssertJ's PathAssert.startsWith() which requires the file to exist.
      assertThat(service.getConfigPath().startsWith(service.getSettingsDir())).isTrue();
    }

    @Test
    void settingsDirCreatedByConstructor() {
      assertThat(service.getSettingsDir()).exists().isDirectory();
    }
  }

  // -------------------------------------------------------------------------
  // Encryption integration
  // -------------------------------------------------------------------------

  @Nested
  class EncryptionIntegration {

    private static final String PLAIN = "secretPassword";
    private static final String CIPHER = "{ENC}abc123";

    @BeforeEach
    void enableEncryption() throws EncryptionException {
      // Override the outer lenient stub to enable encryption in this nested class.
      // Use lenient() here because some tests (null/empty password) exit before
      // isEncryptionEnabled() is ever invoked.
      lenient().when(encryptionService.isEncryptionEnabled()).thenReturn(true);
      lenient().when(encryptionService.isPasswordEncrypted(PLAIN)).thenReturn(false);
      lenient().when(encryptionService.isPasswordEncrypted(CIPHER)).thenReturn(true);
      lenient().when(encryptionService.encryptPassword(PLAIN)).thenReturn(CIPHER);
      lenient().when(encryptionService.decryptPassword(CIPHER)).thenReturn(PLAIN);
    }

    @Test
    void passwordIsEncryptedOnSave() throws IOException, EncryptionException {
      LdapServerConfig cfg = makeConfig("SecureServer");
      cfg.setBindPassword(PLAIN);
      service.saveConfigurations(List.of(cfg));

      // Verify encryption service was asked to encrypt
      verify(encryptionService).encryptPassword(PLAIN);
      // Verify ciphertext appears in the saved file
      String raw = Files.readString(service.getConfigPath());
      assertThat(raw).contains(CIPHER);
    }

    @Test
    void passwordIsDecryptedOnLoad() throws IOException {
      // Write ciphertext directly into the JSON file
      String json = "[{"
          + "\"name\":\"SecureServer\","
          + "\"host\":\"ldap.example.com\","
          + "\"port\":389,"
          + "\"bindPassword\":\"" + CIPHER + "\","
          + "\"useSsl\":false,"
          + "\"useStartTls\":false,"
          + "\"validateCertificate\":true"
          + "}]";
      Files.writeString(service.getConfigPath(), json);

      List<LdapServerConfig> loaded = service.loadConfigurations();
      assertThat(loaded).hasSize(1);
      assertThat(loaded.get(0).getBindPassword()).isEqualTo(PLAIN);
    }

    @Test
    void alreadyEncryptedPasswordNotDoubleEncrypted() throws IOException, EncryptionException {
      // Config whose password is already in ciphertext format
      LdapServerConfig cfg = makeConfig("AlreadyDone");
      cfg.setBindPassword(CIPHER);
      service.saveConfigurations(List.of(cfg));

      // encryptPassword should NOT be called for an already-encrypted value
      verify(encryptionService, never()).encryptPassword(CIPHER);
    }

    @Test
    void nullPasswordSkipsEncryption() throws IOException, EncryptionException {
      LdapServerConfig cfg = makeConfig("NoPass");
      cfg.setBindPassword(null);
      service.saveConfigurations(List.of(cfg));

      verify(encryptionService, never()).encryptPassword(null);
    }

    @Test
    void emptyPasswordSkipsEncryption() throws IOException, EncryptionException {
      LdapServerConfig cfg = makeConfig("EmptyPass");
      cfg.setBindPassword("");
      service.saveConfigurations(List.of(cfg));

      verify(encryptionService, never()).encryptPassword("");
    }

    @Test
    void encryptionDisabledLeavesPasswordCleartext() throws IOException, EncryptionException {
      // Re-disable encryption for this specific test
      when(encryptionService.isEncryptionEnabled()).thenReturn(false);

      LdapServerConfig cfg = makeConfig("ClearServer");
      cfg.setBindPassword(PLAIN);
      service.saveConfigurations(List.of(cfg));

      verify(encryptionService, never()).encryptPassword(PLAIN);
      String raw = Files.readString(service.getConfigPath());
      assertThat(raw).contains(PLAIN);
    }
  }

  // -------------------------------------------------------------------------
  // migratePasswords
  // -------------------------------------------------------------------------

  @Nested
  class MigratePasswords {

    private static final String PLAIN = "migrateMe";
    private static final String CIPHER = "{ENC}migrated";

    @BeforeEach
    void enableEncryption() throws EncryptionException {
      // Use lenient() because emptyListMigratesWithoutError never calls isEncryptionEnabled().
      lenient().when(encryptionService.isEncryptionEnabled()).thenReturn(true);
      lenient().when(encryptionService.isPasswordEncrypted(PLAIN)).thenReturn(false);
      lenient().when(encryptionService.isPasswordEncrypted(CIPHER)).thenReturn(true);
      lenient().when(encryptionService.encryptPassword(PLAIN)).thenReturn(CIPHER);
      lenient().when(encryptionService.decryptPassword(CIPHER)).thenReturn(PLAIN);
    }

    @Test
    void encryptsPlaintextPasswords() throws IOException {
      // Write cleartext JSON directly to bypass saveConfigurations() auto-encrypt
      String json = "[{"
          + "\"name\":\"Migrate1\","
          + "\"host\":\"h.example.com\","
          + "\"port\":389,"
          + "\"bindPassword\":\"" + PLAIN + "\","
          + "\"useSsl\":false,"
          + "\"useStartTls\":false,"
          + "\"validateCertificate\":true"
          + "}]";
      Files.writeString(service.getConfigPath(), json);

      service.migratePasswords(true);

      String raw = Files.readString(service.getConfigPath());
      assertThat(raw).contains(CIPHER);
      assertThat(raw).doesNotContain(PLAIN);
    }

    @Test
    void decryptsEncryptedPasswords() throws IOException {
      // Write ciphertext JSON directly
      String json = "[{"
          + "\"name\":\"Migrate2\","
          + "\"host\":\"h.example.com\","
          + "\"port\":389,"
          + "\"bindPassword\":\"" + CIPHER + "\","
          + "\"useSsl\":false,"
          + "\"useStartTls\":false,"
          + "\"validateCertificate\":true"
          + "}]";
      Files.writeString(service.getConfigPath(), json);

      service.migratePasswords(false);

      String raw = Files.readString(service.getConfigPath());
      assertThat(raw).contains(PLAIN);
      assertThat(raw).doesNotContain(CIPHER);
    }

    @Test
    void emptyListMigratesWithoutError() throws IOException {
      service.saveConfigurations(List.of());
      service.migratePasswords(true);
      assertThat(service.loadConfigurations()).isEmpty();
    }
  }
}
