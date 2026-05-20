package com.ldapbrowser.service;

import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.schema.Schema;
import org.slf4j.Logger;

/**
 * Helper for schema retrieval operations over LDAP.
 */
final class LdapSchemaQueryHelper {

  private static final String EXTENDED_SCHEMA_INFO_CONTROL_OID =
      "1.3.6.1.4.1.30221.2.5.12";

  private static final String[] SCHEMA_ATTRIBUTES = {
      "attributeTypes",
      "objectClasses",
      "ldapSyntaxes",
      "matchingRules",
      "matchingRuleUse",
      "dITContentRules",
      "nameForms",
      "dITStructureRules"
  };

  private final Logger logger;

  LdapSchemaQueryHelper(Logger logger) {
    this.logger = logger;
  }

  Schema tryRetrieveWithExtendedControl(LDAPConnectionPool pool, String schemaDn)
      throws LDAPException {
    if (schemaDn == null || schemaDn.isEmpty()) {
      return null;
    }

    SearchRequest request = new SearchRequest(
        schemaDn,
        SearchScope.BASE,
        "(objectClass=*)",
        SCHEMA_ATTRIBUTES
    );
    request.addControl(new Control(EXTENDED_SCHEMA_INFO_CONTROL_OID, false));

    SearchResult searchResult = pool.search(request);
    if (searchResult.getEntryCount() <= 0) {
      return null;
    }

    SearchResultEntry entry = searchResult.getSearchEntries().get(0);
    try {
      return new Schema(entry);
    } catch (Throwable t) {
      logger.debug("Could not parse schema from subentry, using standard retrieval", t);
      return null;
    }
  }
}