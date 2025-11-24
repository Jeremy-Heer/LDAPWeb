package com.ldapbrowser.ui.utils;

import com.unboundid.ldap.sdk.schema.AttributeTypeDefinition;
import com.unboundid.ldap.sdk.schema.ObjectClassDefinition;
import com.unboundid.ldap.sdk.schema.Schema;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for displaying schema element details in modal dialogs.
 * Provides centralized dialog creation for attribute types and object classes.
 */
public final class SchemaDetailDialogHelper {

  private SchemaDetailDialogHelper() {
    // Private constructor to prevent instantiation
  }

  /**
   * Shows a modal dialog with attribute type details.
   *
   * @param attrType the attribute type definition
   * @param serverName the server name for display
   */
  public static void showAttributeTypeDialog(AttributeTypeDefinition attrType, String serverName) {
    showAttributeTypeDialog(attrType, serverName, null);
  }

  /**
   * Shows a modal dialog with attribute type details with usage information.
   *
   * @param attrType the attribute type definition
   * @param serverName the server name for display
   * @param schema the schema for finding usage information (optional)
   */
  public static void showAttributeTypeDialog(AttributeTypeDefinition attrType, String serverName,
      Schema schema) {
    Dialog dialog = new Dialog();
    dialog.setWidth("800px");
    dialog.setHeaderTitle("Attribute Type: " + attrType.getNameOrOID());

    VerticalLayout details = new VerticalLayout();
    details.setSpacing(true);
    details.setPadding(true);

    // Add detail rows
    addDetailRow(details, "Server", serverName);
    addDetailRow(details, "OID", attrType.getOID());
    addDetailRow(details, "Names", 
        attrType.getNames() != null ? String.join(", ", Arrays.asList(attrType.getNames())) : "");
    addDetailRow(details, "Description", attrType.getDescription());
    
    String atSchemaFile = getSchemaFileFromExtensions(attrType.getExtensions());
    addDetailRow(details, "Schema File", atSchemaFile);
    
    addDetailRow(details, "Syntax OID", attrType.getSyntaxOID());
    addDetailRow(details, "Obsolete", attrType.isObsolete() ? "Yes" : "No");
    addDetailRow(details, "Single Value", attrType.isSingleValued() ? "Yes" : "No");
    addDetailRow(details, "Collective", attrType.isCollective() ? "Yes" : "No");
    addDetailRow(details, "No User Modification", 
        attrType.isNoUserModification() ? "Yes" : "No");
    
    if (attrType.getUsage() != null) {
      addDetailRow(details, "Usage", attrType.getUsage().getName());
    }
    
    if (attrType.getSuperiorType() != null) {
      addDetailRow(details, "Superior Type", attrType.getSuperiorType());
    }
    
    if (attrType.getEqualityMatchingRule() != null) {
      addDetailRow(details, "Equality Matching Rule", attrType.getEqualityMatchingRule());
    }
    
    if (attrType.getOrderingMatchingRule() != null) {
      addDetailRow(details, "Ordering Matching Rule", attrType.getOrderingMatchingRule());
    }
    
    if (attrType.getSubstringMatchingRule() != null) {
      addDetailRow(details, "Substring Matching Rule", attrType.getSubstringMatchingRule());
    }
    
    // Extensions
    if (attrType.getExtensions() != null && !attrType.getExtensions().isEmpty()) {
      StringBuilder extensions = new StringBuilder();
      for (Map.Entry<String, String[]> entry : attrType.getExtensions().entrySet()) {
        if (extensions.length() > 0) {
          extensions.append(", ");
        }
        extensions.append(entry.getKey()).append("=").append(String.join(",", entry.getValue()));
      }
      addDetailRow(details, "Extensions", extensions.toString());
    }

    // Add "Used as May" and "Used as Must" sections if schema provided
    if (schema != null) {
      addAttributeUsageToDialog(details, attrType, schema, serverName);
    }

    // Add raw schema definition at the bottom
    addRawDefinition(details, attrType);

    dialog.add(details);

    Button closeButton = new Button("Close", e -> dialog.close());
    closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    dialog.getFooter().add(closeButton);

    dialog.open();
  }

