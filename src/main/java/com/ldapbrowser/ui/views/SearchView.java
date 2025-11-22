package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.dialogs.DnBrowserDialog;
import com.ldapbrowser.ui.components.AdvancedSearchBuilder;
import com.ldapbrowser.ui.components.EntryEditor;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchScope;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Search view for performing LDAP searches.
 * Provides search filters and displays results.
 */
@Route(value = "search", layout = MainLayout.class)
@PageTitle("Search | LDAP Browser")
public class SearchView extends VerticalLayout implements BeforeEnterObserver {

  private static final Logger logger = LoggerFactory.getLogger(SearchView.class);

  private final ConfigurationService configService;
  private final LdapService ldapService;

  private TextField searchBaseField;
  private TextField filterField;
  private Select<SearchScope> scopeSelect;
  private MultiSelectComboBox<String> returnAttributesField;
  private Button searchButton;
  private TextField gridFilterField;
  private Grid<LdapEntry> resultsGrid;
  private EntryEditor entryEditor;
  private List<LdapEntry> currentResults;

  /**
   * Creates the Search view.
   *
   * @param configService configuration service
   * @param ldapService LDAP service
   */
  public SearchView(ConfigurationService configService, LdapService ldapService) {
    this.configService = configService;
    this.ldapService = ldapService;
    this.currentResults = new ArrayList<>();

    setSizeFull();
    setPadding(false);
    setSpacing(false);

    createLayout();
  }

  private void createLayout() {
    // Header
    H3 title = new H3("LDAP Search");
    title.getStyle().set("margin", "var(--lumo-space-m)");

    // Search form
    VerticalLayout searchForm = createSearchForm();

    // Split layout for results and details
    SplitLayout splitLayout = new SplitLayout();
    splitLayout.setSizeFull();
    splitLayout.setSplitterPosition(60);

    // Results grid
    resultsGrid = createResultsGrid();
    VerticalLayout resultsLayout = new VerticalLayout(resultsGrid);
    resultsLayout.setSizeFull();
    resultsLayout.setPadding(false);

    // Entry editor
    entryEditor = new EntryEditor(ldapService, configService);
    entryEditor.setExpandListener(this::showExpandedEntryDialog);

    splitLayout.addToPrimary(resultsLayout);
    splitLayout.addToSecondary(entryEditor);

    add(title, searchForm, splitLayout);
    expand(splitLayout);
  }

