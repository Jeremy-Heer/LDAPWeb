package com.ldapbrowser.service;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

/**
 * Trust manager that captures the server certificate chain during SSL/TLS handshake.
 * This manager trusts all certificates and stores the full certificate chain
 * for retrieval and validation.
 */
public class CertificateCapturingTrustManager implements X509TrustManager {

  private X509Certificate[] capturedChain;

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    // Not used for server connections
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    // Capture the full certificate chain
    if (chain != null && chain.length > 0) {
      capturedChain = chain;
    }
    // Trust all certificates - we just want to capture them
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return new X509Certificate[0];
  }

  /**
   * Gets the captured server certificate (first in chain).
   *
   * @return the captured X509Certificate, or null if none was captured
   */
  public X509Certificate getCapturedCertificate() {
    return capturedChain != null && capturedChain.length > 0 ? capturedChain[0] : null;
  }

  /**
   * Gets the full captured certificate chain.
   *
   * @return the captured X509Certificate chain, or null if none was captured
   */
  public X509Certificate[] getCapturedChain() {
    return capturedChain;
  }
}
