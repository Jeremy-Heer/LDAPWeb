package com.ldapbrowser.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link OAuthMtlsTokenClientConfig}.
 */
class OAuthMtlsTokenClientConfigTest {

  @Test
  @DisplayName("builds token response client with ssl bundle")
  void buildsTokenClientWithSslBundle() throws Exception {
    OAuthSecurityProperties props = new OAuthSecurityProperties();
    props.getMtls().setSslBundle("oidc-mtls");

    SslBundles sslBundles = Mockito.mock(SslBundles.class);
    SslBundle bundle = Mockito.mock(SslBundle.class);
    when(sslBundles.getBundle("oidc-mtls")).thenReturn(bundle);
    when(bundle.createSslContext()).thenReturn(SSLContext.getDefault());

    OAuthMtlsTokenClientConfig config = new OAuthMtlsTokenClientConfig();

    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
        tokenClient = config.oauthMtlsAuthorizationCodeTokenResponseClient(
            RestClient.builder(), sslBundles, props);

    assertThat(tokenClient).isNotNull();
    verify(sslBundles).getBundle("oidc-mtls");
    verify(bundle).createSslContext();
  }
}