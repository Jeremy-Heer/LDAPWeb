package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link EncryptionService}.
 * Covers encrypt/decrypt round-trips, unique IV generation,
 * wrong-key failures, null/empty handling, and disabled mode.
 */
@ExtendWith(MockitoExtension.class)
class EncryptionServiceTest {

  @Mock
  private KeystoreService keystoreService;

  private SecretKey testKey;
  private EncryptionService service;

  @BeforeEach
  void setUp() throws Exception {
    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
    keyGen.init(256);
    testKey = keyGen.generateKey();

    lenient().when(keystoreService.getEncryptionKey()).thenReturn(testKey);
    service = new EncryptionService(keystoreService, true);
  }

  // ---------------------------------------------------------------
  // Encrypt / decrypt round-trip
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Encrypt/decrypt round-trip")
  class RoundTrip {

    @Test
    @DisplayName("simple ASCII text survives round-trip")
    void simpleText() throws Exception {
      String cleartext = "s3cretP@ssw0rd!";
      String encrypted = service.encryptPassword(cleartext);
      String decrypted = service.decryptPassword(encrypted);

      assertThat(decrypted).isEqualTo(cleartext);
      assertThat(encrypted).isNotEqualTo(cleartext);
    }

    @Test
    @DisplayName("empty string is returned as-is")
    void emptyString() throws Exception {
      String encrypted = service.encryptPassword("");
      assertThat(encrypted).isEmpty();

      String decrypted = service.decryptPassword("");
      assertThat(decrypted).isEmpty();
    }

    @Test
    @DisplayName("unicode text survives round-trip")
    void unicodeText() throws Exception {
      String cleartext = "p\u00e4ssw\u00f6rd-\u00fc\u00df\u20ac\u4e16\u754c";
      String encrypted = service.encryptPassword(cleartext);
      String decrypted = service.decryptPassword(encrypted);

      assertThat(decrypted).isEqualTo(cleartext);
    }

    @Test
    @DisplayName("long text (10 000 chars) survives round-trip")
    void longText() throws Exception {
      String cleartext = "A".repeat(10_000);
      String encrypted = service.encryptPassword(cleartext);
      String decrypted = service.decryptPassword(encrypted);

      assertThat(decrypted).isEqualTo(cleartext);
    }

    @Test
    @DisplayName("single character survives round-trip")
    void singleChar() throws Exception {
      String cleartext = "x";
      String encrypted = service.encryptPassword(cleartext);
      String decrypted = service.decryptPassword(encrypted);

      assertThat(decrypted).isEqualTo(cleartext);
    }

    @Test
    @DisplayName("special characters survive round-trip")
    void specialChars() throws Exception {
      String cleartext = "!@#$%^&*()_+-={}[]|\\:\";<>?,./~`\n\t\r";
      String encrypted = service.encryptPassword(cleartext);
      String decrypted = service.decryptPassword(encrypted);

      assertThat(decrypted).isEqualTo(cleartext);
    }
  }

  // ---------------------------------------------------------------
  // Unique IV — same plaintext produces different ciphertexts
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Unique IV generation")
  class UniqueIv {

    @Test
    @DisplayName("two encryptions of same text produce different ciphertexts")
    void differentCiphertexts() throws Exception {
      String cleartext = "same-password";
      String enc1 = service.encryptPassword(cleartext);
      String enc2 = service.encryptPassword(cleartext);

      assertThat(enc1).isNotEqualTo(enc2);

      // Both still decrypt to the original
      assertThat(service.decryptPassword(enc1)).isEqualTo(cleartext);
      assertThat(service.decryptPassword(enc2)).isEqualTo(cleartext);
    }

    @Test
    @DisplayName("ten encryptions all produce unique ciphertexts")
    void tenDistinctCiphertexts() throws Exception {
      String cleartext = "password123";
      java.util.Set<String> results = new java.util.HashSet<>();
      for (int i = 0; i < 10; i++) {
        results.add(service.encryptPassword(cleartext));
      }
      assertThat(results).hasSize(10);
    }
  }

  // ---------------------------------------------------------------
  // Wrong-key decryption
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Wrong key decryption")
  class WrongKey {

    @Test
    @DisplayName("decryption with different key throws EncryptionException")
    void wrongKeyFails() throws Exception {
      String encrypted = service.encryptPassword("secret");

      // Create a second service with a different key
      KeyGenerator keyGen = KeyGenerator.getInstance("AES");
      keyGen.init(256);
      SecretKey otherKey = keyGen.generateKey();
      when(keystoreService.getEncryptionKey()).thenReturn(otherKey);

      EncryptionService otherService =
          new EncryptionService(keystoreService, true);

      assertThatThrownBy(() -> otherService.decryptPassword(encrypted))
          .isInstanceOf(EncryptionService.EncryptionException.class)
          .hasMessageContaining("Failed to decrypt");
    }
  }

  // ---------------------------------------------------------------
  // Null / empty input handling
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Null and empty handling")
  class NullEmpty {

    @Test
    @DisplayName("encrypting null returns null")
    void encryptNull() throws Exception {
      assertThat(service.encryptPassword(null)).isNull();
    }

    @Test
    @DisplayName("decrypting null returns null")
    void decryptNull() throws Exception {
      assertThat(service.decryptPassword(null)).isNull();
    }

