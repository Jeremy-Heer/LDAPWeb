package com.ldapbrowser.service;

import com.ldapbrowser.exception.CertificateValidationException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLHandshakeException;

/**
 * Handles LDAP certificate-validation failure detection and exception mapping.
 */
class LdapCertificateFailureHelper {

  /**
   * Creates a certificate validation exception when a certificate-related cause exists.
   *
   * @param messagePrefix prefix for the generated exception message
   * @param inspectedThrowable throwable whose cause chain is inspected
   * @param exceptionCause throwable set as the cause of CertificateValidationException
   * @param failedCertificate first failed certificate captured by trust manager
   * @return certificate exception, or null if the throwable is not certificate-related
   */
  CertificateValidationException createCertificateValidationException(
      String messagePrefix,
      Throwable inspectedThrowable,
      Throwable exceptionCause,
      X509Certificate failedCertificate) {
    Throwable failureCause = findCertificateFailureCause(inspectedThrowable);
    if (failureCause == null || failedCertificate == null) {
      return null;
    }

    return new CertificateValidationException(
        messagePrefix + failureCause.getMessage(),
        exceptionCause,
        new X509Certificate[] {failedCertificate}
    );
  }

  /**
   * Finds the first certificate-related cause in a throwable chain.
   *
   * @param throwable throwable to inspect
   * @return matching cause, or null if none is found
   */
  Throwable findCertificateFailureCause(Throwable throwable) {
    Throwable cause = throwable == null ? null : throwable.getCause();
    while (cause != null) {
      if (cause instanceof SSLHandshakeException || cause instanceof CertificateException) {
        return cause;
      }
      cause = cause.getCause();
    }
    return null;
  }
}