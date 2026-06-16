package com.ldapbrowser.util;

import com.ldapbrowser.model.LdapEntry;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared formatter for LDAP export output.
 */
public final class LdapExportFormatter {

  private LdapExportFormatter() {
  }

  /**
   * Parses a comma-separated return attribute string.
   *
   * @param returnAttributes raw attribute input
   * @return trimmed, non-empty attributes preserving input order
   */
  public static List<String> parseReturnAttributes(String returnAttributes) {
    if (returnAttributes == null || returnAttributes.trim().isEmpty()) {
      return new ArrayList<>();
    }

    List<String> attrList = new ArrayList<>();
    for (String attr : returnAttributes.split(",")) {
      String trimmed = attr.trim();
      if (!trimmed.isEmpty()) {
        attrList.add(trimmed);
      }
    }
    return attrList;
  }

  /**
   * Generates export data for the requested format.
   *
   * @param entries LDAP entries to export
   * @param format output format
   * @param requestedAttrs requested attributes or empty for all
   * @param includeHeader whether CSV should include a header row
   * @param includeDn whether CSV should include the DN column
   * @param surroundValues whether CSV values should be quoted
   * @return formatted export data
   */
  public static String generateExportData(List<LdapEntry> entries,
      String format,
      List<String> requestedAttrs,
      boolean includeHeader,
      boolean includeDn,
      boolean surroundValues) {
    String normalizedFormat = format == null ? "CSV" : format.toUpperCase();
    return switch (normalizedFormat) {
      case "CSV" -> generateCsvData(entries, requestedAttrs,
          includeHeader, includeDn, surroundValues);
      case "JSON" -> generateJsonData(entries, requestedAttrs);
      case "LDIF" -> generateLdifData(entries, requestedAttrs);
      case "DN LIST" -> generateDnListData(entries);
      default -> generateCsvData(entries, requestedAttrs,
          includeHeader, includeDn, surroundValues);
    };
  }

  /**
   * Generates a download filename for the requested format.
   *
   * @param format output format
   * @return timestamped filename
   */
  public static String generateFileName(String format) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    String timestamp = LocalDateTime.now().format(formatter);
    String extension = switch ((format == null ? "CSV" : format.toUpperCase())) {
      case "JSON" -> "json";
      case "LDIF" -> "ldif";
      case "DN LIST" -> "txt";
      default -> "csv";
    };
    return "ldap_export_" + timestamp + "." + extension;
  }

  /**
   * Returns the MIME type for the requested format.
   *
   * @param format output format
   * @return MIME type
   */
  public static String getMimeType(String format) {
    return switch ((format == null ? "CSV" : format.toUpperCase())) {
      case "JSON" -> "application/json";
      case "LDIF" -> "text/plain";
      case "DN LIST" -> "text/plain";
      default -> "text/csv";
    };
  }

  private static String generateCsvData(List<LdapEntry> entries,
      List<String> requestedAttrs,
      boolean includeHeader,
      boolean includeDn,
      boolean surroundValues) {
    StringBuilder sb = new StringBuilder();

    if (entries.isEmpty()) {
      return sb.toString();
    }

    Set<String> attributesToExport = new LinkedHashSet<>();
    if (includeDn) {
      attributesToExport.add("dn");
    }

    if (requestedAttrs.isEmpty()) {
      for (LdapEntry entry : entries) {
        attributesToExport.addAll(entry.getAttributes().keySet());
      }
    } else {
      attributesToExport.addAll(requestedAttrs);
    }

    if (includeHeader) {
      sb.append(String.join(",", attributesToExport)).append("\n");
    }

    for (LdapEntry entry : entries) {
      List<String> values = new ArrayList<>();
      for (String attrName : attributesToExport) {
        if ("dn".equals(attrName)) {
          values.add(formatCsvValue(entry.getDn(), surroundValues));
          continue;
        }

        List<String> attrValues = entry.getAttributes().get(attrName);
        if (attrValues != null && !attrValues.isEmpty()) {
          values.add(formatCsvValue(String.join("; ", attrValues),
              surroundValues));
        } else if (surroundValues) {
          values.add("\"\"");
        } else {
          values.add("");
        }
      }
      sb.append(String.join(",", values)).append("\n");
    }

    return sb.toString();
  }

  private static String generateJsonData(List<LdapEntry> entries,
      List<String> requestedAttrs) {
    StringBuilder sb = new StringBuilder();
    sb.append("[\n");

    for (int i = 0; i < entries.size(); i++) {
      LdapEntry entry = entries.get(i);
      sb.append("  {\n");
      sb.append("    \"dn\": \"").append(escapeJson(entry.getDn()))
          .append("\"");

      if (requestedAttrs.isEmpty()) {
        for (var attr : entry.getAttributes().entrySet()) {
          appendJsonAttribute(sb, attr.getKey(), attr.getValue());
        }
      } else {
        for (String attrName : requestedAttrs) {
          List<String> values = entry.getAttributes().get(attrName);
          if (values != null && !values.isEmpty()) {
            appendJsonAttribute(sb, attrName, values);
          }
        }
      }

      sb.append("\n  }");
      if (i < entries.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }

    sb.append("]\n");
    return sb.toString();
  }

  private static void appendJsonAttribute(StringBuilder sb,
      String attributeName,
      List<String> values) {
    sb.append(",\n    \"").append(escapeJson(attributeName))
        .append("\": ");
    if (values.size() == 1) {
      sb.append("\"").append(escapeJson(values.get(0))).append("\"");
      return;
    }

    sb.append("[");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append("\"").append(escapeJson(values.get(i))).append("\"");
    }
    sb.append("]");
  }

  private static String generateLdifData(List<LdapEntry> entries,
      List<String> requestedAttrs) {
    StringBuilder sb = new StringBuilder();

    for (LdapEntry entry : entries) {
      sb.append("dn: ").append(entry.getDn()).append("\n");

      if (requestedAttrs.isEmpty()) {
        for (var attr : entry.getAttributes().entrySet()) {
          for (String value : attr.getValue()) {
            sb.append(attr.getKey()).append(": ").append(value)
                .append("\n");
          }
        }
      } else {
        for (String attrName : requestedAttrs) {
          List<String> values = entry.getAttributes().get(attrName);
          if (values != null) {
            for (String value : values) {
              sb.append(attrName).append(": ").append(value)
                  .append("\n");
            }
          }
        }
      }

      sb.append("\n");
    }

    return sb.toString();
  }

  private static String generateDnListData(List<LdapEntry> entries) {
    StringBuilder sb = new StringBuilder();
    for (LdapEntry entry : entries) {
      sb.append(entry.getDn()).append("\n");
    }
    return sb.toString();
  }

  private static String formatCsvValue(String value, boolean surroundValues) {
    if (surroundValues) {
      return "\"" + escapeQuotes(value) + "\"";
    }
    return value;
  }

  private static String escapeQuotes(String value) {
    return value.replace("\"", "\"\"");
  }

  private static String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}