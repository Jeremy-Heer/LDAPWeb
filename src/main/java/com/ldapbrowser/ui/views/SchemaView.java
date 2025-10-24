package com.ldapbrowser.ui.views;

import com.ldapbrowser.ui.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Schema view for exploring LDAP schema.
 * Displays object classes and attribute types.
 */
@Route(value = "schema", layout = MainLayout.class)
@PageTitle("Schema | LDAP Browser")
public class SchemaView extends VerticalLayout {

  /**
   * Creates the Schema view.
   */
  public SchemaView() {
    setSpacing(true);
    setPadding(true);

    H2 title = new H2("LDAP Schema");
    Paragraph description = new Paragraph(
        "This page will display LDAP schema information including object classes and attributes."
    );

    Paragraph status = new Paragraph("🚧 Under Construction 🚧");
    status.getStyle()
        .set("font-size", "var(--lumo-font-size-xl)")
        .set("font-weight", "bold")
        .set("color", "var(--lumo-warning-color)")
        .set("text-align", "center")
        .set("padding", "var(--lumo-space-xl)");

    add(title, description, status);
  }
}
