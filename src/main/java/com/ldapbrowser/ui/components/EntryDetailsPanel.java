package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Panel for displaying LDAP entry details.
 */
public class EntryDetailsPanel extends VerticalLayout {

  /**
   * Class to represent an attribute row in the grid.
   */
  public static class AttributeRow {
    private final String name;
    private final List<String> values;

    /**
     * Creates an attribute row.
     *
     * @param name attribute name
     * @param values attribute values
     */
    public AttributeRow(String name, List<String> values) {
      this.name = name;
      this.values = values;
    }

    public String getName() {
      return name;
    }

    public String getValues() {
      if (values == null || values.isEmpty()) {
        return "";
      }
      return String.join(", ", values);
    }

    public List<String> getValuesList() {
      return values;
    }
  }

  private final LdapService ldapService;

  private LdapServerConfig serverConfig;
  private LdapEntry currentEntry;

  // UI Components
  private HorizontalLayout headerLayout;
  private Span serverLabel;
  private Span dnLabel;
  private Button copyDnButton;
  private Button refreshButton;
  private Grid<AttributeRow> attributesGrid;

  /**
   * Creates a new entry details panel.
   *
   * @param ldapService the LDAP service
   */
  public EntryDetailsPanel(LdapService ldapService) {
    this.ldapService = ldapService;
    initializeComponents();
    setupLayout();
  }

  private void initializeComponents() {
    // Header
    headerLayout = new HorizontalLayout();
    headerLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    headerLayout.setPadding(true);
    headerLayout.addClassName("ds-panel-header");
    headerLayout.setWidthFull();

    Icon entryIcon = new Icon(VaadinIcon.FILE_TEXT_O);
    entryIcon.setSize("16px");
    entryIcon.getStyle().set("color", "#4a90e2");

    H3 title = new H3("Entry Details");
    title.addClassNames(LumoUtility.Margin.NONE);
    title.getStyle()
        .set("font-size", "0.9em")
        .set("font-weight", "600")
        .set("color", "#333");

    serverLabel = new Span();
    serverLabel.getStyle()
        .set("font-size", "0.8em")
        .set("color", "#666")
        .set("margin-left", "var(--lumo-space-m)");

    dnLabel = new Span();
    dnLabel.getStyle()
        .set("font-size", "0.8em")
        .set("color", "#333")
        .set("margin-left", "var(--lumo-space-s)");

    Icon copyIcon = new Icon(VaadinIcon.COPY);
    copyDnButton = new Button(copyIcon);
    copyDnButton.addThemeVariants(
        ButtonVariant.LUMO_ICON,
        ButtonVariant.LUMO_TERTIARY,
        ButtonVariant.LUMO_SMALL
    );
    copyDnButton.setTooltipText("Copy DN");
    copyDnButton.setVisible(false);

    Icon refreshIcon = new Icon(VaadinIcon.REFRESH);
    refreshButton = new Button(refreshIcon);
    refreshButton.addThemeVariants(
        ButtonVariant.LUMO_ICON,
        ButtonVariant.LUMO_TERTIARY,
        ButtonVariant.LUMO_SMALL
    );
    refreshButton.setTooltipText("Refresh");
    refreshButton.setVisible(false);

    headerLayout.add(entryIcon, title, serverLabel, dnLabel, copyDnButton, refreshButton);
    headerLayout.setFlexGrow(1, dnLabel);

    // Attributes grid
    attributesGrid = new Grid<>();
    attributesGrid.setSizeFull();
    attributesGrid.addColumn(AttributeRow::getName)
        .setHeader("Attribute")
        .setWidth("200px")
        .setFlexGrow(0);
    attributesGrid.addColumn(AttributeRow::getValues)
        .setHeader("Values")
        .setFlexGrow(1);

    // Event listeners
    copyDnButton.addClickListener(e -> copyDnToClipboard());
    refreshButton.addClickListener(e -> refreshEntry());
  }

  private void setupLayout() {
    setSizeFull();
    setPadding(false);
    setSpacing(false);
    addClassName("ds-panel");

    add(headerLayout, attributesGrid);
    setFlexGrow(1, attributesGrid);
  }

  /**
   * Sets the server configuration.
   *
   * @param serverConfig the server configuration
   */
  public void setServerConfig(LdapServerConfig serverConfig) {
    this.serverConfig = serverConfig;
  }

  /**
   * Displays entry details.
   *
   * @param entry the LDAP entry
   */
  public void showEntry(LdapEntry entry) {
    this.currentEntry = entry;

    if (entry == null) {
      clear();
      return;
    }

    // Update header
    serverLabel.setText(entry.getServerName() != null ? entry.getServerName() : "");
    dnLabel.setText(entry.getDn());
    copyDnButton.setVisible(true);
    refreshButton.setVisible(true);

    // Update attributes grid
    List<AttributeRow> rows = new ArrayList<>();
    for (Map.Entry<String, List<String>> attr : entry.getAttributes().entrySet()) {
      rows.add(new AttributeRow(attr.getKey(), attr.getValue()));
    }

    rows.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
    attributesGrid.setItems(rows);
  }

  /**
   * Clears the panel.
   */
  public void clear() {
    currentEntry = null;
    serverLabel.setText("");
    dnLabel.setText("No entry selected");
    copyDnButton.setVisible(false);
    refreshButton.setVisible(false);
    attributesGrid.setItems(new ArrayList<>());
  }

  private void copyDnToClipboard() {
    if (currentEntry != null) {
      String dn = currentEntry.getDn();
      getUI().ifPresent(ui -> {
        ui.getPage().executeJs(
            "navigator.clipboard.writeText($0).then(() => {"
            + "  console.log('DN copied to clipboard');"
            + "}, (err) => {"
            + "  console.error('Could not copy DN: ', err);"
            + "});",
            dn
        );
      });

      NotificationHelper.showInfo("DN copied to clipboard", 2000);
    }
  }

  private void refreshEntry() {
    if (currentEntry != null && serverConfig != null) {
      try {
        LdapEntry refreshedEntry = ldapService.readEntry(serverConfig,
            currentEntry.getDn(), false);
        showEntry(refreshedEntry);

        NotificationHelper.showSuccess("Entry refreshed", 2000);
      } catch (Exception e) {
        NotificationHelper.showError("Failed to refresh entry: " + e.getMessage(), 3000);
      }
    }
  }
}
