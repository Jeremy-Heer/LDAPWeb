package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.components.AdvancedSearchBuilder;
import com.ldapbrowser.ui.components.EntryEditor;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchScope;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Search view for performing LDAP searches.
 * Provides search filters and displays results.
 */
@Route(value = "search", layout = MainLayout.class)
@PageTitle("Search | LDAP Browser")
public class SearchView extends VerticalLayout {

  private static final Logger logger = LoggerFactory.getLogger(SearchView.class);

  private final ConfigurationService configService;
  private final LdapService ldapService;

  private TextField searchBaseField;
  private TextField filterField;
  private Select<SearchScope> scopeSelect;
  private Button searchButton;
  private Grid<LdapEntry> resultsGrid;
  private EntryEditor entryEditor;
  private AdvancedSearchBuilder filterBuilderPanel;
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

    splitLayout.addToPrimary(resultsLayout);
    splitLayout.addToSecondary(entryEditor);

    add(title, searchForm, splitLayout);
    expand(splitLayout);
  }

  private VerticalLayout createSearchForm() {
    VerticalLayout formLayout = new VerticalLayout();
    formLayout.setPadding(true);
    formLayout.setSpacing(true);

    // Search base with browse button
    HorizontalLayout searchBaseLayout = new HorizontalLayout();
    searchBaseLayout.setWidthFull();
    searchBaseLayout.setAlignItems(Alignment.END);

    searchBaseField = new TextField("Search Base");
    searchBaseField.setWidthFull();
    searchBaseField.setPlaceholder("dc=example,dc=com");

    Button browseButton = new Button("Browse", VaadinIcon.FOLDER_OPEN.create());
    browseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    browseButton.addClickListener(e -> showBrowseDialog());

    searchBaseLayout.add(searchBaseField, browseButton);
    searchBaseLayout.expand(searchBaseField);

    // Filter field
    filterField = new TextField("Filter");
    filterField.setWidthFull();
    filterField.setPlaceholder("(objectClass=*)");

    // Scope selector
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

    // Search button
    searchButton = new Button("Search", VaadinIcon.SEARCH.create());
    searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    searchButton.addClickListener(e -> performSearch());

    // Filter builder button
    Button filterBuilderButton = new Button("Filter Builder", VaadinIcon.FILTER.create());
    filterBuilderButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    filterBuilderButton.addClickListener(e -> toggleFilterBuilder());

    HorizontalLayout buttonLayout = new HorizontalLayout(searchButton, filterBuilderButton);

    FormLayout searchFields = new FormLayout();
    searchFields.setWidthFull();
    searchFields.add(searchBaseLayout, filterField, scopeSelect);
    searchFields.setColspan(searchBaseLayout, 2);

    // Advanced filter builder panel (initially hidden)
    filterBuilderPanel = createFilterBuilderPanel();
    filterBuilderPanel.setVisible(false);

    formLayout.add(searchFields, buttonLayout, filterBuilderPanel);
    return formLayout;
  }

  private AdvancedSearchBuilder createFilterBuilderPanel() {
    AdvancedSearchBuilder builder = new AdvancedSearchBuilder(ldapService);
    builder.getStyle()
        .set("border", "1px solid var(--lumo-contrast-20pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("background-color", "var(--lumo-contrast-5pct)")
        .set("padding", "var(--lumo-space-m)");
    
    // Sync the search base with the main search form
    searchBaseField.addValueChangeListener(e -> {
      if (e.getValue() != null && !e.getValue().isEmpty()) {
        builder.setSearchBase(e.getValue());
      }
    });
    
    return builder;
  }

  private Grid<LdapEntry> createResultsGrid() {
    Grid<LdapEntry> grid = new Grid<>();
    grid.setSizeFull();

    grid.addColumn(LdapEntry::getServerName)
        .setHeader("Server")
        .setWidth("150px")
        .setFlexGrow(0);

    grid.addColumn(LdapEntry::getDn)
        .setHeader("Distinguished Name")
        .setFlexGrow(1);

    grid.addColumn(entry -> {
      String cn = entry.getFirstAttributeValue("cn");
      return cn != null ? cn : "";
    }).setHeader("Common Name").setWidth("200px");

    grid.addColumn(entry -> {
      List<String> objectClasses = entry.getAttributeValues("objectClass");
      return objectClasses.isEmpty() ? "" : String.join(", ", objectClasses);
    }).setHeader("Object Classes").setWidth("250px");

    grid.addSelectionListener(selection -> {
      selection.getFirstSelectedItem().ifPresent(this::showEntryDetails);
    });

    return grid;
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

    currentResults.clear();
    List<LdapServerConfig> configs = configService.loadConfigurations();

    String finalFilter = filter;
    for (String serverName : selectedServers) {
      configs.stream()
          .filter(c -> c.getName().equals(serverName))
          .findFirst()
          .ifPresent(config -> {
            try {
              List<LdapEntry> results = ldapService.search(config, baseDn, finalFilter, scope);
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

    // Create dialog with tree browser
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Select DN");
    dialog.setWidth("800px");
    dialog.setHeight("600px");
    dialog.setModal(true);
    dialog.setCloseOnOutsideClick(false);

    // Create the tree browser component
    com.ldapbrowser.ui.components.LdapTreeBrowser treeBrowser = 
        new com.ldapbrowser.ui.components.LdapTreeBrowser(ldapService);

    // Load the tree with all selected server configurations
    try {
      treeBrowser.setServerConfigs(selectedConfigs);
      treeBrowser.loadServers();
    } catch (Exception ex) {
      logger.error("Failed to load tree for DN selector", ex);
      Notification notification = Notification.show(
          "Failed to load LDAP tree: " + ex.getMessage(),
          5000,
          Notification.Position.MIDDLE);
      notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
      dialog.close();
      return;
    }

    // Handle entry selection - only allow actual LDAP entries (not server nodes or Root DSE)
    treeBrowser.addSelectionListener(event -> {
      LdapEntry selectedEntry = event.getSelectedEntry();
      if (selectedEntry != null && isValidDnForSearch(selectedEntry)) {
        searchBaseField.setValue(selectedEntry.getDn());
        dialog.close();
      }
    });

    // Add buttons
    Button selectButton = new Button("Select", e -> {
      // Get the selected entry from the tree
      LdapEntry selectedEntry = treeBrowser.getSelectedEntry();
      if (selectedEntry != null && isValidDnForSearch(selectedEntry)) {
        searchBaseField.setValue(selectedEntry.getDn());
        dialog.close();
      } else if (selectedEntry != null) {
        Notification.show("Please select a valid DN (not a server or Root DSE)",
            3000, Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_ERROR);
      }
    });
    selectButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    HorizontalLayout buttons = new HorizontalLayout(selectButton, cancelButton);
    buttons.setJustifyContentMode(JustifyContentMode.END);
    buttons.setPadding(true);

    VerticalLayout dialogLayout = new VerticalLayout(treeBrowser, buttons);
    dialogLayout.setSizeFull();
    dialogLayout.setPadding(false);
    dialogLayout.setSpacing(false);
    dialogLayout.expand(treeBrowser);

    dialog.add(dialogLayout);
    dialog.open();
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

  private void toggleFilterBuilder() {
    filterBuilderPanel.setVisible(!filterBuilderPanel.isVisible());
    
    // If showing the builder, populate it with current search base
    if (filterBuilderPanel.isVisible()) {
      String currentBase = searchBaseField.getValue();
      if (currentBase != null && !currentBase.isEmpty()) {
        filterBuilderPanel.setSearchBase(currentBase);
      }
      
      // If there's a filter in the builder, use it
      String generatedFilter = filterBuilderPanel.getGeneratedFilter();
      if (generatedFilter != null && !generatedFilter.isEmpty()) {
        filterField.setValue(generatedFilter);
      }
    } else {
      // When hiding, update the main filter with generated filter if available
      String generatedFilter = filterBuilderPanel.getGeneratedFilter();
      if (generatedFilter != null && !generatedFilter.isEmpty()) {
        filterField.setValue(generatedFilter);
      }
    }
  }
}
