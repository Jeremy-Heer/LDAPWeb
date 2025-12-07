package com.ldapbrowser.service;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

/**
 * Trust manager that captures the server certificate during SSL/TLS handshake.
 * This manager trusts all certificates and stores the first certificate
 * in the chain for retrieval.
 */
public class CertificateCapturingTrustManager implements X509TrustManager {

  private X509Certificate capturedCertificate;

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    // Not used for server connections
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    // Capture the server certificate (first in chain)
    if (chain != null && chain.length > 0) {
      capturedCertificate = chain[0];
    }
    // Trust all certificates - we just want to capture it
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return new X509Certificate[0];
  }

  /**
   * Gets the captured server certificate.
   *
   * @return the captured X509Certificate, or null if none was captured
   */
  public X509Certificate getCapturedCertificate() {
    return capturedCertificate;
  }
}
