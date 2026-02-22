package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link TruststoreService}.
 *
 * <p>Each test redirects {@code user.home} to an isolated temp directory so that
 * the service operates on a fresh truststore and never touches the real
 * {@code ~/.ldapbrowser} directory.
 */
@DisplayName("TruststoreService")
class TruststoreServiceTest {

  /** Class-scoped temp dir used to generate a reusable test certificate. */
  @TempDir
  static Path certDir;

  static X509Certificate testCert;
  static String testPem;

  /** Per-test temp dir used as the fake {@code user.home}. */
  @TempDir
  Path tempDir;

  TruststoreService service;
  String originalUserHome;

  // ---------------------------------------------------------------------------
  // Class-level setup — generate a test certificate once using keytool
  // ---------------------------------------------------------------------------

  /**
   * Generates a self-signed RSA certificate using the JDK keytool binary.
   * The certificate is stored in a static field and reused across all tests.
   *
   * @throws Exception if keytool fails or the cert cannot be loaded
   */
  @BeforeAll
  static void generateTestCertificate() throws Exception {
    Path ksFile = certDir.resolve("test.pfx");
    String keytool = findKeytool();

    Process proc = new ProcessBuilder(
        keytool,
        "-genkeypair", "-keyalg", "RSA", "-keysize", "1024",
        "-validity", "3650",
        "-dname", "CN=Test, O=Test, C=US",
        "-keystore", ksFile.toString(),
        "-storepass", "testpass", "-keypass", "testpass",
        "-storetype", "PKCS12", "-alias", "testcert", "-noprompt"
    ).redirectErrorStream(true).start();

    int exit = proc.waitFor();
    if (exit != 0 || !Files.exists(ksFile)) {
      throw new AssertionError("keytool failed with exit code " + exit);
    }

    KeyStore ks = KeyStore.getInstance("PKCS12");
    try (FileInputStream fis = new FileInputStream(ksFile.toFile())) {
      ks.load(fis, "testpass".toCharArray());
    }
    testCert = (X509Certificate) ks.getCertificate("testcert");

    String b64 = Base64.getEncoder().encodeToString(testCert.getEncoded());
    testPem = "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----\n";
  }

  /**
   * Resolves the keytool binary from the running JVM's bin directory.
   *
   * @return absolute path to keytool, or {@code "keytool"} as a fallback
   */
  private static String findKeytool() {
    return ProcessHandle.current().info().command()
        .map(Path::of)
        .map(p -> p.getParent().resolve("keytool"))
        .filter(p -> Files.exists(p))
        .map(Path::toString)
        .orElse("keytool");
  }

  // ---------------------------------------------------------------------------
  // Per-test setup/teardown
  // ---------------------------------------------------------------------------

  @BeforeEach
  void setUp() {
    originalUserHome = System.getProperty("user.home");
    System.setProperty("user.home", tempDir.toString());
    service = new TruststoreService();
  }

  @AfterEach
  void tearDown() {
    System.setProperty("user.home", originalUserHome);
  }

  // ---------------------------------------------------------------------------
  // Initialization
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("initialization")
  class Initialization {

    @Test
    @DisplayName("creates truststore and PIN files when absent")
    void createsTruststoreAndPinFiles() throws Exception {
      assertThat(service.truststoreExists()).isFalse();

      service.initializeTruststoreIfNeeded();

      assertThat(Files.exists(service.getTruststorePath())).isTrue();
      assertThat(service.truststoreExists()).isTrue();
    }

    @Test
    @DisplayName("calling init a second time does not throw")
    void initIsIdempotent() throws Exception {
      service.initializeTruststoreIfNeeded();
      service.initializeTruststoreIfNeeded();
    }

    @Test
    @DisplayName("truststoreExists returns false before any call")
    void truststoreExistsFalseBeforeInit() {
      assertThat(service.truststoreExists()).isFalse();
    }

    @Test
    @DisplayName("getTruststorePath is located inside the temp user.home dir")
    void truststorePathInsideUserHome() {
      assertThat(service.getTruststorePath().toString())
          .startsWith(tempDir.toString());
    }
  }

