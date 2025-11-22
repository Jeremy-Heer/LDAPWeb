package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchScope;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global Access Control tab component for managing LDAP global ACIs.
 * Displays global access control instructions from multiple LDAP servers.
 */
public class GlobalAccessControlTab extends VerticalLayout {

  private static final Logger logger = LoggerFactory.getLogger(GlobalAccessControlTab.class);
  private static final String ACCESS_CONTROL_HANDLER_DN = "cn=Access Control Handler,cn=config";
  private static final String GLOBAL_ACI_ATTRIBUTE = "ds-cfg-global-aci";

  private final LdapService ldapService;
  private Grid<GlobalAciInfo> aciGrid;
  private TextField searchField;
  private VerticalLayout aciDetailsPanel;
  private Div loadingContainer;
  private List<GlobalAciInfo> allAciInfo;
  private Set<LdapServerConfig> selectedServers;

  /**
   * Creates the Global Access Control tab.
   *
   * @param ldapService LDAP service for retrieving ACI data
   */
  public GlobalAccessControlTab(LdapService ldapService) {
    this.ldapService = ldapService;
    this.allAciInfo = new ArrayList<>();
    this.selectedServers = Collections.emptySet();

    setSizeFull();
    setPadding(false);
    setSpacing(false);

    initializeComponents();
  }

  /**
   * Initializes the UI components.
   */
  private void initializeComponents() {
    // Loading indicator
    loadingContainer = new Div();
    loadingContainer.setText("Loading access control information...");
    loadingContainer.getStyle().set("text-align", "center");
    loadingContainer.getStyle().set("padding", "20px");
    loadingContainer.setVisible(false);
    add(loadingContainer);

    // Main split layout - two vertical panes
    SplitLayout mainLayout = new SplitLayout();
    mainLayout.setSizeFull();
    mainLayout.setOrientation(SplitLayout.Orientation.HORIZONTAL);
    mainLayout.setSplitterPosition(60); // 60% for left pane, 40% for right pane

    // LEFT PANE: Control panel with title, buttons, description, search, and grid
    VerticalLayout leftPane = new VerticalLayout();
    leftPane.setSizeFull();
    leftPane.setPadding(true);
    leftPane.setSpacing(true);

    // Header with title and refresh button
    HorizontalLayout header = new HorizontalLayout();
    header.setWidthFull();
    header.setJustifyContentMode(JustifyContentMode.BETWEEN);
    header.setAlignItems(Alignment.CENTER);
    
    H3 title = new H3("Global Access Control");
    title.getStyle().set("margin", "0");
    
    Button refreshButton = new Button("Refresh", VaadinIcon.REFRESH.create());
    refreshButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
    refreshButton.addClickListener(event -> refreshData());
    
    header.add(title, refreshButton);
    leftPane.add(header);

    // Description
    Div description = new Div();
    description.setText("Global Access Control Instructions (ACIs) from Access Control Handler.");
    description.getStyle().set("color", "var(--lumo-secondary-text-color)")
        .set("margin-bottom", "var(--lumo-space-m)");
    leftPane.add(description);

    // Search field for filtering ACI rules
    searchField = new TextField();
    searchField.setPlaceholder("Search global ACIs by name or content...");
    searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
    searchField.setWidthFull();
    searchField.getStyle().set("margin-bottom", "var(--lumo-space-m)");
    searchField.addValueChangeListener(event -> filterAciGrid());
    leftPane.add(searchField);

    // Grid for displaying ACI entries
    aciGrid = new Grid<>(GlobalAciInfo.class, false);
    aciGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
    aciGrid.setAllRowsVisible(false);
    aciGrid.setPageSize(50);
    aciGrid.getStyle()
        .set("border", "1px solid var(--lumo-contrast-10pct)")
        .set("border-radius", "var(--lumo-border-radius-m)");

    // Configure grid columns
    aciGrid.addColumn(GlobalAciInfo::getServerName)
        .setHeader("Server")
        .setAutoWidth(true)
        .setFlexGrow(0)
        .setSortable(true);

    aciGrid.addColumn(GlobalAciInfo::getName)
        .setHeader("Name")
        .setAutoWidth(true)
        .setFlexGrow(1)
        .setSortable(true);

    aciGrid.addColumn(GlobalAciInfo::getResources)
        .setHeader("Resources")
        .setAutoWidth(true)
        .setFlexGrow(1)
        .setSortable(true);

    aciGrid.addColumn(GlobalAciInfo::getRights)
        .setHeader("Rights")
        .setAutoWidth(true)
        .setFlexGrow(0)
        .setSortable(true);

    aciGrid.addColumn(GlobalAciInfo::getClients)
        .setHeader("Clients")
        .setAutoWidth(true)
        .setFlexGrow(1)
        .setSortable(true);

    aciGrid.addSelectionListener(event -> {
      event.getFirstSelectedItem().ifPresent(this::displayAciDetails);
    });

    leftPane.add(aciGrid);
    leftPane.setFlexGrow(1, aciGrid);

    // RIGHT PANE: Details panel
    aciDetailsPanel = new VerticalLayout();
    aciDetailsPanel.setSizeFull();
    aciDetailsPanel.setPadding(true);
    aciDetailsPanel.setSpacing(true);
    aciDetailsPanel.getStyle()
        .set("border-left", "1px solid var(--lumo-contrast-10pct)");

    // Placeholder text when nothing is selected
    Div placeholder = new Div();
    placeholder.setText("Select an ACI entry to view details");
    placeholder.getStyle()
        .set("color", "var(--lumo-secondary-text-color)")
        .set("text-align", "center")
        .set("padding", "var(--lumo-space-xl)");
    aciDetailsPanel.add(placeholder);

    mainLayout.addToPrimary(leftPane);
    mainLayout.addToSecondary(aciDetailsPanel);

    add(mainLayout);
    setFlexGrow(1, mainLayout);
  }

