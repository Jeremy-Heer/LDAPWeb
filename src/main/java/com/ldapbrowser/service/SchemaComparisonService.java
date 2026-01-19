package com.ldapbrowser.service;

import com.ldapbrowser.model.LdapServerConfig;
import com.unboundid.ldap.sdk.schema.AttributeTypeDefinition;
import com.unboundid.ldap.sdk.schema.ObjectClassDefinition;
import com.unboundid.ldap.sdk.schema.Schema;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Service for common schema comparison operations.
 * Centralizes schema loading, comparison, and filtering logic.
 */
@Service
public class SchemaComparisonService {

  private final LdapService ldapService;
  private final LoggingService loggingService;

  /**
   * Creates the schema comparison service.
   *
   * @param ldapService the service used to retrieve LDAP schema information
   * @param loggingService the service used for logging schema comparison information
   */
  public SchemaComparisonService(LdapService ldapService, LoggingService loggingService) {
    this.ldapService = ldapService;
    this.loggingService = loggingService;
  }

  /**
   * Loads schemas from multiple servers with cache clearing.
   *
   * @param servers list of servers to load from
   * @return map of server name to schema (null on error)
   */
  public Map<String, Schema> loadSchemas(List<LdapServerConfig> servers) {
    Map<String, Schema> schemas = new LinkedHashMap<>();

    // Clear cache for all servers
    for (LdapServerConfig cfg : servers) {
      ldapService.clearSchemaCache(cfg.getName());
    }

    // Determine extended schema support
    boolean allSupportExtended = checkExtendedSchemaSupport(servers);
    loggingService.logDebug("SCHEMA",
        "Extended schema info control support: " + allSupportExtended);

    // Fetch schemas
    for (LdapServerConfig cfg : servers) {
      try {
        Schema schema = ldapService.getSchema(cfg.getName(), allSupportExtended);
        schemas.put(cfg.getName(), schema);
        loggingService.logDebug("SCHEMA",
            "Successfully loaded schema from " + cfg.getName());
      } catch (Exception e) {
        schemas.put(cfg.getName(), null);
        loggingService.logError("SCHEMA",
            "Failed to load schema from " + cfg.getName(), e.getMessage());
      }
    }

    return schemas;
  }

  /**
   * Checks if all servers support extended schema info control.
   *
   * @param servers list of servers to check
   * @return true if all servers support the control, false otherwise
   */
  private boolean checkExtendedSchemaSupport(List<LdapServerConfig> servers) {
    final String extendedSchemaInfoOid = "1.3.6.1.4.1.30221.2.5.12";

    for (LdapServerConfig cfg : servers) {
      try {
        if (!ldapService.isConnected(cfg.getName())) {
          ldapService.connect(cfg);
        }
        if (!ldapService.isControlSupported(cfg.getName(), extendedSchemaInfoOid)) {
          return false;
        }
      } catch (Exception e) {
        loggingService.logDebug("SCHEMA",
            "Error checking extended schema support for " + cfg.getName()
                + ": " + e.getMessage());
        return false;
      }
    }
    return true;
  }

  /**
   * Finds missing attribute types in target compared to source.
   *
   * @param source source schema
   * @param target target schema
   * @return set of missing attribute type names
   */
  public Set<String> findMissingAttributeTypes(Schema source, Schema target) {
    if (source == null || target == null) {
      return Collections.emptySet();
    }

    Set<String> missing = new TreeSet<>();
    for (AttributeTypeDefinition attr : source.getAttributeTypes()) {
      String name = attr.getNameOrOID();
      if (target.getAttributeType(name) == null) {
        missing.add(name);
      }
    }
    return missing;
  }

  /**
   * Finds missing or different object classes in target compared to source.
   *
   * @param source source schema
   * @param target target schema
   * @return set of object class names (missing or with different attributes)
   */
  public Set<String> findMissingObjectClasses(Schema source, Schema target) {
    if (source == null || target == null) {
      return Collections.emptySet();
    }

    Set<String> missing = new TreeSet<>();
    for (ObjectClassDefinition oc : source.getObjectClasses()) {
      String name = oc.getNameOrOID();
      ObjectClassDefinition targetOc = target.getObjectClass(name);

      if (targetOc == null) {
        // Completely missing
        missing.add(name);
      } else {
        // Check if attributes differ
        if (hasAttributeDifferences(oc, targetOc)) {
          missing.add(name);
        }
      }
    }
    return missing;
  }

  /**
   * Checks if object class exists in target with different attributes.
   *
   * @param name object class name
   * @param target target schema
   * @return true if object class exists but has different MUST/MAY attributes
   */
  public boolean existsWithDifferentAttributes(String name, Schema target) {
    if (target == null) {
      return false;
    }

    ObjectClassDefinition targetOc = target.getObjectClass(name);
    return targetOc != null;
  }

  /**
   * Checks if object class has different MUST/MAY attributes.
   *
   * @param source source object class definition
   * @param target target object class definition
   * @return true if MUST or MAY attributes differ
   */
  private boolean hasAttributeDifferences(ObjectClassDefinition source,
                                           ObjectClassDefinition target) {
    Set<String> sourceMust = new HashSet<>(
        Arrays.asList(source.getRequiredAttributes()));
    Set<String> targetMust = new HashSet<>(
        Arrays.asList(target.getRequiredAttributes()));

    Set<String> sourceMay = new HashSet<>(
        Arrays.asList(source.getOptionalAttributes()));
    Set<String> targetMay = new HashSet<>(
        Arrays.asList(target.getOptionalAttributes()));

    return !sourceMust.equals(targetMust) || !sourceMay.equals(targetMay);
  }

  /**
   * Removes X-READ-ONLY extensions from schema definition string.
   *
   * @param definition schema definition string
   * @return cleaned definition without X-READ-ONLY extensions
   */
  public String cleanSchemaDefinition(String definition) {
    if (definition == null) {
      return null;
    }

    // Remove X-READ-ONLY 'false' (redundant since false is default)
    String cleaned = definition.replaceAll("\\s*X-READ-ONLY\\s+'false'", "");
    // Remove X-READ-ONLY 'true'
    cleaned = cleaned.replaceAll("\\s*X-READ-ONLY\\s+'true'", "");
    // Normalize whitespace
    cleaned = cleaned.replaceAll("\\s+", " ").trim();

    return cleaned;
  }
}
