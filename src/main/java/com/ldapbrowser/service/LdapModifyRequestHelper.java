package com.ldapbrowser.service;

import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModifyRequest;
import java.util.List;

/**
 * Executes LDAP modify operations with optional controls.
 */
final class LdapModifyRequestHelper {

  void applyModify(
      LDAPConnectionPool pool,
      String dn,
      List<Modification> modifications,
      Control... controls
  ) throws LDAPException {
    if (controls != null && controls.length > 0) {
      ModifyRequest modifyRequest = new ModifyRequest(dn, modifications, controls);
      pool.modify(modifyRequest);
      return;
    }
    pool.modify(dn, modifications);
  }
}