  /**
   * Sets the selected servers and refreshes the data.
   *
   * @param servers set of selected LDAP servers
   */
  public void setSelectedServers(Set<LdapServerConfig> servers) {
    this.selectedServers = servers;
    refreshData();
  }

  /**
   * Refreshes the ACI data from all selected servers.
   */
  public void refreshData() {
    if (selectedServers == null || selectedServers.isEmpty()) {
      NotificationHelper.showInfo("No servers selected. Please select at least one server from the navigation bar.");
      allAciInfo.clear();
      aciGrid.setItems(allAciInfo);
      aciDetailsPanel.removeAll();
      Div placeholder = new Div();
      placeholder.setText("Select an ACI entry to view details");
      placeholder.getStyle()
          .set("color", "var(--lumo-secondary-text-color)")
          .set("text-align", "center")
          .set("padding", "var(--lumo-space-xl)");
      aciDetailsPanel.add(placeholder);
      return;
    }

    loadingContainer.setVisible(true);
    allAciInfo.clear();

    // Load ACIs from each selected server
    for (LdapServerConfig server : selectedServers) {
      try {
        List<LdapEntry> results = ldapService.search(
            server,
            ACCESS_CONTROL_HANDLER_DN,
            "(objectClass=*)",
            SearchScope.BASE
        );

        if (!results.isEmpty()) {
          LdapEntry entry = results.get(0);
          List<String> aciValues = entry.getAttributes().get(GLOBAL_ACI_ATTRIBUTE);
          
          if (aciValues != null && !aciValues.isEmpty()) {
            for (String aciValue : aciValues) {
              allAciInfo.add(new GlobalAciInfo(aciValue, server.getName()));
            }
          }
        }
      } catch (LDAPException | java.security.GeneralSecurityException e) {
        logger.error("Failed to retrieve global ACIs from server {}: {}", 
            server.getName(), e.getMessage());
        NotificationHelper.showError("Failed to load ACIs from " + server.getName() + ": " + e.getMessage());
      }
    }

    loadingContainer.setVisible(false);
    aciGrid.setItems(allAciInfo);
    
    if (allAciInfo.isEmpty()) {
      NotificationHelper.showInfo("No global ACIs found on selected servers.");
    } else {
      NotificationHelper.showSuccess("Loaded " + allAciInfo.size() + " global ACIs from " 
          + selectedServers.size() + " server(s).");
    }
  }

