package com.ldapbrowser.config;

import java.net.http.HttpClient;
import javax.net.ssl.SSLContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.web.client.RestClient;

/**
 * Configures OAuth token endpoint calls with mTLS when enabled.
 */
@Configuration
@ConditionalOnProperty(name = "ldapbrowser.oauth.mtls.enabled", havingValue = "true")
public class OAuthMtlsTokenClientConfig {

  /**
   * Creates an OAuth authorization-code token client that uses the
   * configured SSL bundle for mutual TLS.
   *
   * @param restClientBuilder rest client builder
   * @param sslBundles available ssl bundles
   * @param oauthProperties oauth security settings
   * @return mTLS-enabled token response client
   */
  @Bean
  public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>
      oauthMtlsAuthorizationCodeTokenResponseClient(
          RestClient.Builder restClientBuilder,
          SslBundles sslBundles,
          OAuthSecurityProperties oauthProperties) {
    String bundle = oauthProperties.getMtls().getSslBundle();
    SSLContext sslContext = sslBundles.getBundle(bundle)
        .createSslContext();

    HttpClient httpClient = HttpClient.newBuilder()
        .sslContext(sslContext)
        .build();
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(httpClient);

    RestClientAuthorizationCodeTokenResponseClient client =
        new RestClientAuthorizationCodeTokenResponseClient();
    client.setRestClient(
        restClientBuilder
            .requestFactory(requestFactory)
            .build());

    return client;
  }
}