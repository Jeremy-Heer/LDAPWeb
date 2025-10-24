package com.ldapbrowser.ui.views;

import com.ldapbrowser.ui.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Browse view for navigating LDAP directory tree.
 * Displays tree navigator and entry details.
 */
@Route(value = "browse", layout = MainLayout.class)
@PageTitle("Browse | LDAP Browser")
public class BrowseView extends VerticalLayout {

  /**
   * Creates the Browse view.
   */
  public BrowseView() {
    setSpacing(true);
    setPadding(true);

    H2 title = new H2("Browse Directory");
    Paragraph description = new Paragraph(
        "This page will display the LDAP directory tree and allow you to browse entries."
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
