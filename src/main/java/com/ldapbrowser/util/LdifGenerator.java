package com.ldapbrowser.util;

import java.util.List;

/**
 * Utility for generating LDIF change-record strings for attribute operations.
 *
 * <p>Centralises the identical {@code StringBuilder} patterns used by
 * {@code LdapService.modifyAttribute()}, {@code addAttribute()}, and
 * {@code deleteAttribute()}.
 */
public final class LdifGenerator {

  private LdifGenerator() {
  }

  /**
   * Builds an LDIF {@code replace} change record.
   *
   * @param dn            distinguished name of the entry
   * @param attributeName attribute to replace
   * @param values        replacement values
   * @return LDIF string
   */
  public static String replace(String dn, String attributeName, List<String> values) {
    return buildModifyLdif(dn, "replace", attributeName, values);
  }

  /**
   * Builds an LDIF {@code add} change record.
   *
   * @param dn            distinguished name of the entry
   * @param attributeName attribute to add
   * @param values        values to add
   * @return LDIF string
   */
  public static String add(String dn, String attributeName, List<String> values) {
    return buildModifyLdif(dn, "add", attributeName, values);
  }

  /**
   * Builds an LDIF {@code delete} change record for an entire attribute.
   *
   * @param dn            distinguished name of the entry
   * @param attributeName attribute to delete
   * @return LDIF string
   */
  public static String delete(String dn, String attributeName) {
    return buildModifyLdif(dn, "delete", attributeName, null);
  }

  /**
   * Core builder shared by all public factory methods.
   *
   * @param dn            distinguished name
   * @param operation     LDIF operation keyword (replace / add / delete)
   * @param attributeName attribute name
   * @param values        attribute values, or {@code null} for delete
   * @return LDIF string
   */
  private static String buildModifyLdif(
      String dn, String operation, String attributeName, List<String> values) {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(dn).append("\n");
    ldif.append("changetype: modify\n");
    ldif.append(operation).append(": ").append(attributeName).append("\n");
    if (values != null) {
      for (String value : values) {
        ldif.append(attributeName).append(": ").append(value).append("\n");
      }
    }
    ldif.append("-");
    return ldif.toString();
  }
}
