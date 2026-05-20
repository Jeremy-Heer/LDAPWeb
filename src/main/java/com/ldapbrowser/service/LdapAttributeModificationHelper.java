package com.ldapbrowser.service;

import com.ldapbrowser.util.LdifGenerator;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import java.util.List;
import org.slf4j.Logger;

/**
 * Applies attribute-level LDAP modifications and writes activity logs.
 */
final class LdapAttributeModificationHelper {

  private final LoggingService loggingService;
  private final Logger logger;

  LdapAttributeModificationHelper(LoggingService loggingService, Logger logger) {
    this.loggingService = loggingService;
    this.logger = logger;
  }

  void modifyAttribute(
      LDAPConnectionPool pool,
      String serverName,
      String dn,
      String attributeName,
      List<String> values
  ) throws LDAPException {
    Modification mod = new Modification(
        ModificationType.REPLACE,
        attributeName,
        values.toArray(new String[0])
    );
    pool.modify(dn, mod);
    logger.info("Modified attribute {} on {}", attributeName, dn);
    loggingService.logModification(
        serverName,
        "Modified attribute '" + attributeName + "' on entry",
        dn,
        LdifGenerator.replace(dn, attributeName, values)
    );
  }

  void addAttribute(
      LDAPConnectionPool pool,
      String serverName,
      String dn,
      String attributeName,
      List<String> values
  ) throws LDAPException {
    Modification mod = new Modification(
        ModificationType.ADD,
        attributeName,
        values.toArray(new String[0])
    );
    pool.modify(dn, mod);
    logger.info("Added attribute {} to {}", attributeName, dn);
    loggingService.logModification(
        serverName,
        "Added attribute '" + attributeName + "' to entry",
        dn,
        LdifGenerator.add(dn, attributeName, values)
    );
  }

  void deleteAttribute(
      LDAPConnectionPool pool,
      String serverName,
      String dn,
      String attributeName
  ) throws LDAPException {
    Modification mod = new Modification(ModificationType.DELETE, attributeName);
    pool.modify(dn, mod);
    logger.info("Deleted attribute {} from {}", attributeName, dn);
    loggingService.logModification(
        serverName,
        "Deleted attribute '" + attributeName + "' from entry",
        dn,
        LdifGenerator.delete(dn, attributeName)
    );
  }
}