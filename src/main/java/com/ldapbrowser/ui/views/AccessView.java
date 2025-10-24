package com.ldapbrowser.ui.views;

import com.ldapbrowser.ui.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Access control view.
 * Manages LDAP access control and permissions.
 */
@Route(value = "access", layout = MainLayout.class)
@PageTitle("Access | LDAP Browser")
public class AccessView extends VerticalLayout {

  /**
   * Creates the Access view.
   */
  public AccessView() {
    setSpacing(true);
    setPadding(true);

    H2 title = new H2("Access Control");
    Paragraph description = new Paragraph(
        "This page will allow you to manage LDAP access control and permissions."
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
