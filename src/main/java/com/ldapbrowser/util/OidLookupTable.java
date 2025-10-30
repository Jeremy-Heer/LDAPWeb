package com.ldapbrowser.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive OID lookup table providing descriptions for LDAP OIDs
 * organized by category. Based on the LDAP OID Reference Guide from ldap.com.
 *
 * This utility can be used throughout the application to provide human-readable
 * descriptions for LDAP controls, extended operations, attribute types,
 * object classes, and other OID-identified elements.
 */
public final class OidLookupTable {

  // Extended Schema Info Request Control OID (Ping Identity Directory Server)
  public static final String EXTENDED_SCHEMA_INFO_OID = "1.3.6.1.4.1.30221.2.5.12";

  // =================================================
  // LDAP CONTROLS
  // =================================================
  private static final Map<String, String> CONTROL_DESCRIPTIONS = new HashMap<>();

  static {
    // Standard LDAP Controls
    CONTROL_DESCRIPTIONS.put("1.2.826.0.1.3344810.2.3", "Matched Values Request Control");
    CONTROL_DESCRIPTIONS.put("1.2.840.113556.1.4.319", "Simple Paged Results Control");
    CONTROL_DESCRIPTIONS.put("1.2.840.113556.1.4.473", "Server-Side Sort Request Control");
    CONTROL_DESCRIPTIONS.put("1.2.840.113556.1.4.474", "Server-Side Sort Response Control");
    CONTROL_DESCRIPTIONS.put("1.2.840.113556.1.4.805", "Tree Delete Request Control");
    CONTROL_DESCRIPTIONS.put("1.2.840.113556.1.4.841", "DirSync Request Control");
    CONTROL_DESCRIPTIONS.put("1.2.840.113556.1.4.1413", "Permissive Modify Request Control");
    CONTROL_DESCRIPTIONS.put("1.2.840.113556.1.4.1504", "Attribute Scoped Query Request Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.1.7.1", "LCUP Sync Request Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.1.7.2", "LCUP Sync Update Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.1.7.3", "LCUP Sync Done Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.1.12", "LDAP Assertion Request Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.1.13.1", "LDAP Pre-Read Request and Response Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.1.13.2", "LDAP Post-Read Request and Response Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.1.21.2", "Transaction Specification Request Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.1.22", "LDAP Don't Use Copy Control");
    CONTROL_DESCRIPTIONS.put("1.2.840.113549.6.0.0", "Signed Operation Request Control");
    CONTROL_DESCRIPTIONS.put("1.2.840.113549.6.0.1", "Demand Signed Result Request Control");
    CONTROL_DESCRIPTIONS.put("1.2.840.113549.6.0.2", "Signed Result Response Control");

    // Sun/Oracle Directory Server Controls
    CONTROL_DESCRIPTIONS.put("1.3.6.1.4.1.42.2.27.8.5.1", "Password Policy Request and Response Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.4.1.42.2.27.9.5.2", "Get Effective Rights Request Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.4.1.42.2.27.9.5.8", "Account Usable Request and Response Control");

    // OpenLDAP Controls
    CONTROL_DESCRIPTIONS.put("1.3.6.1.4.1.4203.1.9.1.1", "LDAP Content Synchronization Request Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.4.1.4203.1.9.1.2", "LDAP Content Synchronization State Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.4.1.4203.1.9.1.3", "LDAP Content Synchronization Done Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.4.1.4203.1.10.1", "LDAP Subentries Request Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.4.1.4203.1.10.2", "LDAP No-Op Request Control");

    // Netscape/iPlanet Controls
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.2", "ManageDsaIT Request Control");
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.3", "Persistent Search Request Control");
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.4", "Password Expired Response Control");
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.5", "Password Expiring Response Control");
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.7", "Entry Change Notification Response Control");
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.9", "Virtual List View Request Control");
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.10", "Virtual List View Response Control");
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.12", "Proxied Authorization (v1) Request Control");
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.15", "Authorization Identity Response Control");
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.16", "Authorization Identity Request Control");
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.17", "Real Attributes Only Request Control");
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.18", "Proxied Authorization (v2) Request Control");
    CONTROL_DESCRIPTIONS.put("2.16.840.1.113730.3.4.19", "Virtual Attributes Only Request Control");

    // Ping Identity Directory Server Controls
    CONTROL_DESCRIPTIONS.put("1.3.6.1.4.1.30221.2.5.12", "Extended Schema Info Request Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.4.1.30221.2.5.2", "Intermediate Client Request and Response Control");
    CONTROL_DESCRIPTIONS.put("1.3.6.1.4.1.30221.2.5.6", "Get Authorization Entry Request and Response Control");
  }

  // =================================================
  // PUBLIC UTILITY METHODS
  // =================================================

  /**
   * Gets the description for a control OID.
   *
   * @param oid The OID to look up
   * @return The description, or null if not found
   */
  public static String getControlDescription(String oid) {
    return CONTROL_DESCRIPTIONS.get(oid);
  }

  /**
   * Gets the description for any known OID.
   *
   * @param oid The OID to look up
   * @return The description with category prefix, or the OID itself if not found
   */
  public static String getAnyDescription(String oid) {
    String desc = getControlDescription(oid);
    if (desc != null) {
      return "[Control] " + desc;
    }
    return oid;
  }

  /**
   * Formats an OID with its description.
   *
   * @param oid The OID to format
   * @return Formatted string like "1.2.3.4 - Description" or just the OID if no description
   */
  public static String formatOidWithDescription(String oid) {
    String description = getAnyDescription(oid);
    if (!description.equals(oid)) {
      return oid + " - " + description;
    }
    return oid;
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private OidLookupTable() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }
}
