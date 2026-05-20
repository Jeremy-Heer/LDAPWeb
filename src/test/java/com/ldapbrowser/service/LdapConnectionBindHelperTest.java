package com.ldapbrowser.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.LDAPConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

@DisplayName("LdapConnectionBindHelper")
class LdapConnectionBindHelperTest {

  private final LdapConnectionBindHelper helper = new LdapConnectionBindHelper();

  @Test
  @DisplayName("binds when bind DN is present")
  void bindsWhenBindDnPresent() throws Exception {
    LDAPConnection connection = mock(LDAPConnection.class);
    Logger logger = mock(Logger.class);
    LdapServerConfig config = new LdapServerConfig();
    config.setName("Server");
    config.setBindDn("cn=admin,dc=example,dc=com");
    config.setBindPassword("secret");

    helper.bindIfConfigured(connection, config, logger);

    verify(connection).bind("cn=admin,dc=example,dc=com", "secret");
  }

  @Test
  @DisplayName("skips bind when bind DN is empty")
  void skipsWhenBindDnEmpty() throws Exception {
    LDAPConnection connection = mock(LDAPConnection.class);
    Logger logger = mock(Logger.class);
    LdapServerConfig config = new LdapServerConfig();
    config.setName("Server");
    config.setBindDn("");
    config.setBindPassword("secret");

    helper.bindIfConfigured(connection, config, logger);

    verify(connection, never()).bind(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyString());
  }
}