package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.EntryTemplate;
import com.ldapbrowser.model.EntryTemplate.SearchTemplateSection;
import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.TemplateService;
import com.ldapbrowser.service.TruststoreService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.dialogs.DnBrowserDialog;
import com.ldapbrowser.ui.components.AdvancedSearchBuilder;
import com.ldapbrowser.ui.components.EntryEditor;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchScope;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.security.RolesAllowed;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

/**
 * Search view for performing LDAP searches.
 * Provides search filters and displays results.
 */
@Route(value = "search", layout = MainLayout.class)
@PageTitle("Search | LDAP Browser")
@RolesAllowed({"ADMIN", "VIEWER"})
@UIScope
@Component
public class SearchView extends VerticalLayout implements BeforeEnterObserver {

  private static final Logger logger = LoggerFactory.getLogger(SearchView.class);

  private final ConfigurationService configService;
  private final LdapService ldapService;
  private final TruststoreService truststoreService;
  private final TemplateService templateService;

  private TextField searchBaseField;
  private TextField filterField;
  private Select<SearchScope> scopeSelect;
  private MultiSelectComboBox<String> returnAttributesField;
  private RadioButtonGroup<String> baseTypeRadio;
  private Button browseButton;
  private Button searchButton;
  private Grid<LdapEntry> resultsGrid;
  private EntryEditor entryEditor;
  private ComboBox<String> searchTemplateCombo;
  private TextField templateSearchField;
  private List<EntryTemplate> searchTemplates = new ArrayList<>();
  private List<LdapEntry> currentResults;
  private Map<String, TextField> columnFilters;

  /**
   * Creates the Search view.
   *
   * @param configService configuration service
   * @param ldapService LDAP service
   * @param truststoreService truststore service
   * @param templateService template service
   */
  public SearchView(ConfigurationService configService,
      LdapService ldapService,
      TruststoreService truststoreService,
      TemplateService templateService) {
    this.configService = configService;
    this.ldapService = ldapService;
    this.truststoreService = truststoreService;
    this.templateService = templateService;
    this.currentResults = new ArrayList<>();
    this.columnFilters = new HashMap<>();

    setSizeFull();
    setPadding(false);
    setSpacing(false);

    createLayout();
  }

  private void createLayout() {
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
    entryEditor = new EntryEditor(ldapService, configService,
        templateService);
    entryEditor.setExpandListener(this::showExpandedEntryDialog);

    splitLayout.addToPrimary(resultsLayout);
    splitLayout.addToSecondary(entryEditor);

    add(searchForm, splitLayout);
    expand(splitLayout);
  }

