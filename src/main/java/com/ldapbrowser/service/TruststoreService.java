package com.ldapbrowser.service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Service for managing the application truststore.
 * Handles creation, loading, and CRUD operations for certificates
 * stored in ~/.truststore.pfx with password stored in ~/.truststore.pin.
 */
@Service
public class TruststoreService {

  private static final String SETTINGS_DIR = ".ldapbrowser";
  private static final String TRUSTSTORE_FILENAME = "truststore.pfx";
  private static final String PIN_FILENAME = "truststore.pin";
  private static final String KEYSTORE_TYPE = "PKCS12";
  private static final int PIN_LENGTH = 32;

  private final Path truststorePath;
  private final Path pinPath;
  private final Path settingsDir;

  /**
   * Creates the truststore service.
   * Initializes paths to ~/.ldapbrowser/truststore.pfx and ~/.ldapbrowser/truststore.pin.
   */
  public TruststoreService() {
    String userHome = System.getProperty("user.home");
    this.settingsDir = Paths.get(userHome, SETTINGS_DIR);
    this.truststorePath = settingsDir.resolve(TRUSTSTORE_FILENAME);
    this.pinPath = settingsDir.resolve(PIN_FILENAME);
  }

  /**
   * Initializes the truststore if it doesn't exist.
   * Creates both the .pfx file and .pin file with a random password.
   *
   * @throws Exception if initialization fails
   */
  public void initializeTruststoreIfNeeded() throws Exception {
    // Ensure settings directory exists
    if (!Files.exists(settingsDir)) {
      Files.createDirectories(settingsDir);
    }

    if (!Files.exists(truststorePath) || !Files.exists(pinPath)) {
      // Generate random pin
      String pin = generateRandomPin();

      // Create empty keystore
      KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
      keyStore.load(null, pin.toCharArray());

      // Save keystore
      try (FileOutputStream fos = new FileOutputStream(truststorePath.toFile())) {
        keyStore.store(fos, pin.toCharArray());
      }

      // Save pin
      Files.writeString(pinPath, pin);

      // Set restrictive permissions on pin file (Unix-like systems)
      try {
        pinPath.toFile().setReadable(false, false);
        pinPath.toFile().setReadable(true, true);
        pinPath.toFile().setWritable(false, false);
        pinPath.toFile().setWritable(true, true);
      } catch (Exception e) {
        // Permissions may not work on all platforms, log but continue
        System.err.println("Warning: Could not set restrictive permissions on pin file: " 
            + e.getMessage());
      }
    }
  }

  /**
   * Generates a random PIN for the truststore.
   *
   * @return random PIN string
   */
  private String generateRandomPin() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[PIN_LENGTH];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Loads the truststore PIN from ~/.truststore.pin.
   *
   * @return the PIN as a character array
   * @throws IOException if pin file cannot be read
   */
  private char[] loadPin() throws IOException {
    if (!Files.exists(pinPath)) {
      throw new IOException("Truststore PIN file does not exist: " + pinPath);
    }
    return Files.readString(pinPath).trim().toCharArray();
  }

  /**
   * Loads the keystore from disk.
   *
   * @return loaded KeyStore instance
   * @throws Exception if keystore cannot be loaded
   */
  private KeyStore loadKeyStore() throws Exception {
    char[] pin = loadPin();
    KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);

    try (FileInputStream fis = new FileInputStream(truststorePath.toFile())) {
      keyStore.load(fis, pin);
    }