  private VerticalLayout createSearchForm() {
    VerticalLayout formLayout = new VerticalLayout();
    formLayout.setPadding(true);
    formLayout.setSpacing(true);

    // First row: compact search fields
    HorizontalLayout compactSearchRow = new HorizontalLayout();
    compactSearchRow.setWidthFull();
    compactSearchRow.setDefaultVerticalComponentAlignment(Alignment.END);
    compactSearchRow.setSpacing(true);

    // Search Base with Browse button
    HorizontalLayout searchBaseLayout = new HorizontalLayout();
    searchBaseLayout.setWidthFull();
    searchBaseLayout.setDefaultVerticalComponentAlignment(Alignment.END);
    searchBaseLayout.setSpacing(false);
    searchBaseLayout.getStyle().set("gap", "var(--lumo-space-xs)");
    
    searchBaseField = new TextField("Search Base");
    searchBaseField.setWidthFull();
    searchBaseField.setPlaceholder("dc=example,dc=com");

    Button browseButton = new Button(VaadinIcon.FOLDER_OPEN.create());
    browseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    browseButton.setTooltipText("Select DN from Directory");
    browseButton.addClickListener(e -> showBrowseDialog());

    searchBaseLayout.add(searchBaseField, browseButton);
    searchBaseLayout.expand(searchBaseField);

    filterField = new TextField("Filter");
    filterField.setWidthFull();
    filterField.setPlaceholder("(objectClass=*)");

    scopeSelect = new Select<>();
    scopeSelect.setLabel("Scope");
    scopeSelect.setItems(SearchScope.values());
    scopeSelect.setValue(SearchScope.SUB);
    scopeSelect.setItemLabelGenerator(scope -> {
      if (scope == SearchScope.BASE) {
        return "Base";
      } else if (scope == SearchScope.ONE) {
        return "One Level";
      } else if (scope == SearchScope.SUB) {
        return "Subtree";
      } else if (scope == SearchScope.SUBORDINATE_SUBTREE) {
        return "Subordinate";
      }
      return scope.toString();
    });
    scopeSelect.setWidth("150px");

    // Return Attributes field - multi-select combo box with schema attributes
    returnAttributesField = new MultiSelectComboBox<>("Return Attributes");
    returnAttributesField.setPlaceholder("All attributes");
    returnAttributesField.setWidth("250px");
    returnAttributesField.setAllowCustomValue(true);
    
    // Add common default attributes for immediate use
    List<String> commonAttributes = new ArrayList<>();
    commonAttributes.add("cn");
    commonAttributes.add("sn");
    commonAttributes.add("givenName");
    commonAttributes.add("mail");
    commonAttributes.add("uid");
    commonAttributes.add("objectClass");
    commonAttributes.add("ou");
    commonAttributes.add("dc");
    commonAttributes.add("description");
    commonAttributes.add("telephoneNumber");
    commonAttributes.add("title");
    commonAttributes.add("memberOf");
    commonAttributes.add("member");
    returnAttributesField.setItems(commonAttributes);
    
    // Set default value to "cn"
    returnAttributesField.setValue(java.util.Collections.singleton("cn"));
    
    returnAttributesField.addCustomValueSetListener(event -> {
      String customValue = event.getDetail();
      if (customValue != null && !customValue.trim().isEmpty()) {
        Set<String> currentValues = returnAttributesField.getSelectedItems();
        List<String> newValues = new ArrayList<>(currentValues);
        newValues.add(customValue.trim());
        returnAttributesField.setValue(newValues);
      }
    });
    
    // Add focus listener to load attributes from schema when field is focused
    returnAttributesField.addFocusListener(event -> {
      if (returnAttributesField.getListDataView().getItemCount() <= 15) {
        // Only reload if we still have the default list
        loadAttributesFromSchema();
      }
    });

    compactSearchRow.add(searchBaseLayout, filterField, scopeSelect, returnAttributesField);
    compactSearchRow.setFlexGrow(2, searchBaseLayout);
    compactSearchRow.setFlexGrow(2, filterField);
    compactSearchRow.setFlexGrow(0, scopeSelect);
    compactSearchRow.setFlexGrow(1, returnAttributesField);

    // Second row: action buttons and grid filter
    searchButton = new Button("Search", VaadinIcon.SEARCH.create());
    searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    searchButton.addClickListener(e -> performSearch());

    Button filterBuilderButton = new Button("Filter Builder", VaadinIcon.FILTER.create());
    filterBuilderButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    filterBuilderButton.addClickListener(e -> showFilterBuilderDialog());

    gridFilterField = new TextField();
    gridFilterField.setPlaceholder("Filter results...");
    gridFilterField.setPrefixComponent(VaadinIcon.SEARCH.create());
    gridFilterField.setClearButtonVisible(true);
    gridFilterField.setWidth("300px");
    gridFilterField.addValueChangeListener(e -> filterResults(e.getValue()));

    HorizontalLayout buttonLayout = new HorizontalLayout(searchButton, filterBuilderButton, gridFilterField);
    buttonLayout.setSpacing(true);
    buttonLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    formLayout.add(compactSearchRow, buttonLayout);
    return formLayout;
  }

  private void showFilterBuilderDialog() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Filter Builder");
    dialog.setWidth("900px");
    dialog.setHeight("700px");
    dialog.setModal(true);
    dialog.setCloseOnOutsideClick(false);

    VerticalLayout dialogLayout = new VerticalLayout();
    dialogLayout.setSizeFull();
    dialogLayout.setPadding(true);
    dialogLayout.setSpacing(true);

    // Filter field (manual entry)
    TextField dialogFilterField = new TextField("Filter");
    dialogFilterField.setWidthFull();
    dialogFilterField.setPlaceholder("(objectClass=*)");
    dialogFilterField.setValue(filterField.getValue() != null ? filterField.getValue() : "");