  private VerticalLayout createSearchForm() {
    VerticalLayout formLayout = new VerticalLayout();
    formLayout.setPadding(true);
    formLayout.setSpacing(true);

    // First row: compact search fields
    HorizontalLayout compactSearchRow = new HorizontalLayout();
    compactSearchRow.setWidthFull();
    compactSearchRow.setDefaultVerticalComponentAlignment(Alignment.START);
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
    searchBaseField.setEnabled(false);

    browseButton = new Button(VaadinIcon.FOLDER_OPEN.create());
    browseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    browseButton.setTooltipText("Select DN from Directory");
    browseButton.addClickListener(e -> showBrowseDialog());
    browseButton.setEnabled(false);

    searchBaseLayout.add(searchBaseField, browseButton);
    searchBaseLayout.expand(searchBaseField);

    // Base type radio group
    baseTypeRadio = new RadioButtonGroup<>();
    baseTypeRadio.setItems("Default Base", "Custom Base");
    baseTypeRadio.setValue("Default Base");
    baseTypeRadio.getStyle().set("margin-top", "0");
    baseTypeRadio.addValueChangeListener(e -> {
      boolean isCustom = "Custom Base".equals(e.getValue());
      searchBaseField.setEnabled(isCustom);
      browseButton.setEnabled(isCustom);
    });

    // Filter field with Filter Builder button
    HorizontalLayout filterLayout = new HorizontalLayout();
    filterLayout.setWidthFull();
    filterLayout.setDefaultVerticalComponentAlignment(Alignment.END);
    filterLayout.setSpacing(false);
    filterLayout.getStyle().set("gap", "var(--lumo-space-xs)");
    
    filterField = new TextField("Filter");
    filterField.setWidthFull();
    filterField.setPlaceholder("(objectClass=*)");

    Button filterBuilderButton = new Button(VaadinIcon.FILTER.create());
    filterBuilderButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    filterBuilderButton.setTooltipText("Filter Builder");
    filterBuilderButton.addClickListener(e -> showFilterBuilderDialog());

    filterLayout.add(filterField, filterBuilderButton);
    filterLayout.expand(filterField);

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

    // Add base type radio under search base
    VerticalLayout searchBaseWithRadio = new VerticalLayout(
        searchBaseLayout, baseTypeRadio);
    searchBaseWithRadio.setPadding(false);
    searchBaseWithRadio.setSpacing(false);

    // Add search button under filter, aligned with radio group
    searchButton = new Button("Search", VaadinIcon.SEARCH.create());
    searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    searchButton.addClickListener(e -> performSearch());

    HorizontalLayout searchButtonRow = new HorizontalLayout(searchButton);
    searchButtonRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    searchButtonRow.setSpacing(false);

    VerticalLayout filterWithButton = new VerticalLayout(
        filterLayout, searchButtonRow);
    filterWithButton.setPadding(false);
    filterWithButton.setSpacing(false);

    compactSearchRow.add(searchBaseWithRadio, filterWithButton,
        scopeSelect, returnAttributesField);
    compactSearchRow.setFlexGrow(2, searchBaseWithRadio);
    compactSearchRow.setFlexGrow(2, filterWithButton);
    compactSearchRow.setFlexGrow(0, scopeSelect);
    compactSearchRow.setFlexGrow(1, returnAttributesField);

    // Template search row
    HorizontalLayout templateRow = new HorizontalLayout();
    templateRow.setWidthFull();
    templateRow.setDefaultVerticalComponentAlignment(Alignment.END);
    templateRow.setSpacing(true);

    searchTemplateCombo = new ComboBox<>("Search Template");
    searchTemplateCombo.setWidth("200px");
    searchTemplateCombo.setClearButtonVisible(true);
    loadSearchTemplates();
    searchTemplateCombo.addValueChangeListener(e -> {
      boolean isTemplate = e.getValue() != null
          && !"LDAP".equals(e.getValue());
      templateSearchField.setVisible(isTemplate);
      compactSearchRow.setVisible(!isTemplate);
    });

    templateSearchField = new TextField("Search");
    templateSearchField.setWidthFull();
    templateSearchField.setPlaceholder(
        "Enter search text (used in template filter)");
    templateSearchField.setVisible(false);

    Button templateSearchButton = new Button("Search",
        VaadinIcon.SEARCH.create(),
        e -> performTemplateSearch());
    templateSearchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    templateRow.add(searchTemplateCombo, templateSearchField,
        templateSearchButton);
    templateRow.setFlexGrow(1, templateSearchField);

    formLayout.add(templateRow, compactSearchRow);
    return formLayout;
  }