    return keyStore;
  }

  /**
   * Saves the keystore to disk.
   *
   * @param keyStore keystore to save
   * @throws Exception if keystore cannot be saved
   */
  private void saveKeyStore(KeyStore keyStore) throws Exception {
    char[] pin = loadPin();
    try (FileOutputStream fos = new FileOutputStream(truststorePath.toFile())) {
      keyStore.store(fos, pin);
    }
  }

  /**
   * Lists all certificate aliases in the truststore.
   *
   * @return list of certificate aliases
   * @throws Exception if truststore cannot be accessed
   */
  public List<String> listCertificates() throws Exception {
    initializeTruststoreIfNeeded();
    KeyStore keyStore = loadKeyStore();
    List<String> aliases = new ArrayList<>();
    Collections.list(keyStore.aliases()).forEach(aliases::add);
    Collections.sort(aliases);
    return aliases;
  }

  /**
   * Gets a certificate by alias.
   *
   * @param alias certificate alias
   * @return certificate or null if not found
   * @throws Exception if truststore cannot be accessed
   */
  public Certificate getCertificate(String alias) throws Exception {
    initializeTruststoreIfNeeded();
    KeyStore keyStore = loadKeyStore();
    return keyStore.getCertificate(alias);
  }

  /**
   * Adds a certificate to the truststore.
   *
   * @param alias certificate alias
   * @param certificate certificate to add
   * @throws Exception if certificate cannot be added
   */
  public void addCertificate(String alias, Certificate certificate) throws Exception {
    initializeTruststoreIfNeeded();
    KeyStore keyStore = loadKeyStore();

    if (keyStore.containsAlias(alias)) {
      throw new IllegalArgumentException("Certificate with alias '" + alias + "' already exists");
    }

    keyStore.setCertificateEntry(alias, certificate);
    saveKeyStore(keyStore);
  }

  /**
   * Removes a certificate from the truststore.
   *
   * @param alias certificate alias to remove
   * @throws Exception if certificate cannot be removed
   */
  public void removeCertificate(String alias) throws Exception {
    initializeTruststoreIfNeeded();
    KeyStore keyStore = loadKeyStore();

    if (!keyStore.containsAlias(alias)) {
      throw new IllegalArgumentException("Certificate with alias '" + alias + "' does not exist");
    }

    keyStore.deleteEntry(alias);
    saveKeyStore(keyStore);
  }

  /**
   * Gets certificate details as a formatted string.
   *
   * @param alias certificate alias
   * @return formatted certificate details
   * @throws Exception if certificate cannot be accessed
   */
  public String getCertificateDetails(String alias) throws Exception {
    Certificate cert = getCertificate(alias);
    if (cert == null) {
      return "Certificate not found";
    }

    if (cert instanceof X509Certificate x509) {
      StringBuilder details = new StringBuilder();
      details.append("Subject: ").append(x509.getSubjectX500Principal().getName()).append("\n");
      details.append("Issuer: ").append(x509.getIssuerX500Principal().getName()).append("\n");
      details.append("Serial Number: ").append(x509.getSerialNumber()).append("\n");
      details.append("Valid From: ").append(x509.getNotBefore()).append("\n");
      details.append("Valid To: ").append(x509.getNotAfter()).append("\n");
      details.append("Signature Algorithm: ").append(x509.getSigAlgName()).append("\n");
      
      // Extended Key Usage
      try {
        java.util.List<String> extendedKeyUsage = x509.getExtendedKeyUsage();
        if (extendedKeyUsage != null && !extendedKeyUsage.isEmpty()) {
          details.append("\nExtended Key Usage:\n");
          for (String oid : extendedKeyUsage) {
            details.append("  ").append(formatKeyUsageOid(oid)).append("\n");
          }
        }
      } catch (java.security.cert.CertificateParsingException e) {
        details.append("\nExtended Key Usage: Error parsing\n");
      }
      
      // Subject Alternative Names
      try {
        java.util.Collection<java.util.List<?>> subjectAltNames = x509.getSubjectAlternativeNames();
        if (subjectAltNames != null && !subjectAltNames.isEmpty()) {
          details.append("\nSubject Alternative Names:\n");
          for (java.util.List<?> san : subjectAltNames) {
            if (san.size() >= 2) {
              Integer type = (Integer) san.get(0);
              Object value = san.get(1);
              details.append("  ").append(formatSanType(type)).append(": ")
                  .append(value).append("\n");
            }
          }
        }
      } catch (java.security.cert.CertificateParsingException e) {
        details.append("\nSubject Alternative Names: Error parsing\n");
      }
      
      return details.toString();
    }

    return cert.toString();
  }

  /**
   * Gets the path to the truststore file.
   *
   * @return truststore file path
   */
  public Path getTruststorePath() {
    return truststorePath;
  }

  /**
   * Checks if truststore exists.
   *
   * @return true if both truststore and pin file exist
   */
  public boolean truststoreExists() {
    return Files.exists(truststorePath) && Files.exists(pinPath);
  }

  /**
   * Gets truststore statistics.
   *
   * @return statistics as formatted string
   * @throws Exception if truststore cannot be accessed
   */
  public String getTruststoreStats() throws Exception {
    if (!truststoreExists()) {
      return "Truststore not initialized";
    }

    KeyStore keyStore = loadKeyStore();
    int certCount = Collections.list(keyStore.aliases()).size();
    long fileSize = Files.size(truststorePath);

    return String.format("Certificates: %d | File size: %d bytes | Location: %s",
        certCount, fileSize, truststorePath.toAbsolutePath().toString());
  }

  /**
   * Gets the settings directory path.
   *
   * @return Path to the settings directory
   */
  public Path getSettingsDir() {
    return settingsDir;
  }

  /**
   * Gets the truststore password for use with SSL/TLS connections.
   *
   * @return truststore password as character array
   * @throws IOException if PIN file cannot be read
   */
  public char[] getTruststorePassword() throws IOException {
    return loadPin();
  }

  /**
   * Formats a Key Usage OID to a readable name.
   *
   * @param oid the OID string
   * @return formatted key usage name
   */
  private String formatKeyUsageOid(String oid) {
    return switch (oid) {
      case "1.3.6.1.5.5.7.3.1" -> "TLS Web Server Authentication (" + oid + ")";
      case "1.3.6.1.5.5.7.3.2" -> "TLS Web Client Authentication (" + oid + ")";
      case "1.3.6.1.5.5.7.3.3" -> "Code Signing (" + oid + ")";
      case "1.3.6.1.5.5.7.3.4" -> "Email Protection (" + oid + ")";
      case "1.3.6.1.5.5.7.3.8" -> "Time Stamping (" + oid + ")";
      case "1.3.6.1.5.5.7.3.9" -> "OCSP Signing (" + oid + ")";
      default -> oid;
    };
  }

  /**
   * Formats a Subject Alternative Name type to a readable name.
   *
   * @param type the SAN type integer
   * @return formatted SAN type name
   */
  private String formatSanType(Integer type) {
    return switch (type) {
      case 0 -> "Other Name";
      case 1 -> "RFC822 Name";
      case 2 -> "DNS Name";
      case 3 -> "X400 Address";
      case 4 -> "Directory Name";
      case 5 -> "EDI Party Name";
      case 6 -> "URI";
      case 7 -> "IP Address";
      case 8 -> "Registered ID";
      default -> "Unknown (" + type + ")";
    };
  }
}