    // Filter builder component
    AdvancedSearchBuilder filterBuilder = new AdvancedSearchBuilder(ldapService);
    filterBuilder.getStyle()
        .set("border", "1px solid var(--lumo-contrast-20pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("background-color", "var(--lumo-contrast-5pct)")
        .set("padding", "var(--lumo-space-m)");
    
    // Set current search base in filter builder if available
    String currentBase = searchBaseField.getValue();
    if (currentBase != null && !currentBase.isEmpty()) {
      filterBuilder.setSearchBase(currentBase);
    }

    // Button to apply filter from builder to the text field
    Button applyFilterButton = new Button("Apply Filter from Builder", VaadinIcon.ARROW_DOWN.create());
    applyFilterButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    applyFilterButton.addClickListener(e -> {
      String generatedFilter = filterBuilder.getGeneratedFilter();
      if (generatedFilter != null && !generatedFilter.isEmpty()) {
        dialogFilterField.setValue(generatedFilter);
      }
    });

    HorizontalLayout filterBuilderHeader = new HorizontalLayout();
    filterBuilderHeader.setWidthFull();
    filterBuilderHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    filterBuilderHeader.add(new com.vaadin.flow.component.html.H4("Visual Filter Builder"), applyFilterButton);
    filterBuilderHeader.setFlexGrow(1, filterBuilderHeader.getComponentAt(0));

    dialogLayout.add(dialogFilterField, filterBuilderHeader, filterBuilder);
    dialogLayout.setFlexGrow(1, filterBuilder);

    // Dialog buttons in header
    Button applyButton = new Button("Apply", e -> {
      filterField.setValue(dialogFilterField.getValue());
      dialog.close();
    });
    applyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.getHeader().add(applyButton, cancelButton);

    dialog.add(dialogLayout);
    dialog.open();
  }

  private Grid<LdapEntry> createResultsGrid() {
    Grid<LdapEntry> grid = new Grid<>();
    grid.setSizeFull();

    // Initial columns - will be updated dynamically based on return attributes
    updateGridColumns(grid, new ArrayList<>());

    grid.addSelectionListener(selection -> {
      selection.getFirstSelectedItem().ifPresent(this::showEntryDetails);
    });

    return grid;
  }

  /**
   * Updates the grid columns based on selected return attributes.
   * Always starts with Server and Distinguished Name columns,
   * followed by columns for each selected attribute.
   */
  private void updateGridColumns(Grid<LdapEntry> grid, List<String> selectedAttributes) {
    grid.removeAllColumns();

    // Always add Server column
    grid.addColumn(LdapEntry::getServerName)
        .setHeader("Server")
        .setWidth("150px")
        .setFlexGrow(0)
        .setResizable(true);

    // Always add Distinguished Name column
    grid.addColumn(LdapEntry::getDn)
        .setHeader("Distinguished Name")
        .setFlexGrow(1)
        .setResizable(true);

    // Add columns for selected attributes
    if (selectedAttributes != null && !selectedAttributes.isEmpty()) {
      for (String attrName : selectedAttributes) {
        grid.addColumn(entry -> {
          List<String> values = entry.getAttributeValues(attrName);
          return values.isEmpty() ? "" : String.join(", ", values);
        }).setHeader(attrName).setWidth("200px").setResizable(true);
      }
    }
  }

  /**
   * Loads attribute names from the schemas of selected servers.
   */
  private void loadAttributesFromSchema() {
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers == null || selectedServers.isEmpty()) {
      logger.debug("No servers selected, keeping default attributes");
      return;
    }

    List<LdapServerConfig> configs = configService.loadConfigurations();
    List<LdapServerConfig> selectedConfigs = configs.stream()
        .filter(c -> selectedServers.contains(c.getName()))
        .collect(java.util.stream.Collectors.toList());

    if (selectedConfigs.isEmpty()) {
      logger.debug("No matching server configs found, keeping default attributes");
      return;
    }

