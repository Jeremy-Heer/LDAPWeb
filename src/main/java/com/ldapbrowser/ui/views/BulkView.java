package com.ldapbrowser.ui.views;

import com.ldapbrowser.ui.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Bulk operations view.
 * Allows performing bulk LDAP operations.
 */
@Route(value = "bulk", layout = MainLayout.class)
@PageTitle("Bulk | LDAP Browser")
public class BulkView extends VerticalLayout {

  /**
   * Creates the Bulk view.
   */
  public BulkView() {
    setSpacing(true);
    setPadding(true);

    H2 title = new H2("Bulk Operations");
    Paragraph description = new Paragraph(
        "This page will allow you to perform bulk LDAP operations."
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