  /**
   * Shows a modal dialog with object class details.
   *
   * @param objClass the object class definition
   * @param serverName the server name for display
   */
  public static void showObjectClassDialog(ObjectClassDefinition objClass, String serverName) {
    showObjectClassDialog(objClass, serverName, null);
  }

  /**
   * Shows a modal dialog with object class details with clickable attributes.
   *
   * @param objClass the object class definition
   * @param serverName the server name for display
   * @param schema the schema for finding attribute definitions (optional)
   */
  public static void showObjectClassDialog(ObjectClassDefinition objClass, String serverName,
      Schema schema) {
    Dialog dialog = new Dialog();
    dialog.setWidth("800px");
    dialog.setHeaderTitle("Object Class: " + objClass.getNameOrOID());

    VerticalLayout details = new VerticalLayout();
    details.setSpacing(true);
    details.setPadding(true);

    // Add detail rows
    addDetailRow(details, "Server", serverName);
    addDetailRow(details, "OID", objClass.getOID());
    addDetailRow(details, "Names",
        objClass.getNames() != null ? String.join(", ", Arrays.asList(objClass.getNames())) : "");
    addDetailRow(details, "Description", objClass.getDescription());

    String ocSchemaFile = getSchemaFileFromExtensions(objClass.getExtensions());
    addDetailRow(details, "Schema File", ocSchemaFile);

    addDetailRow(details, "Type",
        objClass.getObjectClassType() != null ? objClass.getObjectClassType().getName() : "");
    addDetailRow(details, "Obsolete", objClass.isObsolete() ? "Yes" : "No");

    if (objClass.getSuperiorClasses() != null && objClass.getSuperiorClasses().length > 0) {
      addDetailRow(details, "Superior Classes",
          String.join(", ", objClass.getSuperiorClasses()));
    }

    // Required Attributes - clickable if schema provided
    if (objClass.getRequiredAttributes() != null && objClass.getRequiredAttributes().length > 0) {
      if (schema != null) {
        addClickableAttributeList(details, "Required Attributes",
            objClass.getRequiredAttributes(), schema, serverName);
      } else {
        addDetailRow(details, "Required Attributes",
            String.join(", ", objClass.getRequiredAttributes()));
      }
    }

    // Optional Attributes - clickable if schema provided
    if (objClass.getOptionalAttributes() != null && objClass.getOptionalAttributes().length > 0) {
      if (schema != null) {
        addClickableAttributeList(details, "Optional Attributes",
            objClass.getOptionalAttributes(), schema, serverName);
      } else {
        addDetailRow(details, "Optional Attributes",
            String.join(", ", objClass.getOptionalAttributes()));
      }
    }

    if (objClass.getExtensions() != null && !objClass.getExtensions().isEmpty()) {
      StringBuilder extensions = new StringBuilder();
      for (Map.Entry<String, String[]> entry : objClass.getExtensions().entrySet()) {
        if (extensions.length() > 0) {
          extensions.append(", ");
        }
        extensions.append(entry.getKey()).append("=").append(String.join(",", entry.getValue()));
      }
      addDetailRow(details, "Extensions", extensions.toString());
    }

    // Add raw schema definition at the bottom
    addRawDefinition(details, objClass);

    dialog.add(details);

    Button closeButton = new Button("Close", e -> dialog.close());
    closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    dialog.getFooter().add(closeButton);

    dialog.open();
  }

  /**
   * Adds a detail row with label and value to the layout.
   *
   * @param parent the parent layout
   * @param label the label text
   * @param value the value text
   */
  public static void addDetailRow(VerticalLayout parent, String label, String value) {
    if (value != null && !value.trim().isEmpty()) {
      HorizontalLayout row = new HorizontalLayout();
      row.setWidthFull();
      row.setDefaultVerticalComponentAlignment(Alignment.START);
      row.setSpacing(true);

      Span labelSpan = new Span(label + ":");
      labelSpan.getStyle().set("font-weight", "bold").set("min-width", "150px");

      Span valueSpan = new Span(value);
      valueSpan.getStyle().set("word-wrap", "break-word");

      row.add(labelSpan, valueSpan);
      row.setFlexGrow(1, valueSpan);

      parent.add(row);
    }
  }

