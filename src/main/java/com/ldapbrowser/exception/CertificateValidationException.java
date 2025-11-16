package com.ldapbrowser.exception;

import java.security.cert.X509Certificate;

/**
 * Exception thrown when certificate validation fails during LDAP connection.
 * Contains the failed certificate chain for potential user review and import.
 */
public class CertificateValidationException extends Exception {

  private final X509Certificate[] certificateChain;

  /**
   * Creates a certificate validation exception.
   *
   * @param message error message
   * @param cause underlying certificate exception
   * @param certificateChain the certificate chain that failed validation
   */
  public CertificateValidationException(String message, Throwable cause,
      X509Certificate[] certificateChain) {
    super(message, cause);
    this.certificateChain = certificateChain;
  }

  /**
   * Gets the certificate chain that failed validation.
   *
   * @return certificate chain, typically with server certificate at index 0
   */
  public X509Certificate[] getCertificateChain() {
    return certificateChain;
  }

  /**
   * Gets the server certificate (first certificate in chain).
   *
   * @return server certificate, or null if chain is empty
   */
  public X509Certificate getServerCertificate() {
    return (certificateChain != null && certificateChain.length > 0) 
        ? certificateChain[0] 
        : null;
  }
}
