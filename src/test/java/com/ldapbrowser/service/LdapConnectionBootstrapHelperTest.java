package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.ExtendedResult;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import com.unboundid.util.ssl.SSLUtil;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LdapConnectionBootstrapHelper")
class LdapConnectionBootstrapHelperTest {

  private final LdapConnectionBootstrapHelper helper = new LdapConnectionBootstrapHelper();

  @Test
  @DisplayName("creates socket factory from SSLUtil")
  void createsSocketFactory() throws Exception {
    SSLUtil sslUtil = mock(SSLUtil.class);
    SSLContext sslContext = mock(SSLContext.class);
    SSLSocketFactory socketFactory = mock(SSLSocketFactory.class);
    when(sslUtil.createSSLContext()).thenReturn(sslContext);
    when(sslContext.getSocketFactory()).thenReturn(socketFactory);

    SocketFactory result = helper.createSocketFactory(sslUtil);

    assertThat(result).isSameAs(socketFactory);
    verify(sslUtil).createSSLContext();
    verify(sslContext).getSocketFactory();
  }

  @Test
  @DisplayName("applies StartTLS only when configured")
  void appliesStartTlsOnlyWhenConfigured() throws Exception {
    SSLUtil sslUtil = mock(SSLUtil.class);
    SSLContext sslContext = mock(SSLContext.class);
    when(sslUtil.createSSLContext()).thenReturn(sslContext);

    LDAPConnection connection = mock(LDAPConnection.class);
    LdapServerConfig config = new LdapServerConfig();
    config.setUseStartTls(true);
    config.setUseSsl(false);
    when(connection.processExtendedOperation(org.mockito.ArgumentMatchers.any(StartTLSExtendedRequest.class)))
      .thenReturn(mock(ExtendedResult.class));

    helper.applyStartTlsIfNeeded(connection, config, sslUtil);

    verify(connection).processExtendedOperation(
      org.mockito.ArgumentMatchers.any(StartTLSExtendedRequest.class));
  }

  @Test
  @DisplayName("skips StartTLS when SSL is enabled")
  void skipsStartTlsWhenSslEnabled() throws Exception {
    SSLUtil sslUtil = mock(SSLUtil.class);
    LDAPConnection connection = mock(LDAPConnection.class);
    LdapServerConfig config = new LdapServerConfig();
    config.setUseStartTls(true);
    config.setUseSsl(true);

    helper.applyStartTlsIfNeeded(connection, config, sslUtil);

    verify(connection, never()).processExtendedOperation(
        org.mockito.ArgumentMatchers.any(StartTLSExtendedRequest.class));
  }
}