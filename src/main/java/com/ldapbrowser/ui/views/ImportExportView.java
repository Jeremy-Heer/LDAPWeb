package com.ldapbrowser.ui.views;

import com.ldapbrowser.ui.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Import/Export view.
 * Handles importing and exporting LDAP data.
 */
@Route(value = "import-export", layout = MainLayout.class)
@PageTitle("Import/Export | LDAP Browser")
public class ImportExportView extends VerticalLayout {

  /**
   * Creates the Import/Export view.
   */
  public ImportExportView() {
    setSpacing(true);
    setPadding(true);

    H2 title = new H2("Import/Export");
    Paragraph description = new Paragraph(
        "This page will allow you to import and export LDAP data."
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
