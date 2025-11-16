package com.ldapbrowser.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom X509TrustManager that validates certificates against the application truststore.
 * Uses the truststore.pfx file managed by TruststoreService.
 * Captures certificate chain when validation fails for user review and import.
 */
public class TrustStoreTrustManager implements X509TrustManager {

  private static final Logger logger = LoggerFactory.getLogger(TrustStoreTrustManager.class);

  private final X509TrustManager defaultTrustManager;
  private final Path truststorePath;
  private final char[] truststorePassword;
  private final boolean truststoreEmpty;
  
  // Store the last failed certificate chain for potential import
  private X509Certificate[] lastFailedChain;
  private CertificateException lastException;

  /**
   * Creates a TrustStoreTrustManager using the specified truststore.
   *
   * @param truststorePath path to the truststore file
   * @param truststorePassword truststore password
   * @throws RuntimeException if truststore cannot be loaded
   */
  public TrustStoreTrustManager(Path truststorePath, char[] truststorePassword) {
    this.truststorePath = truststorePath;
    this.truststorePassword = truststorePassword;
    
    // Check if truststore is empty
    boolean empty = false;
    try {
      KeyStore ks = KeyStore.getInstance("PKCS12");
      if (Files.exists(truststorePath)) {
        try (var inputStream = Files.newInputStream(truststorePath)) {
          ks.load(inputStream, truststorePassword);
        }
        empty = (ks.size() == 0);
      } else {
        empty = true;
      }
    } catch (Exception e) {
      logger.warn("Could not check truststore size: {}", e.getMessage());
      empty = true;
    }
    this.truststoreEmpty = empty;
    
    if (empty) {
      logger.warn("Truststore is empty - all certificates will be rejected initially");
      this.defaultTrustManager = null;
    } else {
      this.defaultTrustManager = createTrustManager();
    }
  }

  /**
   * Creates the default trust manager from the truststore.
   *
   * @return X509TrustManager configured with truststore
   * @throws RuntimeException if trust manager creation fails
   */
  private X509TrustManager createTrustManager() {
    try {
      // Load the truststore
      KeyStore truststore = KeyStore.getInstance("PKCS12");
      
      if (Files.exists(truststorePath)) {
        try (var inputStream = Files.newInputStream(truststorePath)) {
          truststore.load(inputStream, truststorePassword);
        }
        logger.debug("Loaded truststore from {}", truststorePath);
      } else {
        // Initialize empty truststore if file doesn't exist
        truststore.load(null, truststorePassword);
        logger.warn("Truststore file does not exist: {}", truststorePath);
      }

      // Initialize trust manager factory with the truststore
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(
          TrustManagerFactory.getDefaultAlgorithm()
      );
      tmf.init(truststore);

      // Find and return the X509TrustManager
      for (TrustManager tm : tmf.getTrustManagers()) {
        if (tm instanceof X509TrustManager) {
          return (X509TrustManager) tm;
        }
      }

      throw new RuntimeException("No X509TrustManager found in TrustManagerFactory");

    } catch (KeyStoreException | IOException | NoSuchAlgorithmException 
             | CertificateException e) {
      logger.error("Failed to create trust manager from truststore", e);
      throw new RuntimeException("Failed to create trust manager", e);
    }
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    defaultTrustManager.checkClientTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    // If truststore is empty, reject all certificates but capture them for import
    if (truststoreEmpty || defaultTrustManager == null) {
      logger.warn("Certificate rejected - truststore is empty");
      CertificateException e = new CertificateException(
          "No trusted certificates in truststore - certificate must be imported"
      );
      lastFailedChain = chain;
      lastException = e;
      throw e;
    }
    
    try {
      defaultTrustManager.checkServerTrusted(chain, authType);
      logger.debug("Server certificate validated successfully");
      // Clear any previous failure
      lastFailedChain = null;
      lastException = null;
    } catch (CertificateException e) {
      logger.error("Server certificate validation failed: {}", e.getMessage());
      // Store the failed certificate chain for potential import
      lastFailedChain = chain;
      lastException = e;
      throw e;
    }
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    if (truststoreEmpty || defaultTrustManager == null) {
      return new X509Certificate[0];
    }
    return defaultTrustManager.getAcceptedIssuers();
  }

  /**
   * Gets the certificate chain from the last validation failure.
   *
   * @return certificate chain that failed validation, or null if no failure
   */
  public X509Certificate[] getLastFailedChain() {
    return lastFailedChain;
  }

  /**
   * Gets the exception from the last validation failure.
   *
   * @return exception from last validation failure, or null if no failure
   */
  public CertificateException getLastException() {
    return lastException;
  }

  /**
   * Clears the stored failure information.
   */
  public void clearFailureInfo() {
    lastFailedChain = null;
    lastException = null;
  }
}
