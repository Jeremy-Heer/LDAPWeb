package com.ldapbrowser.ui.dialogs;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Help dialog providing documentation for using the application.
 * Displays in a popup with search functionality.
 * Content is loaded from help.md and rendered using Vaadin Markdown.
 */
public class HelpDialog extends Dialog {

  private final TextField searchField;
  private final VerticalLayout contentContainer;
  private final List<Div> allSections;

  /**
   * Creates the Help dialog with search functionality.
   */
  public HelpDialog() {
    setHeaderTitle("LDAP Browser Help");
    setModal(false);
    setDraggable(true);
    setResizable(true);
    setWidth("800px");
    setHeight("600px");

    allSections = new ArrayList<>();

    // Close button
    Button closeButton = new Button(VaadinIcon.CLOSE.create(), e -> close());
    closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    getHeader().add(closeButton);

    // Create main layout
    VerticalLayout mainLayout = new VerticalLayout();
    mainLayout.setSizeFull();
    mainLayout.setPadding(false);
    mainLayout.setSpacing(false);

    // Search field
    searchField = new TextField();
    searchField.setPlaceholder("Search help content...");
    searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
    searchField.setWidthFull();
    searchField.setClearButtonVisible(true);
    searchField.setValueChangeMode(ValueChangeMode.EAGER);
    searchField.addValueChangeListener(e -> filterContent(e.getValue()));
    searchField.getStyle()
        .set("padding", "var(--lumo-space-m)")
        .set("margin-bottom", "var(--lumo-space-m)");

    // Container for scrollable content
    contentContainer = new VerticalLayout();
    contentContainer.setPadding(false);
    contentContainer.setSpacing(true);
    contentContainer.getStyle()
        .set("overflow-y", "auto")
        .set("padding", "0 var(--lumo-space-m) var(--lumo-space-m) var(--lumo-space-m)");

    // Load and render markdown content
    loadHelpContent();

    mainLayout.add(searchField, contentContainer);
    mainLayout.expand(contentContainer);

    add(mainLayout);
  }

  /**
   * Load help content from markdown file.
   */
  private void loadHelpContent() {
    try (InputStream is = getClass().getResourceAsStream("/help.md")) {
      if (is == null) {
        return;
      }
      String markdownContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      displayMarkdownContent(markdownContent);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Display markdown content as Markdown components.
   */
  private void displayMarkdownContent(String markdownContent) {
    allSections.clear();

    // Split by level 2 headers (## )
    String[] sections = markdownContent.split("(?=## )");

    for (String section : sections) {
      section = section.trim();
      if (!section.isEmpty()) {
        Div sectionDiv = new Div();
        sectionDiv.addClassName("help-section");

        Markdown markdown = new Markdown(section);
        markdown.setWidthFull();

        sectionDiv.add(markdown);
        allSections.add(sectionDiv);
        contentContainer.add(sectionDiv);
      }
    }
  }

  /**
   * Filters the help content based on search query.
   *
   * @param query search query
   */
  private void filterContent(String query) {
    if (query == null || query.trim().isEmpty()) {
      // Show all sections
      allSections.forEach(section -> section.setVisible(true));
      return;
    }

    String lowerQuery = query.toLowerCase().trim();
    
    for (Div section : allSections) {
      String sectionText = getSectionText(section).toLowerCase();
      section.setVisible(sectionText.contains(lowerQuery));
    }
  }

  /**
   * Extracts text content from a section for search filtering.
   *
   * @param section the section div
   * @return concatenated text content
   */
  private String getSectionText(Div section) {
    StringBuilder text = new StringBuilder();
    section.getChildren().forEach(component -> {
      text.append(component.getElement().getText()).append(" ");
    });
    return text.toString();
  }
}