  /**
   * Displays the details of the selected ACI in the details panel.
   *
   * @param selectedAci the selected ACI information
   */
  private void displayAciDetails(GlobalAciInfo selectedAci) {
    aciDetailsPanel.removeAll();

    com.ldapbrowser.util.AciParser.ParsedAci parsedAci = selectedAci.getParsedAci();

    // Header with title (no action buttons for read-only)
    H4 aciTitle = new H4(parsedAci.getName());
    aciTitle.getStyle().set("margin", "0").set("margin-bottom", "var(--lumo-space-m)");
    aciDetailsPanel.add(aciTitle);

    // Server section
    addDetailRow("Server", selectedAci.getServerName());

    // Resources section
    if (!parsedAci.getTargets().isEmpty()) {
      addDetailRow("Resources", String.join(", ", parsedAci.getTargets()));
    } else {
      addDetailRow("Resources", "All resources");
    }

    // Rights section
    String rightsValue = parsedAci.getAllowOrDeny().toUpperCase();
    if (!parsedAci.getPermissions().isEmpty()) {
      rightsValue += " (" + String.join(", ", parsedAci.getPermissions()) + ")";
    }
    addDetailRow("Rights", rightsValue);

    // Clients section
    if (!parsedAci.getBindRules().isEmpty()) {
      addDetailRow("Clients", String.join(", ", parsedAci.getBindRules()));
    } else {
      addDetailRow("Clients", "Any client");
    }

    // Raw Definition section
    HorizontalLayout rawSection = new HorizontalLayout();
    rawSection.setDefaultVerticalComponentAlignment(HorizontalLayout.Alignment.START);
    rawSection.setSpacing(true);
    rawSection.setWidthFull();
    rawSection.getStyle().set("margin-top", "var(--lumo-space-l)");
    
    Span rawLabel = new Span("Raw Definition:");
    rawLabel.getStyle().set("font-weight", "bold").set("min-width", "150px");
    
    TextArea rawTextArea = new TextArea();
    rawTextArea.setValue(parsedAci.getRawAci());
    rawTextArea.setReadOnly(true);
    rawTextArea.setWidthFull();
    rawTextArea.setMinHeight("100px");
    rawTextArea.getStyle()
        .set("font-family", "monospace")
        .set("font-size", "var(--lumo-font-size-s)");
    
    rawSection.add(rawLabel, rawTextArea);
    rawSection.setFlexGrow(1, rawTextArea);
    
    aciDetailsPanel.add(rawSection);
  }

  /**
   * Adds a detail row with label and value.
   *
   * @param label the label text
   * @param value the value text
   */
  private void addDetailRow(String label, String value) {
    if (value != null && !value.trim().isEmpty()) {
      HorizontalLayout row = new HorizontalLayout();
      row.setDefaultVerticalComponentAlignment(HorizontalLayout.Alignment.START);
      row.setSpacing(true);
      row.getStyle().set("margin-bottom", "var(--lumo-space-s)");

      Span labelSpan = new Span(label + ":");
      labelSpan.getStyle().set("font-weight", "bold").set("min-width", "150px");

      Span valueSpan = new Span(value);
      valueSpan.getStyle().set("word-wrap", "break-word");

      row.add(labelSpan, valueSpan);
      row.setFlexGrow(1, valueSpan);

      aciDetailsPanel.add(row);
    }
  }

  /**
   * Filters the ACI grid based on the search field value.
   */
  private void filterAciGrid() {
    String searchTerm = searchField.getValue().toLowerCase().trim();
    
    if (searchTerm.isEmpty()) {
      aciGrid.setItems(allAciInfo);
    } else {
      List<GlobalAciInfo> filteredList = allAciInfo.stream()
          .filter(aci -> 
              aci.getName().toLowerCase().contains(searchTerm) 
              || aci.getAciValue().toLowerCase().contains(searchTerm)
              || aci.getServerName().toLowerCase().contains(searchTerm))
          .collect(Collectors.toList());
      aciGrid.setItems(filteredList);
    }
  }

  /**
   * Data class for global ACI information.
   */
  public static class GlobalAciInfo {
    private final String aciValue;
    private final String serverName;
    private final com.ldapbrowser.util.AciParser.ParsedAci parsedAci;

    /**
     * Creates a new GlobalAciInfo instance.
     *
     * @param aciValue the raw ACI string value
     * @param serverName the name of the server this ACI came from
     */
    public GlobalAciInfo(String aciValue, String serverName) {
      this.aciValue = aciValue;
      this.serverName = serverName;
      this.parsedAci = com.ldapbrowser.util.AciParser.parseAci(aciValue);
    }

    public String getAciValue() {
      return aciValue;
    }

    public String getServerName() {
      return serverName;
    }

    public com.ldapbrowser.util.AciParser.ParsedAci getParsedAci() {
      return parsedAci;
    }

    public String getName() {
      return parsedAci.getName();
    }

    public String getResources() {
      return parsedAci.getResourcesString();
    }

    public String getRights() {
      return parsedAci.getRightsString();
    }

    public String getClients() {
      return parsedAci.getClientsString();
    }
  }
}
