package com.ldapbrowser.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;

/**
 * Unit tests for {@link OAuthSecurityValidationConfig}.
 */
class OAuthSecurityValidationConfigTest {

  @Test
  @DisplayName("fails fast when mTLS enabled but ssl bundle is blank")
  void failsWhenBundleBlank() {
    OAuthSecurityProperties props = new OAuthSecurityProperties();
    props.getMtls().setEnabled(true);
    props.getMtls().setSslBundle(" ");

    SslBundles sslBundles = Mockito.mock(SslBundles.class);
    OAuthSecurityValidationConfig config =
        new OAuthSecurityValidationConfig(
            props, sslBundles, "tls_client_auth");

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ssl-bundle is blank");
  }

  @Test
  @DisplayName("resolves ssl bundle when mTLS enabled")
  void resolvesBundleWhenEnabled() {
    OAuthSecurityProperties props = new OAuthSecurityProperties();
    props.getMtls().setEnabled(true);
    props.getMtls().setSslBundle("oidc-mtls");

    SslBundles sslBundles = Mockito.mock(SslBundles.class);
    when(sslBundles.getBundle("oidc-mtls"))
        .thenReturn(Mockito.mock(SslBundle.class));

    OAuthSecurityValidationConfig config =
        new OAuthSecurityValidationConfig(
            props, sslBundles, "tls_client_auth");

    assertThatCode(config::validate).doesNotThrowAnyException();
    verify(sslBundles).getBundle("oidc-mtls");
  }
}