    @Test
    @DisplayName("encrypting empty returns empty")
    void encryptEmpty() throws Exception {
      assertThat(service.encryptPassword("")).isEmpty();
    }

    @Test
    @DisplayName("decrypting empty returns empty")
    void decryptEmpty() throws Exception {
      assertThat(service.decryptPassword("")).isEmpty();
    }
  }

  // ---------------------------------------------------------------
  // Encryption disabled mode
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Encryption disabled")
  class Disabled {

    private EncryptionService disabledService;

    @BeforeEach
    void setUp() {
      disabledService = new EncryptionService(keystoreService, false);
    }

    @Test
    @DisplayName("encrypt returns cleartext when disabled")
    void encryptPassthrough() throws Exception {
      String cleartext = "visible-password";
      assertThat(disabledService.encryptPassword(cleartext))
          .isEqualTo(cleartext);
    }

    @Test
    @DisplayName("decrypt returns input as-is when disabled")
    void decryptPassthrough() throws Exception {
      String input = "some-value";
      assertThat(disabledService.decryptPassword(input))
          .isEqualTo(input);
    }

    @Test
    @DisplayName("isEncryptionEnabled returns false when disabled")
    void enabledFlag() {
      assertThat(disabledService.isEncryptionEnabled()).isFalse();
    }
  }

  // ---------------------------------------------------------------
  // isPasswordEncrypted heuristic
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("isPasswordEncrypted heuristic")
  class PasswordDetection {

    @Test
    @DisplayName("encrypted output is detected as encrypted")
    void encryptedDetected() throws Exception {
      String encrypted = service.encryptPassword("test-password");
      assertThat(service.isPasswordEncrypted(encrypted)).isTrue();
    }

    @Test
    @DisplayName("cleartext is not detected as encrypted")
    void cleartextNotDetected() {
      assertThat(service.isPasswordEncrypted("plainPassword123")).isFalse();
    }

    @Test
    @DisplayName("null returns false")
    void nullReturnsFalse() {
      assertThat(service.isPasswordEncrypted(null)).isFalse();
    }

    @Test
    @DisplayName("empty returns false")
    void emptyReturnsFalse() {
      assertThat(service.isPasswordEncrypted("")).isFalse();
    }

    @Test
    @DisplayName("short Base64 is not detected as encrypted")
    void shortBase64NotDetected() {
      // Base64 of a few bytes — too short to be IV + ciphertext + tag
      assertThat(service.isPasswordEncrypted("AQID")).isFalse();
    }
  }

  // ---------------------------------------------------------------
  // Re-encrypt with old key
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Re-encryption")
  class Reencrypt {

    @Test
    @DisplayName("re-encrypt with old key produces valid ciphertext")
    void reencryptRoundTrip() throws Exception {
      // Encrypt with original key
      String cleartext = "migrate-me";
      String encrypted = service.encryptPassword(cleartext);

      // Rotate: create a new key, make the service use it
      SecretKey oldKey = testKey;
      KeyGenerator keyGen = KeyGenerator.getInstance("AES");
      keyGen.init(256);
      SecretKey newKey = keyGen.generateKey();
      when(keystoreService.getEncryptionKey()).thenReturn(newKey);

      EncryptionService newService =
          new EncryptionService(keystoreService, true);

      // Re-encrypt: decrypt with oldKey, encrypt with newKey
      String reencrypted = newService.reencryptPassword(encrypted, oldKey);

      // Decrypt with new service yields original cleartext
      assertThat(newService.decryptPassword(reencrypted))
          .isEqualTo(cleartext);
    }
  }

  // ---------------------------------------------------------------
  // Corrupt input
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Corrupt / invalid input")
  class CorruptInput {

    @Test
    @DisplayName("decrypting invalid Base64 throws EncryptionException")
    void invalidBase64() {
      assertThatThrownBy(() -> service.decryptPassword("not!!valid!!base64"))
          .isInstanceOf(EncryptionService.EncryptionException.class);
    }

    @Test
    @DisplayName("decrypting truncated ciphertext throws EncryptionException")
    void truncatedCiphertext() throws Exception {
      String encrypted = service.encryptPassword("hello");
      // Chop off the last few characters to corrupt it
      String truncated = encrypted.substring(0, encrypted.length() - 4);

      assertThatThrownBy(() -> service.decryptPassword(truncated))
          .isInstanceOf(EncryptionService.EncryptionException.class);
    }

    @Test
    @DisplayName("decrypting tampered ciphertext throws EncryptionException")
    void tamperedCiphertext() throws Exception {
      String encrypted = service.encryptPassword("hello");
      // Flip a character in the middle
      char[] chars = encrypted.toCharArray();
      int mid = chars.length / 2;
      chars[mid] = (chars[mid] == 'A') ? 'B' : 'A';
      String tampered = new String(chars);

      assertThatThrownBy(() -> service.decryptPassword(tampered))
          .isInstanceOf(EncryptionService.EncryptionException.class);
    }
  }

  // ---------------------------------------------------------------
  // isEncryptionEnabled
  // ---------------------------------------------------------------

  @Test
  @DisplayName("isEncryptionEnabled returns true when enabled")
  void enabledFlag() {
    assertThat(service.isEncryptionEnabled()).isTrue();
  }
}
