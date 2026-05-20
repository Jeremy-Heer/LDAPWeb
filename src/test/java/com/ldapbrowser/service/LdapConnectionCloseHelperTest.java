package com.ldapbrowser.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.unboundid.ldap.sdk.LDAPConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LdapConnectionCloseHelper")
class LdapConnectionCloseHelperTest {

  private final LdapConnectionCloseHelper helper = new LdapConnectionCloseHelper();

  @Test
  @DisplayName("closes connection when non-null")
  void closesWhenNonNull() {
    LDAPConnection connection = mock(LDAPConnection.class);

    helper.closeQuietly(connection);

    verify(connection).close();
  }

  @Test
  @DisplayName("does nothing when null")
  void noOpWhenNull() {
    helper.closeQuietly(null);
  }
}