    try {
      List<String> attributeNames = ldapService.getAllAttributeNames(selectedConfigs);
      if (attributeNames != null && !attributeNames.isEmpty()) {
        returnAttributesField.setItems(attributeNames);
        logger.info("Loaded {} attribute names from schema", attributeNames.size());
      } else {
        logger.debug("No attributes returned from schema, keeping default attributes");
      }
    } catch (Exception e) {
      logger.warn("Failed to load attribute names from schema: {}", e.getMessage());
      // Keep the existing items (don't clear them)
    }
  }

  private void showEntryDetails(LdapEntry entry) {
    // Find the server config for this entry
    List<LdapServerConfig> configs = configService.loadConfigurations();
    configs.stream()
        .filter(c -> c.getName().equals(entry.getServerName()))
        .findFirst()
        .ifPresent(config -> {
          entryEditor.setServerConfig(config);
          entryEditor.editEntry(entry);
        });
  }

  private void showExpandedEntryDialog() {
    // Get the current entry from the editor
    LdapEntry currentEntry = entryEditor.getCurrentEntry();
    LdapServerConfig currentConfig = entryEditor.getServerConfig();
    
    if (currentEntry == null || currentConfig == null) {
      return;
    }

    // Create a dialog with a full-size entry editor
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Entry: " + currentEntry.getRdn());
    dialog.setWidth("90%");
    dialog.setHeight("90%");
    dialog.setModal(true);

    // Create a new entry editor for the dialog
    EntryEditor dialogEditor = new EntryEditor(ldapService, configService);
    dialogEditor.setServerConfig(currentConfig);
    dialogEditor.editEntry(currentEntry);
    dialogEditor.setSizeFull();

    // Close button
    Button closeButton = new Button("Close", e -> dialog.close());
    closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

    HorizontalLayout buttonBar = new HorizontalLayout(closeButton);
    buttonBar.setJustifyContentMode(JustifyContentMode.END);
    buttonBar.setPadding(true);

    VerticalLayout dialogLayout = new VerticalLayout(dialogEditor, buttonBar);
    dialogLayout.setSizeFull();
    dialogLayout.setPadding(false);
    dialogLayout.setSpacing(false);
    dialogLayout.expand(dialogEditor);

    dialog.add(dialogLayout);
    dialog.open();
  }

  private void filterResults(String filterText) {
    if (filterText == null || filterText.isEmpty()) {
      // No filter, show all results
      resultsGrid.setItems(currentResults);
      return;
    }

    // Filter results based on the text
    String lowerCaseFilter = filterText.toLowerCase();
    List<LdapEntry> filteredResults = currentResults.stream()
        .filter(entry -> {
          // Check if DN matches
          if (entry.getDn().toLowerCase().contains(lowerCaseFilter)) {
            return true;
          }
          // Check if RDN matches
          if (entry.getRdn() != null && entry.getRdn().toLowerCase().contains(lowerCaseFilter)) {
            return true;
          }
          // Check if server name matches
          if (entry.getServerName().toLowerCase().contains(lowerCaseFilter)) {
            return true;
          }
          // Check if any attribute value matches
          for (Map.Entry<String, List<String>> attr : entry.getAttributes().entrySet()) {
            // Check attribute name
            if (attr.getKey().toLowerCase().contains(lowerCaseFilter)) {
              return true;
            }
            // Check attribute values
            for (String value : attr.getValue()) {
              if (value.toLowerCase().contains(lowerCaseFilter)) {
                return true;
              }
            }
          }
          return false;
        })
        .collect(java.util.stream.Collectors.toList());

    resultsGrid.setItems(filteredResults);
  }

  private void performSearch() {
    String baseDn = searchBaseField.getValue();
    String filter = filterField.getValue();
    SearchScope scope = scopeSelect.getValue();

    if (baseDn == null || baseDn.isEmpty()) {
      Notification.show("Please enter a search base", 3000, Notification.Position.MIDDLE)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    if (filter == null || filter.isEmpty()) {
      filter = "(objectClass=*)";
    }

    // Get selected servers from session
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers.isEmpty()) {
      Notification.show(
          "Please select at least one server from the navbar",
          3000,
          Notification.Position.MIDDLE
      ).addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    // Get selected return attributes
    Set<String> selectedAttributes = returnAttributesField.getSelectedItems();
    String[] attributesToReturn = null;
    if (selectedAttributes != null && !selectedAttributes.isEmpty()) {
      attributesToReturn = selectedAttributes.toArray(new String[0]);
    }

    currentResults.clear();
    List<LdapServerConfig> configs = configService.loadConfigurations();

    String finalFilter = filter;
    String[] finalAttributes = attributesToReturn;
    for (String serverName : selectedServers) {
      configs.stream()
          .filter(c -> c.getName().equals(serverName))
          .findFirst()
          .ifPresent(config -> {
            try {
              List<LdapEntry> results;
              if (finalAttributes != null) {
                results = ldapService.search(config, baseDn, finalFilter, scope, finalAttributes);
              } else {
                results = ldapService.search(config, baseDn, finalFilter, scope);
              }
              currentResults.addAll(results);
              logger.info("Search on {} returned {} results", serverName, results.size());
            } catch (LDAPException | GeneralSecurityException e) {
              Notification.show(
                  "Search failed on " + serverName + ": " + e.getMessage(),
                  5000,
                  Notification.Position.MIDDLE
              ).addThemeVariants(NotificationVariant.LUMO_ERROR);
              logger.error("Search failed on {}", serverName, e);
            }
          });
    }

    // Update grid columns based on selected attributes
    List<String> selectedAttributesList = new ArrayList<>(
        selectedAttributes != null ? selectedAttributes : new ArrayList<>());
    updateGridColumns(resultsGrid, selectedAttributesList);

    // Clear the grid filter when new search is performed
    gridFilterField.clear();
    
    resultsGrid.setItems(currentResults);
    Notification.show(
        "Search complete: " + currentResults.size() + " entries found",
        3000,
        Notification.Position.BOTTOM_END
    ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  private void showBrowseDialog() {
    // Get the selected servers from the session
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers == null || selectedServers.isEmpty()) {
      Notification notification = Notification.show(
          "Please select a server from the Connections page",
          3000,
          Notification.Position.MIDDLE);
      notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    // Load all selected server configurations
    List<LdapServerConfig> configs = configService.loadConfigurations();
    List<LdapServerConfig> selectedConfigs = configs.stream()
        .filter(c -> selectedServers.contains(c.getName()))
        .collect(java.util.stream.Collectors.toList());

    if (selectedConfigs.isEmpty()) {
      Notification notification = Notification.show(
          "Server configuration not found",
          3000,
          Notification.Position.MIDDLE);
      notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    new DnBrowserDialog(ldapService)
        .withServerConfigs(selectedConfigs)
        .withValidation(
            entry -> isValidDnForSearch(entry),
            "Please select a valid DN (not a server or Root DSE)"
        )
        .onDnSelected(dn -> searchBaseField.setValue(dn))
        .open();
  }

  /**
   * Checks if the selected entry is valid for use as a search base DN.
   * Excludes server nodes and Root DSE entries.
   */
  private boolean isValidDnForSearch(LdapEntry entry) {
    if (entry == null) {
      return false;
    }
    
    // Exclude server nodes
    if (entry.getDn().startsWith("SERVER:")) {
      return false;
    }
    
    // Exclude Root DSE (empty DN)
    if (entry.getDn().isEmpty() || "Root DSE".equals(entry.getRdn())) {
      return false;
    }
    
    return true;
  }

  /**
   * Sets the search base DN.
   * This is used when navigating from other views to pre-populate the search base.
   *
   * @param dn the DN to set as the search base
   */
  public void setSearchBase(String dn) {
    if (dn != null && !dn.isEmpty()) {
      searchBaseField.setValue(dn);
      searchBaseField.focus();
    }
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    // Check for searchBase query parameter
    QueryParameters queryParameters = event.getLocation().getQueryParameters();
    Map<String, List<String>> params = queryParameters.getParameters();
    
    if (params.containsKey("searchBase")) {
      List<String> searchBaseValues = params.get("searchBase");
      if (searchBaseValues != null && !searchBaseValues.isEmpty()) {
        String searchBase = searchBaseValues.get(0);
        setSearchBase(searchBase);
      }
    }
  }

}
