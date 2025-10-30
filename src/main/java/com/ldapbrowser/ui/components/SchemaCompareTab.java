package com.ldapbrowser.ui.components;

import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Schema compare tab component placeholder.
 * Future implementation will allow comparing schemas across servers.
 */
public class SchemaCompareTab extends VerticalLayout {

  /**
   * Creates the schema compare tab.
   */
  public SchemaCompareTab() {
    setSpacing(true);
    setPadding(true);
    setJustifyContentMode(JustifyContentMode.CENTER);
    setAlignItems(Alignment.CENTER);
    setSizeFull();

    H3 title = new H3("Schema Comparison");
    
    Paragraph status = new Paragraph("🚧 Under Construction 🚧");
    status.getStyle()
        .set("font-size", "var(--lumo-font-size-xl)")
        .set("font-weight", "bold")
        .set("color", "var(--lumo-warning-color)")
        .set("text-align", "center")
        .set("padding", "var(--lumo-space-xl)");

    Paragraph description = new Paragraph(
        "This feature will allow you to compare LDAP schemas across multiple servers."
    );
    description.getStyle().set("text-align", "center");

    add(title, status, description);
  }
}
