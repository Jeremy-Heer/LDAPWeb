package com.ldapbrowser.service;

import com.unboundid.ldap.sdk.RootDSE;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper for interpreting Root DSE attributes.
 */
final class LdapRootDseHelper {

  private static final String DEFAULT_SCHEMA_DN = "cn=schema";

  List<String> getNamingContexts(RootDSE rootDse) {
    return getAttributeValues(rootDse, "namingContexts");
  }

  List<String> getPrivateNamingContexts(RootDSE rootDse) {
    return getAttributeValues(rootDse, "ds-private-naming-contexts");
  }

  boolean isControlSupported(RootDSE rootDse, String controlOid) {
    if (rootDse == null || controlOid == null) {
      return false;
    }
    String[] supportedControls = rootDse.getAttributeValues("supportedControl");
    if (supportedControls == null) {
      return false;
    }
    for (String control : supportedControls) {
      if (controlOid.equals(control)) {
        return true;
      }
    }
    return false;
  }

  boolean supportsSchemaModification(RootDSE rootDse) {
    if (rootDse == null) {
      return false;
    }
    String[] supportedFeatures = rootDse.getAttributeValues("supportedFeatures");
    if (supportedFeatures == null) {
      return false;
    }
    for (String feature : supportedFeatures) {
      if ("1.3.6.1.4.1.4203.1.5.1".equals(feature)
          || "1.3.6.1.4.1.42.2.27.9.5.4".equals(feature)) {
        return true;
      }
    }
    return false;
  }

  String getSchemaSubentryDn(RootDSE rootDse) {
    if (rootDse == null) {
      return DEFAULT_SCHEMA_DN;
    }

    String schemaDn = rootDse.getAttributeValue("subschemaSubentry");
    if (schemaDn != null && !schemaDn.isEmpty()) {
      return schemaDn;
    }

    schemaDn = rootDse.getAttributeValue("schemaNamingContext");
    if (schemaDn != null && !schemaDn.isEmpty()) {
      return schemaDn;
    }

    schemaDn = rootDse.getAttributeValue("schemaSubentry");
    if (schemaDn != null && !schemaDn.isEmpty()) {
      return schemaDn;
    }

    return DEFAULT_SCHEMA_DN;
  }

  private List<String> getAttributeValues(RootDSE rootDse, String attributeName) {
    if (rootDse == null) {
      return new ArrayList<>();
    }
    String[] values = rootDse.getAttributeValues(attributeName);
    return values != null ? List.of(values) : new ArrayList<>();
  }
}