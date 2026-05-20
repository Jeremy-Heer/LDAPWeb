package com.ldapbrowser.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LdapModifyRequestHelper")
class LdapModifyRequestHelperTest {

  private final LdapModifyRequestHelper helper = new LdapModifyRequestHelper();

  @Test
  @DisplayName("uses direct modify when no controls are provided")
  void usesDirectModifyWithoutControls() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);
    List<Modification> modifications =
        List.of(new Modification(ModificationType.REPLACE, "description", "updated"));

    helper.applyModify(pool, "uid=user,dc=example,dc=com", modifications);

    verify(pool).modify(eq("uid=user,dc=example,dc=com"), eq(modifications));
  }

  @Test
  @DisplayName("uses modify request when controls are provided")
  void usesModifyRequestWithControls() throws Exception {
    LDAPConnectionPool pool = mock(LDAPConnectionPool.class);
    List<Modification> modifications =
        List.of(new Modification(ModificationType.REPLACE, "description", "updated"));
    Control control = new Control("1.2.840.113556.1.4.1413", true);

    helper.applyModify(pool, "uid=user,dc=example,dc=com", modifications, control);

    verify(pool).modify(any(com.unboundid.ldap.sdk.ModifyRequest.class));
  }
}