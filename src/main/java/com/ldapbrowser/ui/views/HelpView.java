package com.ldapbrowser.ui.views;

import com.ldapbrowser.ui.MainLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Help view providing documentation for using the application.
 * Content is loaded from help.md and rendered using Vaadin Markdown.
 */
@Route(value = "help", layout = MainLayout.class)
@PageTitle("Help | LDAP Browser")
public class HelpView extends VerticalLayout {

  /**
   * Creates the Help view with documentation loaded from markdown.
   */
  public HelpView() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);

    // Container for scrollable content
    Div contentContainer = new Div();
    contentContainer.getStyle()
        .set("overflow-y", "auto")
        .set("padding-right", "var(--lumo-space-m)");

    // Load and render markdown content
    loadHelpContent(contentContainer);

    add(contentContainer);
    expand(contentContainer);
  }

  /**
   * Loads the help content from the markdown file and displays it.
   *
   * @param container the container to add content to
   */
  /**
   * Load help content from markdown file.
   */
  private void loadHelpContent(Div container) {
    try (InputStream is = getClass().getResourceAsStream("/help.md")) {
      if (is == null) {
        Div errorDiv = new Div();
        errorDiv.setText("Help content not found.");
        errorDiv.getStyle()
            .set("color", "var(--lumo-error-text-color)")
            .set("padding", "var(--lumo-space-l)")
            .set("text-align", "center");
        container.add(errorDiv);
        return;
      }

      String markdownContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      displayMarkdownContent(container, markdownContent);

    } catch (IOException e) {
      Div errorDiv = new Div();
      errorDiv.setText("Error loading help content: " + e.getMessage());
      errorDiv.getStyle()
          .set("color", "var(--lumo-error-text-color)")
          .set("padding", "var(--lumo-space-l)")
          .set("text-align", "center");
      container.add(errorDiv);
      e.printStackTrace();
    }
  }

  /**
   * Display markdown content using Vaadin Markdown component.
   */
  private void displayMarkdownContent(Div container, String markdownContent) {
    Markdown markdown = new Markdown(markdownContent);
    markdown.setWidthFull();
    container.add(markdown);
  }
}
