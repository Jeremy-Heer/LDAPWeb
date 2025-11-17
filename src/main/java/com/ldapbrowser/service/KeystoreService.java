package com.ldapbrowser.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for managing the keystore used for password encryption.
 * Handles keystore initialization, key generation, and key retrieval.
 */
@Service
public class KeystoreService {

  private static final Logger logger = LoggerFactory.getLogger(KeystoreService.class);
  private static final String SETTINGS_DIR = ".ldapbrowser";
  private static final String KEYSTORE_FILE = "keystore.pfx";
  private static final String PIN_FILE = "keystore.pin";
  private static final String KEY_ALIAS = "ldap-password-encryption-key";
  private static final String KEYSTORE_TYPE = "PKCS12";
  private static final String KEY_ALGORITHM = "AES";
  private static final int KEY_SIZE = 256;

  private final Path keystorePath;
  private final Path pinPath;
  private final Path settingsDir;

  /**
   * Constructor initializes paths for keystore and PIN file.
   */
  public KeystoreService() {
    String userHome = System.getProperty("user.home");
    this.settingsDir = Path.of(userHome, SETTINGS_DIR);
    this.keystorePath = settingsDir.resolve(KEYSTORE_FILE);
    this.pinPath = settingsDir.resolve(PIN_FILE);
  }

  /**
   * Initializes the keystore if it doesn't exist.
   * Creates the keystore file, generates a random PIN, and creates an encryption key.
   *
   * @throws IOException if file operations fail
   * @throws KeyStoreException if keystore operations fail
   */
  public void initializeKeystoreIfNeeded() throws IOException, KeyStoreException {
    if (Files.exists(keystorePath)) {
      logger.debug("Keystore already exists at: {}", keystorePath);
      return;
    }

    logger.info("Initializing keystore at: {}", keystorePath);

    // Ensure settings directory exists
    if (!Files.exists(settingsDir)) {
      Files.createDirectories(settingsDir);
      logger.info("Created settings directory: {}", settingsDir);
    }

    // Generate random PIN
    String pin = generateRandomPin();
    Files.writeString(pinPath, pin, StandardCharsets.UTF_8);
    setRestrictivePermissions(pinPath);
    logger.info("Created PIN file: {}", pinPath);

    // Create keystore
    try {
      KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
      keyStore.load(null, pin.toCharArray());

      // Generate encryption key
      SecretKey key = generateEncryptionKey();
      KeyStore.SecretKeyEntry keyEntry = new KeyStore.SecretKeyEntry(key);
      KeyStore.ProtectionParameter protection = 
          new KeyStore.PasswordProtection(pin.toCharArray());
      keyStore.setEntry(KEY_ALIAS, keyEntry, protection);

      // Save keystore
      try (FileOutputStream fos = new FileOutputStream(keystorePath.toFile())) {
        keyStore.store(fos, pin.toCharArray());
      }

      logger.info("Created keystore with encryption key: {}", keystorePath);
    } catch (NoSuchAlgorithmException | CertificateException e) {
      throw new KeyStoreException("Failed to initialize keystore", e);
    }
  }