  /**
   * Adds a raw definition section with copy button to the layout.
   *
   * @param details the details layout
   * @param schemaElement the schema element (AttributeTypeDefinition or ObjectClassDefinition)
   */
  public static void addRawDefinition(VerticalLayout details, Object schemaElement) {
    String raw = getRawDefinitionString(schemaElement);
    if (raw != null && !raw.trim().isEmpty()) {
      // Create header with copy button
      HorizontalLayout rawHeader = new HorizontalLayout();
      rawHeader.setWidthFull();
      rawHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);
      rawHeader.getStyle().set("margin-top", "8px");
      
      Span rawLabel = new Span("Raw Definition");
      rawLabel.getStyle()
          .set("font-weight", "500")
          .set("font-size", "var(--lumo-font-size-s)");
      
      Button copyButton = new Button(new Icon(VaadinIcon.COPY));
      copyButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
      copyButton.setTooltipText("Copy to clipboard");
      copyButton.addClickListener(e -> {
        copyButton.getElement().executeJs(
            "navigator.clipboard.writeText($0).then(() => {" +
            "  const notification = document.createElement('vaadin-notification');" +
            "  notification.renderer = function(root) {" +
            "    root.textContent = 'Copied to clipboard';" +
            "  };" +
            "  notification.position = 'bottom-start';" +
            "  notification.duration = 2000;" +
            "  notification.open();" +
            "});",
            raw
        );
      });
      
      rawHeader.add(rawLabel, copyButton);
      rawHeader.setFlexGrow(1, rawLabel);
      
      TextArea rawArea = new TextArea();
      rawArea.setValue(raw);
      rawArea.setReadOnly(true);
      rawArea.setWidthFull();
      rawArea.setHeight("200px");
      // Use monospace and preserve whitespace
      rawArea.getStyle().set("font-family", "monospace");
      rawArea.getElement().getStyle().set("white-space", "pre");
      
      details.add(rawHeader, rawArea);
    }
  }

  /**
   * Gets the raw definition string from a schema element.
   *
   * @param schemaElement the schema element
   * @return the raw definition string
   */
  private static String getRawDefinitionString(Object schemaElement) {
    // Try common methods for getting the raw definition
    String[] candidateMethods = new String[] {
        "toString", "getDefinitionString", "getDefinitionOrString",
        "getOriginalString"
    };

    for (String methodName : candidateMethods) {
      try {
        java.lang.reflect.Method m = schemaElement.getClass().getMethod(methodName);
        Object res = m.invoke(schemaElement);
        if (res instanceof String) {
          return (String) res;
        }
      } catch (NoSuchMethodException ns) {
        // ignore, try next
      } catch (Exception ignored) {
        // ignore any invocation issues and try next
      }
    }

    // Fallback to toString() which should at least provide a usable representation
    try {
      return schemaElement.toString();
    } catch (Exception e) {
      return "";
    }
  }

  /**
   * Extracts the schema file name from extensions map.
   *
   * @param extensions the extensions map
   * @return the schema file name or empty string
   */
  private static String getSchemaFileFromExtensions(Map<String, String[]> extensions) {
    if (extensions != null && extensions.containsKey("X-ORIGIN")) {
      String[] origins = extensions.get("X-ORIGIN");
      if (origins != null && origins.length > 0) {
        return origins[0];
      }
    }
    return "";
  }

  /**
   * Add "Used as May" and "Used as Must" sections to attribute type dialog.
   *
   * @param details the layout to add sections to
   * @param at the attribute type definition
   * @param schema the schema to search
   * @param serverName the server name
   */
  private static void addAttributeUsageToDialog(VerticalLayout details, AttributeTypeDefinition at,
      Schema schema, String serverName) {
    // Find all attribute names for this attribute type
    Set<String> attrNames = new HashSet<>();
    if (at.getNames() != null && at.getNames().length > 0) {
      attrNames.addAll(Arrays.asList(at.getNames()));
    }
    attrNames.add(at.getOID());

    // Find object classes that use this attribute
    List<ObjectClassDefinition> usedAsMay = new ArrayList<>();
    List<ObjectClassDefinition> usedAsMust = new ArrayList<>();

    for (ObjectClassDefinition oc : schema.getObjectClasses()) {
      // Check MAY list
      if (oc.getOptionalAttributes() != null) {
        for (String mayAttr : oc.getOptionalAttributes()) {
          if (attrNames.contains(mayAttr)) {
            usedAsMay.add(oc);
            break;
          }
        }
      }

      // Check MUST list
      if (oc.getRequiredAttributes() != null) {
        for (String mustAttr : oc.getRequiredAttributes()) {
          if (attrNames.contains(mustAttr)) {
            usedAsMust.add(oc);
            break;
          }
        }
      }
    }

    // Add "Used as May" section
    if (!usedAsMay.isEmpty()) {
      addClickableObjectClassListToDialog(details, "Used as May", usedAsMay, serverName, schema);
    }

    // Add "Used as Must" section
    if (!usedAsMust.isEmpty()) {
      addClickableObjectClassListToDialog(details, "Used as Must", usedAsMust, serverName, schema);
    }
  }

  /**
   * Add a clickable list of object classes to the details panel.
   *
   * @param parent the parent layout
   * @param label the section label
   * @param objectClasses the list of object classes
   * @param serverName the server name
   * @param schema the schema for nested lookups
   */
  private static void addClickableObjectClassListToDialog(VerticalLayout parent, String label,
      List<ObjectClassDefinition> objectClasses, String serverName, Schema schema) {
    HorizontalLayout row = new HorizontalLayout();
    row.setWidthFull();
    row.setDefaultVerticalComponentAlignment(Alignment.START);
    row.setSpacing(true);

    Span labelSpan = new Span(label + ":");
    labelSpan.getStyle().set("font-weight", "bold").set("min-width", "150px");

    HorizontalLayout linksLayout = new HorizontalLayout();
    linksLayout.setSpacing(false);
    linksLayout.getStyle().set("flex-wrap", "wrap");

    for (int i = 0; i < objectClasses.size(); i++) {
      ObjectClassDefinition oc = objectClasses.get(i);
      
      Button linkButton = new Button(oc.getNameOrOID());
      linkButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
      linkButton.getStyle().set("padding", "0").set("min-height", "auto");
      linkButton.addClickListener(e -> 
          showObjectClassDialog(oc, serverName, schema));
      
      linksLayout.add(linkButton);
      
      if (i < objectClasses.size() - 1) {
        Span comma = new Span(", ");
        comma.getStyle().set("padding", "0 2px");
        linksLayout.add(comma);
      }
    }

    row.add(labelSpan, linksLayout);
    row.setFlexGrow(1, linksLayout);

    parent.add(row);
  }

  /**
   * Add a clickable list of attributes to the details panel.
   *
   * @param parent the parent layout
   * @param label the section label
   * @param attributeNames the attribute names array
   * @param schema the schema for looking up attribute definitions
   * @param serverName the server name
   */
  private static void addClickableAttributeList(VerticalLayout parent, String label,
      String[] attributeNames, Schema schema, String serverName) {
    HorizontalLayout row = new HorizontalLayout();
    row.setWidthFull();
    row.setDefaultVerticalComponentAlignment(Alignment.START);
    row.setSpacing(true);

    Span labelSpan = new Span(label + ":");
    labelSpan.getStyle().set("font-weight", "bold").set("min-width", "150px");

    HorizontalLayout linksLayout = new HorizontalLayout();
    linksLayout.setSpacing(false);
    linksLayout.getStyle().set("flex-wrap", "wrap");

    for (int i = 0; i < attributeNames.length; i++) {
      String attrName = attributeNames[i];
      AttributeTypeDefinition attrType = schema.getAttributeType(attrName);
      
      Button linkButton = new Button(attrName);
      linkButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
      linkButton.getStyle().set("padding", "0").set("min-height", "auto");
      
      if (attrType != null) {
        linkButton.addClickListener(e -> 
            showAttributeTypeDialog(attrType, serverName, schema));
      } else {
        // If attribute type not found, disable the button
        linkButton.setEnabled(false);
      }
      
      linksLayout.add(linkButton);
      
      if (i < attributeNames.length - 1) {
        Span comma = new Span(", ");
        comma.getStyle().set("padding", "0 2px");
        linksLayout.add(comma);
      }
    }

    row.add(labelSpan, linksLayout);
    row.setFlexGrow(1, linksLayout);

    parent.add(row);
  }
}