  // ---------------------------------------------------------------------------
  // Certificate CRUD
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("certificate CRUD")
  class CertificateCrud {

    @BeforeEach
    void init() throws Exception {
      service.initializeTruststoreIfNeeded();
    }

    @Test
    @DisplayName("listCertificates returns empty list on a fresh truststore")
    void listIsEmptyOnFreshStore() throws Exception {
      assertThat(service.listCertificates()).isEmpty();
    }

    @Test
    @DisplayName("addCertificate makes the alias appear in listCertificates")
    void addThenListContainsAlias() throws Exception {
      service.addCertificate("mytest", testCert);

      List<String> aliases = service.listCertificates();

      assertThat(aliases).containsExactly("mytest");
    }

    @Test
    @DisplayName("addCertificate with a duplicate alias throws IllegalArgumentException")
    void addDuplicateAliasThrows() throws Exception {
      service.addCertificate("dup", testCert);

      assertThatThrownBy(() -> service.addCertificate("dup", testCert))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("dup");
    }

    @Test
    @DisplayName("removeCertificate removes the entry from the truststore")
    void removeDeletesEntry() throws Exception {
      service.addCertificate("toremove", testCert);
      service.removeCertificate("toremove");

      assertThat(service.listCertificates()).doesNotContain("toremove");
    }

    @Test
    @DisplayName("removeCertificate with a non-existent alias throws IllegalArgumentException")
    void removeNonExistentThrows() {
      assertThatThrownBy(() -> service.removeCertificate("nosuchcert"))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getCertificate returns null for an unknown alias")
    void getCertificateNullForUnknown() throws Exception {
      assertThat(service.getCertificate("bogus")).isNull();
    }

    @Test
    @DisplayName("getCertificate returns the certificate that was added")
    void getCertificateReturnsAddedCert() throws Exception {
      service.addCertificate("known", testCert);

      assertThat(service.getCertificate("known")).isEqualTo(testCert);
    }

    @Test
    @DisplayName("getCertificateDetails contains subject for a valid alias")
    void getCertificateDetailsContainsSubject() throws Exception {
      service.addCertificate("detail", testCert);

      String details = service.getCertificateDetails("detail");

      assertThat(details).containsIgnoringCase("CN=Test");
    }
  }

  // ---------------------------------------------------------------------------
  // PEM import
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("PEM import")
  class PemImport {

    @BeforeEach
    void init() throws Exception {
      service.initializeTruststoreIfNeeded();
    }

    @Test
    @DisplayName("importPemCertificates imports a single cert and returns 1")
    void importSinglePemReturnsOne() throws Exception {
      int count = service.importPemCertificates(testPem, "pem-alias");

      assertThat(count).isEqualTo(1);
      assertThat(service.listCertificates()).contains("pem-alias");
    }

    @Test
    @DisplayName("importPemCertificates with no certificate block throws")
    void importNoPemBlockThrows() {
      assertThatThrownBy(
          () -> service.importPemCertificates("this is not a certificate", "alias"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("No valid PEM");
    }
  }

  // ---------------------------------------------------------------------------
  // Password and stats
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("password and stats")
  class PasswordAndStats {

    @Test
    @DisplayName("getTruststorePassword returns a non-empty char array after init")
    void truststorePasswordNonEmpty() throws Exception {
      service.initializeTruststoreIfNeeded();

      char[] pwd = service.getTruststorePassword();

      assertThat(pwd).isNotEmpty();
    }

    @Test
    @DisplayName("getTruststoreStats contains certificate count after init")
    void statsContainsCertCount() throws Exception {
      service.initializeTruststoreIfNeeded();

      String stats = service.getTruststoreStats();

      assertThat(stats).contains("Certificates:");
    }

    @Test
    @DisplayName("getTruststoreStats returns 'not initialized' when absent")
    void statsNotInitializedMessage() throws Exception {
      assertThat(service.getTruststoreStats())
          .containsIgnoringCase("not initialized");
    }
  }
}
