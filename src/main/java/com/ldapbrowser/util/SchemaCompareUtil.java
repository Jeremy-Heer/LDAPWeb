package com.ldapbrowser.util;

import com.unboundid.ldap.sdk.schema.AttributeSyntaxDefinition;
import com.unboundid.ldap.sdk.schema.AttributeTypeDefinition;
import com.unboundid.ldap.sdk.schema.MatchingRuleDefinition;
import com.unboundid.ldap.sdk.schema.MatchingRuleUseDefinition;
import com.unboundid.ldap.sdk.schema.ObjectClassDefinition;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Utility class for canonicalizing LDAP schema elements for comparison.
 * This class provides methods to normalize schema definitions by removing
 * vendor-specific extensions and ordering elements consistently.
 */
public final class SchemaCompareUtil {

  private SchemaCompareUtil() {
    // Utility class
  }

  /**
   * Canonicalizes an ObjectClassDefinition for comparison.
   *
   * @param def the object class definition
   * @param includeExtensions whether to include extensions in canonical form
   * @return canonical string representation
   */
  public static String canonical(ObjectClassDefinition def, boolean includeExtensions) {
    if (def == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("OID=").append(def.getOID());

    // Names (sorted)
    if (def.getNames() != null && def.getNames().length > 0) {
      TreeSet<String> names = new TreeSet<>(Arrays.asList(def.getNames()));
      sb.append(" NAMES=").append(names);
    }

    // Description
    if (def.getDescription() != null && !def.getDescription().isEmpty()) {
      sb.append(" DESC=").append(def.getDescription());
    }

    // Obsolete
    if (def.isObsolete()) {
      sb.append(" OBSOLETE");
    }

    // Superior classes (sorted)
    if (def.getSuperiorClasses() != null && def.getSuperiorClasses().length > 0) {
      TreeSet<String> superiors = new TreeSet<>(Arrays.asList(def.getSuperiorClasses()));
      sb.append(" SUP=").append(superiors);
    }

    // Type
    if (def.getObjectClassType() != null) {
      sb.append(" TYPE=").append(def.getObjectClassType().getName());
    }

    // Required attributes (sorted)
    if (def.getRequiredAttributes() != null && def.getRequiredAttributes().length > 0) {
      TreeSet<String> required = new TreeSet<>(Arrays.asList(def.getRequiredAttributes()));
      sb.append(" MUST=").append(required);
    }

    // Optional attributes (sorted)
    if (def.getOptionalAttributes() != null && def.getOptionalAttributes().length > 0) {
      TreeSet<String> optional = new TreeSet<>(Arrays.asList(def.getOptionalAttributes()));
      sb.append(" MAY=").append(optional);
    }

    // Extensions (sorted, optionally included)
    if (includeExtensions && def.getExtensions() != null && !def.getExtensions().isEmpty()) {
      Map<String, String[]> sorted = new TreeMap<>(def.getExtensions());
      sb.append(" EXT=").append(sorted);
    }

    return sb.toString();
  }

  /**
   * Canonicalizes an AttributeTypeDefinition for comparison.
   *
   * @param def the attribute type definition
   * @param includeExtensions whether to include extensions in canonical form
   * @return canonical string representation
   */
  public static String canonical(AttributeTypeDefinition def, boolean includeExtensions) {
    if (def == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("OID=").append(def.getOID());

    // Names (sorted)
    if (def.getNames() != null && def.getNames().length > 0) {
      TreeSet<String> names = new TreeSet<>(Arrays.asList(def.getNames()));
      sb.append(" NAMES=").append(names);
    }

    // Description
    if (def.getDescription() != null && !def.getDescription().isEmpty()) {
      sb.append(" DESC=").append(def.getDescription());
    }

    // Obsolete
    if (def.isObsolete()) {
      sb.append(" OBSOLETE");
    }

    // Superior type
    if (def.getSuperiorType() != null) {
      sb.append(" SUP=").append(def.getSuperiorType());
    }

    // Equality matching rule
    if (def.getEqualityMatchingRule() != null) {
      sb.append(" EQUALITY=").append(def.getEqualityMatchingRule());
    }

    // Ordering matching rule
    if (def.getOrderingMatchingRule() != null) {
      sb.append(" ORDERING=").append(def.getOrderingMatchingRule());
    }

    // Substring matching rule
    if (def.getSubstringMatchingRule() != null) {
      sb.append(" SUBSTR=").append(def.getSubstringMatchingRule());
    }

    // Syntax
    if (def.getSyntaxOID() != null) {
      sb.append(" SYNTAX=").append(def.getSyntaxOID());
    }

    // Single value
    if (def.isSingleValued()) {
      sb.append(" SINGLE-VALUE");
    }

    // Collective
    if (def.isCollective()) {
      sb.append(" COLLECTIVE");
    }

    // No user modification
    if (def.isNoUserModification()) {
      sb.append(" NO-USER-MODIFICATION");
    }

    // Usage
    if (def.getUsage() != null) {
      sb.append(" USAGE=").append(def.getUsage().getName());
    }

    // Extensions (sorted, optionally included)
    if (includeExtensions && def.getExtensions() != null && !def.getExtensions().isEmpty()) {
      Map<String, String[]> sorted = new TreeMap<>(def.getExtensions());
      sb.append(" EXT=").append(sorted);
    }

    return sb.toString();
  }

  /**
   * Canonicalizes a MatchingRuleDefinition for comparison.
   *
   * @param def the matching rule definition
   * @param includeExtensions whether to include extensions in canonical form
   * @return canonical string representation
   */
  public static String canonical(MatchingRuleDefinition def, boolean includeExtensions) {
    if (def == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("OID=").append(def.getOID());

    // Names (sorted)
    if (def.getNames() != null && def.getNames().length > 0) {
      TreeSet<String> names = new TreeSet<>(Arrays.asList(def.getNames()));
      sb.append(" NAMES=").append(names);
    }

    // Description
    if (def.getDescription() != null && !def.getDescription().isEmpty()) {
      sb.append(" DESC=").append(def.getDescription());
    }

    // Obsolete
    if (def.isObsolete()) {
      sb.append(" OBSOLETE");
    }

    // Syntax
    if (def.getSyntaxOID() != null) {
      sb.append(" SYNTAX=").append(def.getSyntaxOID());
    }

    // Extensions (sorted, optionally included)
    if (includeExtensions && def.getExtensions() != null && !def.getExtensions().isEmpty()) {
      Map<String, String[]> sorted = new TreeMap<>(def.getExtensions());
      sb.append(" EXT=").append(sorted);
    }

    return sb.toString();
  }

  /**
   * Canonicalizes a MatchingRuleUseDefinition for comparison.
   *
   * @param def the matching rule use definition
   * @param includeExtensions whether to include extensions in canonical form
   * @return canonical string representation
   */
  public static String canonical(MatchingRuleUseDefinition def, boolean includeExtensions) {
    if (def == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("OID=").append(def.getOID());

    // Names (sorted)
    if (def.getNames() != null && def.getNames().length > 0) {
      TreeSet<String> names = new TreeSet<>(Arrays.asList(def.getNames()));
      sb.append(" NAMES=").append(names);
    }

    // Description
    if (def.getDescription() != null && !def.getDescription().isEmpty()) {
      sb.append(" DESC=").append(def.getDescription());
    }

    // Obsolete
    if (def.isObsolete()) {
      sb.append(" OBSOLETE");
    }

    // Applicable attribute types (sorted)
    if (def.getApplicableAttributeTypes() != null 
        && def.getApplicableAttributeTypes().length > 0) {
      TreeSet<String> attributes = new TreeSet<>(
          Arrays.asList(def.getApplicableAttributeTypes())
      );
      sb.append(" APPLIES=").append(attributes);
    }

    // Extensions (sorted, optionally included)
    if (includeExtensions && def.getExtensions() != null && !def.getExtensions().isEmpty()) {
      Map<String, String[]> sorted = new TreeMap<>(def.getExtensions());
      sb.append(" EXT=").append(sorted);
    }

    return sb.toString();
  }

  /**
   * Canonicalizes an AttributeSyntaxDefinition for comparison.
   *
   * @param def the syntax definition
   * @param includeExtensions whether to include extensions in canonical form
   * @return canonical string representation
   */
  public static String canonical(AttributeSyntaxDefinition def, boolean includeExtensions) {
    if (def == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("OID=").append(def.getOID());

    // Description
    if (def.getDescription() != null && !def.getDescription().isEmpty()) {
      sb.append(" DESC=").append(def.getDescription());
    }

    // Extensions (sorted, optionally included)
    if (includeExtensions && def.getExtensions() != null && !def.getExtensions().isEmpty()) {
      Map<String, String[]> sorted = new TreeMap<>(def.getExtensions());
      sb.append(" EXT=").append(sorted);
    }

    return sb.toString();
  }
}
