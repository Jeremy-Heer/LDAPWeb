package com.ldapbrowser.service;

import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import org.slf4j.Logger;

/**
 * Helper for LDAP schema modification operations.
 */
final class LdapSchemaModificationHelper {

  private final Logger logger;

  LdapSchemaModificationHelper(Logger logger) {
    this.logger = logger;
  }

  boolean canAccessSchemaSubentry(LDAPConnectionPool pool, String schemaDn) {
    if (schemaDn == null) {
      return false;
    }
    try {
      SearchResult result = pool.search(
          schemaDn,
          SearchScope.BASE,
          "(objectClass=*)",
          "objectClass"
      );
      return result != null && !result.getSearchEntries().isEmpty();
    } catch (LDAPException e) {
      logger.debug("Schema subentry not accessible: {}", e.getMessage());
      return false;
    }
  }

  void applyAddModification(
      LDAPConnectionPool pool,
      String schemaDn,
      String schemaAttributeName,
      String definition
  ) throws LDAPException {
    Modification addModification = new Modification(
        ModificationType.ADD,
        schemaAttributeName,
        definition
    );
    pool.modify(schemaDn, addModification);
  }
}