  private void showFilterBuilderDialog() {
    String currentFilter = filterField.getValue();
    String currentBase = searchBaseField.getValue();
    
    Dialog dialog = AdvancedSearchBuilder.createFilterBuilderDialog(
        ldapService,
        "Filter Builder",
        currentFilter,
        currentBase,
        filter -> filterField.setValue(filter)
    );
    
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
   * Adds filter text fields in the header row for each column.
   */
  private void updateGridColumns(Grid<LdapEntry> grid, List<String> selectedAttributes) {
    grid.removeAllColumns();
    columnFilters.clear();

    // Always add Server column
    Grid.Column<LdapEntry> serverColumn = grid.addColumn(LdapEntry::getServerName)
        .setHeader("Server")
        .setWidth("150px")
        .setFlexGrow(0)
        .setResizable(true);

    // Always add Distinguished Name column
    Grid.Column<LdapEntry> dnColumn = grid.addColumn(LdapEntry::getDn)
        .setHeader("Distinguished Name")
        .setFlexGrow(1)
        .setResizable(true);

    // Add columns for selected attributes
    List<Grid.Column<LdapEntry>> attributeColumns = new ArrayList<>();
    List<String> attributeNames = new ArrayList<>();
    if (selectedAttributes != null && !selectedAttributes.isEmpty()) {
      for (String attrName : selectedAttributes) {
        Grid.Column<LdapEntry> col = grid.addColumn(entry -> {
          List<String> values = entry.getAttributeValues(attrName);
          return values.isEmpty() ? "" : String.join(", ", values);
        }).setHeader(attrName).setWidth("200px").setResizable(true);
        attributeColumns.add(col);
        attributeNames.add(attrName);
      }
    }

    // Only append header row if not already present (after column setup)
    // getHeaderRows() always has at least 1 row (the default header with column names)
    // so we only add our filter row if size == 1
    HeaderRow filterRow;
    if (grid.getHeaderRows().size() == 1) {
      filterRow = grid.appendHeaderRow();
    } else {
      filterRow = grid.getHeaderRows().get(1); // Reuse existing filter row
    }

    // Server column filter
    TextField serverFilter = new TextField();
    serverFilter.setPlaceholder("Filter...");
    serverFilter.setClearButtonVisible(true);
    serverFilter.setWidthFull();
    serverFilter.setValueChangeMode(ValueChangeMode.EAGER);
    serverFilter.addValueChangeListener(e -> applyColumnFilters());
    filterRow.getCell(serverColumn).setComponent(serverFilter);
    columnFilters.put("server", serverFilter);

    // DN column filter
    TextField dnFilter = new TextField();
    dnFilter.setPlaceholder("Filter...");
    dnFilter.setClearButtonVisible(true);
    dnFilter.setWidthFull();
    dnFilter.setValueChangeMode(ValueChangeMode.EAGER);
    dnFilter.addValueChangeListener(e -> applyColumnFilters());
    filterRow.getCell(dnColumn).setComponent(dnFilter);
    columnFilters.put("dn", dnFilter);

    // Attribute column filters
    for (int i = 0; i < attributeColumns.size(); i++) {
      TextField attrFilter = new TextField();
      attrFilter.setPlaceholder("Filter...");
      attrFilter.setClearButtonVisible(true);
      attrFilter.setWidthFull();
      attrFilter.setValueChangeMode(ValueChangeMode.EAGER);
      attrFilter.addValueChangeListener(e -> applyColumnFilters());
      filterRow.getCell(attributeColumns.get(i)).setComponent(attrFilter);
      columnFilters.put("attr_" + attributeNames.get(i), attrFilter);
    }
  }

  /**
   * Applies column filters to the results grid.
   */
  private void applyColumnFilters() {
    if (currentResults == null || currentResults.isEmpty()) {
      return;
    }

    List<LdapEntry> filteredResults = new ArrayList<>(currentResults);

    // Apply server filter
    TextField serverFilter = columnFilters.get("server");
    if (serverFilter != null && serverFilter.getValue() != null && !serverFilter.getValue().isEmpty()) {
      String serverFilterValue = serverFilter.getValue().toLowerCase();
      filteredResults = filteredResults.stream()
          .filter(entry -> entry.getServerName().toLowerCase().contains(serverFilterValue))
          .collect(java.util.stream.Collectors.toList());
    }

    // Apply DN filter
    TextField dnFilter = columnFilters.get("dn");
    if (dnFilter != null && dnFilter.getValue() != null && !dnFilter.getValue().isEmpty()) {
      String dnFilterValue = dnFilter.getValue().toLowerCase();
      filteredResults = filteredResults.stream()
          .filter(entry -> entry.getDn().toLowerCase().contains(dnFilterValue))
          .collect(java.util.stream.Collectors.toList());
    }

    // Apply attribute filters
    for (Map.Entry<String, TextField> filterEntry : columnFilters.entrySet()) {
      String key = filterEntry.getKey();
      if (key.startsWith("attr_")) {
        String attrName = key.substring(5); // Remove "attr_" prefix
        TextField attrFilter = filterEntry.getValue();
        if (attrFilter != null && attrFilter.getValue() != null && !attrFilter.getValue().isEmpty()) {
          String attrFilterValue = attrFilter.getValue().toLowerCase();
          filteredResults = filteredResults.stream()
              .filter(entry -> {
                List<String> values = entry.getAttributeValues(attrName);
                return values.stream()
                    .anyMatch(value -> value.toLowerCase().contains(attrFilterValue));
              })
              .collect(java.util.stream.Collectors.toList());
        }
      }
    }

    resultsGrid.setItems(filteredResults);
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
          try {
            // Fetch the full entry with all user attributes (not just the ones from the search)
            LdapEntry fullEntry = ldapService.readEntry(config, entry.getDn(), false);
            entryEditor.setServerConfig(config);
            entryEditor.editEntry(fullEntry);
          } catch (Exception e) {
            logger.error("Failed to read full entry details for {}: {}", entry.getDn(), e.getMessage());
            // Fallback to showing the partial entry from search results
            entryEditor.setServerConfig(config);
            entryEditor.editEntry(entry);
            NotificationHelper.showError(
                "Could not load full entry details: " + e.getMessage(), 
                5000
            );
          }
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
    EntryEditor dialogEditor = new EntryEditor(ldapService,
        configService, templateService);
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

  private void performSearch() {
    boolean useDefaultBase = "Default Base".equals(baseTypeRadio.getValue());
    String baseDn = searchBaseField.getValue();
    String filter = filterField.getValue();
    SearchScope scope = scopeSelect.getValue();

    if (!useDefaultBase && (baseDn == null || baseDn.isEmpty())) {
      NotificationHelper.showError("Please enter a search base", 3000);
      return;
    }

    if (filter == null || filter.isEmpty()) {
      filter = "(objectClass=*)";
    }

    // Get selected servers from session
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers.isEmpty()) {
      NotificationHelper.showError(
          "Please select at least one server from the navbar",
          3000);
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

    // Validate default base availability when using default base
    if (useDefaultBase) {
      for (String serverName : selectedServers) {
        configs.stream()
            .filter(c -> c.getName().equals(serverName))
            .findFirst()
            .ifPresent(config -> {
              if (config.getBaseDn() == null
                  || config.getBaseDn().isEmpty()) {
                NotificationHelper.showError(
                    "No default base DN configured for server: "
                        + serverName,
                    5000);
              }
            });
      }
      // Check if any server lacks a default base
      boolean anyMissing = selectedServers.stream().anyMatch(serverName ->
          configs.stream()
              .filter(c -> c.getName().equals(serverName))
              .findFirst()
              .map(c -> c.getBaseDn() == null || c.getBaseDn().isEmpty())
              .orElse(true));
      if (anyMissing) {
        return;
      }
    }

    String finalFilter = filter;
    String[] finalAttributes = attributesToReturn;
    for (String serverName : selectedServers) {
      configs.stream()
          .filter(c -> c.getName().equals(serverName))
          .findFirst()
          .ifPresent(config -> {
            try {
              String searchBase = useDefaultBase
                  ? config.getBaseDn() : baseDn;
              List<LdapEntry> results;
              if (finalAttributes != null) {
                results = ldapService.search(
                    config, searchBase, finalFilter, scope,
                    finalAttributes);
              } else {
                results = ldapService.search(
                    config, searchBase, finalFilter, scope);
              }
              currentResults.addAll(results);
              logger.info("Search on {} returned {} results",
                  serverName, results.size());
            } catch (LDAPException | GeneralSecurityException e) {
              NotificationHelper.showError(
                  "Search failed on " + serverName + ": "
                      + e.getMessage(),
                  5000);
              logger.error("Search failed on {}", serverName, e);
            }
          });
    }

    // Update grid columns based on selected attributes
    List<String> selectedAttributesList = new ArrayList<>(
        selectedAttributes != null ? selectedAttributes : new ArrayList<>());
    updateGridColumns(resultsGrid, selectedAttributesList);

    // Column filters will be cleared when updateGridColumns creates new headers
    
    resultsGrid.setItems(currentResults);
    NotificationHelper.showSuccess(
        "Search complete: " + currentResults.size() + " entries found",
        3000);
  }

  private void showBrowseDialog() {
    // Get the selected servers from the session
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers == null || selectedServers.isEmpty()) {
      NotificationHelper.showError(
          "Please select a server from the Connections page",
          3000);
      return;
    }

    // Load all selected server configurations
    List<LdapServerConfig> configs = configService.loadConfigurations();
    List<LdapServerConfig> selectedConfigs = configs.stream()
        .filter(c -> selectedServers.contains(c.getName()))
        .collect(java.util.stream.Collectors.toList());

    if (selectedConfigs.isEmpty()) {
      NotificationHelper.showError(
          "Server configuration not found",
          3000);
      return;
    }

    new DnBrowserDialog(ldapService, truststoreService)
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

  /**
   * Loads templates that have a Search section.
   */
  private void loadSearchTemplates() {
    List<String> items = new ArrayList<>();
    items.add("LDAP");
    try {
      LdapServerConfig serverCfg = getSelectedServerConfig();
      List<EntryTemplate> all =
          (serverCfg != null)
              ? templateService.getTemplatesForServer(serverCfg)
              : templateService.loadTemplates();
      searchTemplates = all.stream()
          .filter(t -> t.getSearchSection() != null)
          .toList();
      for (EntryTemplate t : searchTemplates) {
        items.add(t.getName());
      }
    } catch (Exception e) {
      // templates file may not exist yet
    }
    searchTemplateCombo.setItems(items);
    searchTemplateCombo.setValue("LDAP");
  }

  /**
   * Gets the config for the first currently selected server.
   */
  private LdapServerConfig getSelectedServerConfig() {
    Set<String> names = MainLayout.getSelectedServers();
    if (names == null || names.isEmpty()) {
      return null;
    }
    String firstName = names.iterator().next();
    return configService.loadConfigurations().stream()
        .filter(c -> firstName.equals(c.getName()))
        .findFirst()
        .orElse(null);
  }

  /**
   * Performs a search using the selected search template.
   */
  private void performTemplateSearch() {
    String templateName = searchTemplateCombo.getValue();
    if (templateName == null || "LDAP".equals(templateName)) {
      performSearch();
      return;
    }
    EntryTemplate tmpl = searchTemplates.stream()
        .filter(t -> t.getName().equals(templateName))
        .findFirst().orElse(null);
    if (tmpl == null || tmpl.getSearchSection() == null) {
      NotificationHelper.showError("Template not found");
      return;
    }

    SearchTemplateSection ss = tmpl.getSearchSection();
    String searchText = templateSearchField.getValue();
    if (searchText == null) {
      searchText = "";
    }

    // Build filter by replacing {SEARCH} placeholder
    String filter = ss.getSearchFilter();
    if (filter == null || filter.isEmpty()) {
      NotificationHelper.showError("Template has no search filter");
      return;
    }
    filter = filter.replace("{SEARCH}", searchText);

    // Parse scope
    SearchScope scope = SearchScope.SUB;
    if ("base".equals(ss.getScope())) {
      scope = SearchScope.BASE;
    } else if ("one".equals(ss.getScope())) {
      scope = SearchScope.ONE;
    }

    // Get return attributes
    String[] returnAttrs = null;
    if (ss.getReturnAttributes() != null
        && !ss.getReturnAttributes().isEmpty()) {
      returnAttrs = ss.getReturnAttributes().toArray(new String[0]);
    }

    // Get selected servers
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers.isEmpty()) {
      NotificationHelper.showError(
          "Please select at least one server");
      return;
    }

    currentResults.clear();
    List<LdapServerConfig> configs =
        configService.loadConfigurations();

    String finalFilter = filter;
    SearchScope finalScope = scope;
    String[] finalReturnAttrs = returnAttrs;

    for (String serverName : selectedServers) {
      configs.stream()
          .filter(c -> c.getName().equals(serverName))
          .findFirst()
          .ifPresent(config -> {
            try {
              // Resolve base DNs from template config
              List<String> baseDns = resolveBaseDns(
                  config, ss.getBaseFilter(),
                  ss.getBaseDn());
              for (String base : baseDns) {
                List<LdapEntry> results;
                if (finalReturnAttrs != null) {
                  results = ldapService.search(config, base,
                      finalFilter, finalScope, finalReturnAttrs);
                } else {
                  results = ldapService.search(config, base,
                      finalFilter, finalScope);
                }
                currentResults.addAll(results);
              }
            } catch (Exception e) {
              NotificationHelper.showError(
                  "Search failed on " + serverName + ": "
                      + e.getMessage());
              logger.error("Template search failed on {}",
                  serverName, e);
            }
          });
    }

    // Update grid columns
    List<String> attrList = ss.getReturnAttributes() != null
        ? new ArrayList<>(ss.getReturnAttributes())
        : new ArrayList<>();
    updateGridColumns(resultsGrid, attrList);

    resultsGrid.setItems(currentResults);
    NotificationHelper.showSuccess(
        "Search complete: " + currentResults.size()
            + " entries found", 3000);
  }

  /**
   * Resolves base DNs by searching with the given filter.
   * If templateBaseDn names a key in the server's otherBases map,
   * that DN is used directly. Otherwise falls back to baseFilter
   * resolution or the server's default base DN.
   */
  private List<String> resolveBaseDns(LdapServerConfig config,
      String baseFilter, String templateBaseDn) throws Exception {
    // Check for named base from server config
    if (templateBaseDn != null && !templateBaseDn.trim().isEmpty()
        && !"Default".equalsIgnoreCase(templateBaseDn.trim())) {
      String namedDn = config.getOtherBases() != null
          ? config.getOtherBases().get(templateBaseDn.trim()) : null;
      if (namedDn != null && !namedDn.isEmpty()) {
        return List.of(namedDn);
      }
      // Named base not found on this server — warn and fall through
      logger.warn("Named base '{}' not found on server '{}'",
          templateBaseDn, config.getName());
    }

    if (baseFilter == null || baseFilter.isEmpty()) {
      // Use the server's default base DN
      String defaultBase = config.getBaseDn();
      if (defaultBase != null && !defaultBase.isEmpty()) {
        return List.of(defaultBase);
      }
      return ldapService.getNamingContexts(config);
    }
    List<String> bases = new ArrayList<>();
    List<String> namingContexts =
        ldapService.getNamingContexts(config);
    for (String nc : namingContexts) {
      List<LdapEntry> results = ldapService.search(
          config, nc, baseFilter, SearchScope.SUB, "dn");
      for (LdapEntry entry : results) {
        bases.add(entry.getDn());
      }
    }
    if (bases.isEmpty()) {
      // Fallback to default base
      String defaultBase = config.getBaseDn();
      if (defaultBase != null && !defaultBase.isEmpty()) {
        return List.of(defaultBase);
      }
    }
    return bases;
  }
}