  /**
   * Gets the encryption key from the keystore.
   *
   * @return AES secret key for encryption/decryption
   * @throws KeyStoreException if key retrieval fails
   */
  public SecretKey getEncryptionKey() throws KeyStoreException {
    try {
      initializeKeystoreIfNeeded();

      String pin = getKeystorePassword();
      KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);

      try (InputStream is = Files.newInputStream(keystorePath)) {
        keyStore.load(is, pin.toCharArray());
      }

      KeyStore.ProtectionParameter protection = 
          new KeyStore.PasswordProtection(pin.toCharArray());
      KeyStore.SecretKeyEntry entry = 
          (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, protection);

      if (entry == null) {
        throw new KeyStoreException("Encryption key not found in keystore");
      }

      return entry.getSecretKey();
    } catch (IOException | NoSuchAlgorithmException 
        | CertificateException | UnrecoverableEntryException e) {
      throw new KeyStoreException("Failed to retrieve encryption key", e);
    }
  }

  /**
   * Rotates the encryption key by generating a new one.
   * Returns the old key so passwords can be re-encrypted.
   *
   * @return the old encryption key
   * @throws KeyStoreException if key rotation fails
   */
  public SecretKey rotateKey() throws KeyStoreException {
    logger.info("Rotating encryption key");

    try {
      // Get old key
      SecretKey oldKey = getEncryptionKey();

      // Load keystore
      String pin = getKeystorePassword();
      KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
      try (InputStream is = Files.newInputStream(keystorePath)) {
        keyStore.load(is, pin.toCharArray());
      }

      // Generate new key
      SecretKey newKey = generateEncryptionKey();
      KeyStore.SecretKeyEntry keyEntry = new KeyStore.SecretKeyEntry(newKey);
      KeyStore.ProtectionParameter protection = 
          new KeyStore.PasswordProtection(pin.toCharArray());

      // Replace key
      keyStore.setEntry(KEY_ALIAS, keyEntry, protection);

      // Save keystore
      try (FileOutputStream fos = new FileOutputStream(keystorePath.toFile())) {
        keyStore.store(fos, pin.toCharArray());
      }

      logger.info("Encryption key rotated successfully");
      return oldKey;
    } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
      throw new KeyStoreException("Failed to rotate encryption key", e);
    }
  }

  /**
   * Gets keystore statistics and metadata.
   *
   * @return formatted string with keystore information
   * @throws KeyStoreException if keystore access fails
   */
  public String getKeystoreStats() throws KeyStoreException {
    try {
      if (!Files.exists(keystorePath)) {
        return "Keystore not initialized";
      }

      initializeKeystoreIfNeeded();

      String pin = getKeystorePassword();
      KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
      try (InputStream is = Files.newInputStream(keystorePath)) {
        keyStore.load(is, pin.toCharArray());
      }

      Date creationDate = keyStore.getCreationDate(KEY_ALIAS);
      String creationDateStr = creationDate != null 
          ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
              .withZone(ZoneId.systemDefault())
              .format(creationDate.toInstant())
          : "Unknown";

      long fileSize = Files.size(keystorePath);

      return String.format(
          "Keystore Location: %s\n"
          + "Keystore Type: %s\n"
          + "Key Algorithm: %s\n"
          + "Key Size: %d bits\n"
          + "Key Alias: %s\n"
          + "Creation Date: %s\n"
          + "File Size: %d bytes",
          keystorePath,
          KEYSTORE_TYPE,
          KEY_ALGORITHM,
          KEY_SIZE,
          KEY_ALIAS,
          creationDateStr,
          fileSize
      );
    } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
      throw new KeyStoreException("Failed to retrieve keystore statistics", e);
    }
  }

  /**
   * Checks if the keystore is initialized.
   *
   * @return true if keystore exists and has encryption key
   */
  public boolean isInitialized() {
    try {
      if (!Files.exists(keystorePath)) {
        return false;
      }

      String pin = getKeystorePassword();
      KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
      try (InputStream is = Files.newInputStream(keystorePath)) {
        keyStore.load(is, pin.toCharArray());
      }

      return keyStore.containsAlias(KEY_ALIAS);
    } catch (Exception e) {
      logger.error("Error checking keystore initialization", e);
      return false;
    }
  }

  /**
   * Gets the settings directory path.
   *
   * @return settings directory path
   */
  public Path getSettingsDir() {
    return settingsDir;
  }

  /**
   * Gets the keystore password from the PIN file.
   *
   * @return keystore password
   * @throws IOException if PIN file cannot be read
   */
  String getKeystorePassword() throws IOException {
    if (!Files.exists(pinPath)) {
      throw new IOException("PIN file not found: " + pinPath);
    }
    return Files.readString(pinPath, StandardCharsets.UTF_8).trim();
  }

  /**
   * Generates a random PIN for keystore protection.
   *
   * @return random PIN string
   */
  private String generateRandomPin() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Generates an AES-256 encryption key.
   *
   * @return AES secret key
   * @throws KeyStoreException if key generation fails
   */
  private SecretKey generateEncryptionKey() throws KeyStoreException {
    try {
      KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
      keyGen.init(KEY_SIZE, new SecureRandom());
      return keyGen.generateKey();
    } catch (NoSuchAlgorithmException e) {
      throw new KeyStoreException("Failed to generate encryption key", e);
    }
  }

  /**
   * Sets restrictive file permissions on the PIN file (Unix-like systems).
   *
   * @param path path to PIN file
   */
  private void setRestrictivePermissions(Path path) {
    try {
      Set<PosixFilePermission> perms = new HashSet<>();
      perms.add(PosixFilePermission.OWNER_READ);
      perms.add(PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(path, perms);
      logger.debug("Set restrictive permissions on: {}", path);
    } catch (UnsupportedOperationException e) {
      logger.debug("POSIX file permissions not supported on this system");
    } catch (IOException e) {
      logger.warn("Failed to set restrictive permissions on: {}", path, e);
    }
  }
}
