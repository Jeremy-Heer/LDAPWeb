package com.ldapbrowser.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Configuration;

/**
 * Validates OAuth security settings on startup.
 */
@Configuration
@ConditionalOnProperty(name = "ldapbrowser.auth.mode", havingValue = "oauth")
public class OAuthSecurityValidationConfig {

  private static final Logger logger =
      LoggerFactory.getLogger(OAuthSecurityValidationConfig.class);

  private final OAuthSecurityProperties oauthProperties;
  private final String clientAuthMethod;
  private final SslBundles sslBundles;

  /**
   * Creates the OAuth security validation configuration.
   *
   * @param oauthProperties typed oauth security settings
   * @param clientAuthMethod oauth client authentication method
   */
  public OAuthSecurityValidationConfig(
      OAuthSecurityProperties oauthProperties,
      SslBundles sslBundles,
      @Value("${spring.security.oauth2.client.registration.oidc.client-authentication-method:client_secret_basic}")
          String clientAuthMethod) {
    this.oauthProperties = oauthProperties;
    this.sslBundles = sslBundles;
    this.clientAuthMethod = clientAuthMethod;
  }

  @PostConstruct
  void validate() {
    if (!oauthProperties.getMtls().isEnabled()) {
      return;
    }

    String bundle = oauthProperties.getMtls().getSslBundle();
    if (bundle == null || bundle.isBlank()) {
      throw new IllegalStateException(
          "OAuth mTLS is enabled but ldapbrowser.oauth.mtls.ssl-bundle is blank");
    }
    // Fail fast if the configured bundle cannot be resolved.
    sslBundles.getBundle(bundle);

    if (!"tls_client_auth".equals(clientAuthMethod)
        && !"self_signed_tls_client_auth".equals(clientAuthMethod)) {
      logger.warn(
          "OAuth mTLS is enabled but client-authentication-method='{}' is not an mTLS method",
          clientAuthMethod);
    }
  }
}