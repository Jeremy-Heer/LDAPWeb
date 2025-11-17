package com.ldapbrowser.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for encrypting and decrypting LDAP passwords.
 * Uses AES-256-GCM encryption with keys stored in the keystore.
 */
@Service
public class EncryptionService {

  private static final Logger logger = LoggerFactory.getLogger(EncryptionService.class);
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12; // 96 bits
  private static final int GCM_TAG_LENGTH = 128; // 128 bits

  private final KeystoreService keystoreService;
  private final boolean encryptionEnabled;

  /**
   * Constructor initializes the encryption service.
   *
   * @param keystoreService keystore service for key management
   * @param encryptionEnabled whether encryption is enabled
   */
  public EncryptionService(
      KeystoreService keystoreService,
      @Value("${ldapbrowser.encryption.enabled:true}") boolean encryptionEnabled) {
    this.keystoreService = keystoreService;
    this.encryptionEnabled = encryptionEnabled;
    logger.info("Encryption service initialized. Encryption enabled: {}", encryptionEnabled);
  }

  /**
   * Encrypts a password.
   *
   * @param cleartext cleartext password
   * @return Base64-encoded encrypted password
   * @throws EncryptionException if encryption fails
   */
  public String encryptPassword(String cleartext) throws EncryptionException {
    if (cleartext == null || cleartext.isEmpty()) {
      return cleartext;
    }

    if (!encryptionEnabled) {
      return cleartext;
    }

    try {
      // Get encryption key
      SecretKey key = keystoreService.getEncryptionKey();

      // Generate random IV
      byte[] iv = new byte[GCM_IV_LENGTH];
      SecureRandom random = new SecureRandom();
      random.nextBytes(iv);

      // Initialize cipher
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

      // Encrypt
      byte[] ciphertext = cipher.doFinal(cleartext.getBytes(StandardCharsets.UTF_8));

      // Combine IV + ciphertext (includes auth tag)
      ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
      byteBuffer.put(iv);
      byteBuffer.put(ciphertext);

      // Return Base64-encoded result
      return Base64.getEncoder().encodeToString(byteBuffer.array());
    } catch (Exception e) {
      throw new EncryptionException("Failed to encrypt password", e);
    }
  }

  /**
   * Decrypts a password.
   *
   * @param encrypted Base64-encoded encrypted password
   * @return cleartext password
   * @throws EncryptionException if decryption fails
   */
  public String decryptPassword(String encrypted) throws EncryptionException {
    if (encrypted == null || encrypted.isEmpty()) {
      return encrypted;
    }

    if (!encryptionEnabled) {
      return encrypted;
    }

    try {
      // Get encryption key
      SecretKey key = keystoreService.getEncryptionKey();

      // Decode Base64
      byte[] decoded = Base64.getDecoder().decode(encrypted);

      // Extract IV and ciphertext
      ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
      byte[] iv = new byte[GCM_IV_LENGTH];
      byteBuffer.get(iv);
      byte[] ciphertext = new byte[byteBuffer.remaining()];
      byteBuffer.get(ciphertext);

      // Initialize cipher
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

      // Decrypt
      byte[] plaintext = cipher.doFinal(ciphertext);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new EncryptionException("Failed to decrypt password", e);
    }
  }

  /**
   * Checks if encryption is enabled.
   *
   * @return true if encryption is enabled
   */
  public boolean isEncryptionEnabled() {
    return encryptionEnabled;
  }

  /**
   * Checks if a password is encrypted.
   * Heuristic: encrypted passwords are Base64-encoded and longer than typical cleartext.
   *
   * @param password password to check
   * @return true if password appears to be encrypted
   */
  public boolean isPasswordEncrypted(String password) {
    if (password == null || password.isEmpty()) {
      return false;
    }

    // Try to decode as Base64
    try {
      byte[] decoded = Base64.getDecoder().decode(password);
      // Encrypted passwords have IV (12 bytes) + ciphertext + tag (16 bytes)
      // Minimum length is 28 bytes for empty password
      return decoded.length >= 28;
    } catch (IllegalArgumentException e) {
      // Not valid Base64, must be cleartext
      return false;
    }
  }

  /**
   * Re-encrypts a password using a new key.
   * First decrypts with old key, then encrypts with current key.
   *
   * @param encrypted encrypted password
   * @param oldKey old encryption key
   * @return password encrypted with current key
   * @throws EncryptionException if re-encryption fails
   */
  public String reencryptPassword(String encrypted, SecretKey oldKey) throws EncryptionException {
    try {
      // Decode Base64
      byte[] decoded = Base64.getDecoder().decode(encrypted);

      // Extract IV and ciphertext
      ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
      byte[] iv = new byte[GCM_IV_LENGTH];
      byteBuffer.get(iv);
      byte[] ciphertext = new byte[byteBuffer.remaining()];
      byteBuffer.get(ciphertext);

      // Decrypt with old key
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, oldKey, parameterSpec);
      byte[] plaintext = cipher.doFinal(ciphertext);
      String cleartext = new String(plaintext, StandardCharsets.UTF_8);

      // Encrypt with current key
      return encryptPassword(cleartext);
    } catch (Exception e) {
      throw new EncryptionException("Failed to re-encrypt password", e);
    }
  }

  /**
   * Custom exception for encryption/decryption failures.
   */
  public static class EncryptionException extends Exception {
    public EncryptionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
