package com.ldapbrowser.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.cert.X509Certificate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link CertificateValidationException}.
 */
@DisplayName("CertificateValidationException")
class CertificateValidationExceptionTest {

  @Nested
  @DisplayName("getCertificateChain()")
  class GetCertificateChain {

    @Test
    @DisplayName("returns a defensive copy — mutating the result does not affect the exception")
    void returnedArrayIsDefensiveCopy() {
      X509Certificate cert = Mockito.mock(X509Certificate.class);
      X509Certificate[] original = {cert};

      CertificateValidationException ex =
          new CertificateValidationException("test", null, original);

      X509Certificate[] first = ex.getCertificateChain();
      assertThat(first).isNotNull().hasSize(1);

      // Mutate the returned copy — the exception must not be affected.
      first[0] = null;

      X509Certificate[] second = ex.getCertificateChain();
      assertThat(second[0]).as("internal chain must be unchanged after mutating copy")
          .isSameAs(cert);
    }

    @Test
    @DisplayName("returned array is a different instance each call")
    void eachCallReturnsDifferentInstance() {
      X509Certificate cert = Mockito.mock(X509Certificate.class);
      CertificateValidationException ex =
          new CertificateValidationException("test", null, new X509Certificate[]{cert});

      assertThat(ex.getCertificateChain())
          .isNotSameAs(ex.getCertificateChain());
    }

    @Test
    @DisplayName("returns null when chain was null")
    void nullChainReturnsNull() {
      CertificateValidationException ex =
          new CertificateValidationException("test", null, null);

      assertThat(ex.getCertificateChain()).isNull();
    }
  }
}
