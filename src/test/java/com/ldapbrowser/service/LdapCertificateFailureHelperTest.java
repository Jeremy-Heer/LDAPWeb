package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ldapbrowser.exception.CertificateValidationException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LdapCertificateFailureHelper")
class LdapCertificateFailureHelperTest {

  private final LdapCertificateFailureHelper helper = new LdapCertificateFailureHelper();

  @Test
  @DisplayName("creates exception for SSL handshake failure when certificate is available")
  void createsExceptionForSslHandshakeFailure() {
    X509Certificate cert = mock(X509Certificate.class);
    SSLHandshakeException handshakeException = new SSLHandshakeException("bad certificate");
    Throwable ldapException = new RuntimeException("ldap failed", handshakeException);

    CertificateValidationException result = helper.createCertificateValidationException(
        "Server certificate validation failed: ",
        ldapException,
        handshakeException,
        cert
    );

    assertThat(result).isNotNull();
    assertThat(result.getMessage()).contains("Server certificate validation failed: bad certificate");
    assertThat(result.getCause()).isSameAs(handshakeException);
    assertThat(result.getCertificateChain()).containsExactly(cert);
  }

  @Test
  @DisplayName("returns null when no failed certificate is available")
  void returnsNullWhenCertificateMissing() {
    SSLHandshakeException handshakeException = new SSLHandshakeException("bad certificate");
    Throwable ldapException = new RuntimeException("ldap failed", handshakeException);

    CertificateValidationException result = helper.createCertificateValidationException(
        "Certificate validation failed: ",
        ldapException,
        ldapException,
        null
    );

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("returns null when throwable chain has no certificate failure")
  void returnsNullWithoutCertificateFailureCause() {
    X509Certificate cert = mock(X509Certificate.class);
    Throwable ldapException = new RuntimeException("ldap failed", new IllegalStateException("other"));

    CertificateValidationException result = helper.createCertificateValidationException(
        "Certificate validation failed: ",
        ldapException,
        ldapException,
        cert
    );

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("finds nested CertificateException in cause chain")
  void findsNestedCertificateException() {
    Throwable root = new RuntimeException("root",
        new IllegalStateException("middle", new CertificateException("invalid cert")));

    Throwable result = helper.findCertificateFailureCause(root);

    assertThat(result)
        .isInstanceOf(CertificateException.class)
        .hasMessageContaining("invalid cert");
  }
}