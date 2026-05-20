package com.ldapbrowser.service;

import com.ldapbrowser.model.LdapEntry;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.schema.AttributeTypeDefinition;
import com.unboundid.ldap.sdk.schema.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps UnboundID LDAP search entries to application {@link LdapEntry} objects.
 */
final class LdapEntryMapper {

  LdapEntry mapEntry(SearchResultEntry entry, String serverName) {
    LdapEntry ldapEntry = new LdapEntry(entry.getDN(), serverName);
    for (Attribute attr : entry.getAttributes()) {
      for (String value : attr.getValues()) {
        ldapEntry.addAttribute(attr.getName(), value);
      }
    }
    return ldapEntry;
  }

  List<LdapEntry> mapEntries(List<SearchResultEntry> entries, String serverName) {
    List<LdapEntry> mapped = new ArrayList<>();
    for (SearchResultEntry entry : entries) {
      mapped.add(mapEntry(entry, serverName));
    }
    return mapped;
  }

  LdapEntry mapReadEntry(
      SearchResultEntry entry,
      String serverName,
      boolean includeOperational,
      Schema schema
  ) {
    LdapEntry ldapEntry = new LdapEntry(entry.getDN(), serverName);
    for (Attribute attr : entry.getAttributes()) {
      boolean isOperational = isOperationalAttribute(attr.getName(), schema);
      for (String value : attr.getValues()) {
        if (isOperational && includeOperational) {
          ldapEntry.addOperationalAttribute(attr.getName(), value);
        } else if (!isOperational) {
          ldapEntry.addAttribute(attr.getName(), value);
        }
      }
    }
    return ldapEntry;
  }

  private boolean isOperationalAttribute(String attributeName, Schema schema) {
    if (schema == null) {
      return false;
    }
    AttributeTypeDefinition attrDef = schema.getAttributeType(attributeName);
    if (attrDef == null || attrDef.getUsage() == null) {
      return false;
    }
    String usage = attrDef.getUsage().getName();
    return !usage.equalsIgnoreCase("userApplications");
  }
}