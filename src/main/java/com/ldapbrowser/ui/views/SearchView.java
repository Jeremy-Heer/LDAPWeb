package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.components.AdvancedSearchBuilder;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchScope;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
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
public class SearchView extends VerticalLayout {

  private static final Logger logger = LoggerFactory.getLogger(SearchView.class);

  private final ConfigurationService configService;
  private final LdapService ldapService;

  private TextField searchBaseField;
  private TextField filterField;
  private Select<SearchScope> scopeSelect;
  private Button searchButton;
  private Grid<LdapEntry> resultsGrid;
  private VerticalLayout entryDetailsPanel;
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

    // Entry details panel
    entryDetailsPanel = createEntryDetailsPanel();
    entryDetailsPanel.setVisible(false);

    splitLayout.addToPrimary(resultsLayout);
    splitLayout.addToSecondary(entryDetailsPanel);

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

  private VerticalLayout createEntryDetailsPanel() {
    VerticalLayout panel = new VerticalLayout();
    panel.setSizeFull();
    panel.setPadding(true);
    panel.setSpacing(true);
    panel.getStyle().set("background-color", "var(--lumo-contrast-5pct)");

    return panel;
  }

  private void showEntryDetails(LdapEntry entry) {
    entryDetailsPanel.removeAll();
    entryDetailsPanel.setVisible(true);

    // Header
    HorizontalLayout header = new HorizontalLayout();
    header.setWidthFull();
    header.setAlignItems(Alignment.CENTER);

    Span serverLabel = new Span("Server: " + entry.getServerName());
    serverLabel.getStyle()
        .set("font-weight", "bold")
        .set("color", "var(--lumo-primary-text-color)");

    header.add(serverLabel);

    // DN with copy button
    HorizontalLayout dnLayout = new HorizontalLayout();
    dnLayout.setWidthFull();
    dnLayout.setAlignItems(Alignment.CENTER);

    TextField dnField = new TextField("Distinguished Name");
    dnField.setValue(entry.getDn());
    dnField.setReadOnly(true);
    dnField.setWidthFull();

    Button copyDnButton = new Button(VaadinIcon.COPY.create());
    copyDnButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    copyDnButton.getElement().executeJs(
        "navigator.clipboard.writeText($0)",
        entry.getDn()
    );
    copyDnButton.addClickListener(e -> {
      Notification.show("DN copied to clipboard", 2000, Notification.Position.BOTTOM_CENTER);
    });

    dnLayout.add(dnField, copyDnButton);
    dnLayout.expand(dnField);

    // Action buttons
    HorizontalLayout actionButtons = createEntryActionButtons(entry);

    // Attributes grid
    Grid<Map.Entry<String, List<String>>> attributesGrid = new Grid<>();
    attributesGrid.setItems(entry.getAttributes().entrySet());
    attributesGrid.setHeight("400px");

    attributesGrid.addColumn(Map.Entry::getKey)
        .setHeader("Attribute")
        .setWidth("200px");

    attributesGrid.addColumn(e -> String.join(", ", e.getValue()))
        .setHeader("Values")
        .setFlexGrow(1);

    attributesGrid.addColumn(new ComponentRenderer<>(attrEntry -> {
      HorizontalLayout actions = new HorizontalLayout();

      Button editButton = new Button(VaadinIcon.EDIT.create());
      editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
      editButton.addClickListener(e ->
          showEditAttributeDialog(entry, attrEntry.getKey(), attrEntry.getValue())
      );

      Button deleteButton = new Button(VaadinIcon.TRASH.create());
      deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
      deleteButton.addClickListener(e ->
          deleteAttribute(entry, attrEntry.getKey())
      );

      actions.add(editButton, deleteButton);
      return actions;
    })).setHeader("Actions").setWidth("120px").setFlexGrow(0);

    entryDetailsPanel.add(header, dnLayout, actionButtons, new H4("Attributes"), attributesGrid);
  }

  private HorizontalLayout createEntryActionButtons(LdapEntry entry) {
    HorizontalLayout layout = new HorizontalLayout();

    Button addAttrButton = new Button("Add Attribute", VaadinIcon.PLUS.create());
    addAttrButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    addAttrButton.addClickListener(e -> showAddAttributeDialog(entry));

    Button testLoginButton = new Button("Test Login", VaadinIcon.KEY.create());
    testLoginButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    testLoginButton.addClickListener(e -> showTestLoginDialog(entry));

    Button refreshButton = new Button("Refresh", VaadinIcon.REFRESH.create());
    refreshButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    refreshButton.addClickListener(e -> refreshEntry(entry));

    Button deleteButton = new Button("Delete Entry", VaadinIcon.TRASH.create());
    deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
    deleteButton.addClickListener(e -> deleteEntry(entry));

    layout.add(addAttrButton, testLoginButton, refreshButton, deleteButton);
    return layout;
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

  private void showAddAttributeDialog(LdapEntry entry) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Add Attribute");

    TextField attrNameField = new TextField("Attribute Name");
    attrNameField.setWidthFull();

    TextField attrValueField = new TextField("Value");
    attrValueField.setWidthFull();

    Button addButton = new Button("Add", e -> {
      String attrName = attrNameField.getValue();
      String attrValue = attrValueField.getValue();

      if (attrName != null && !attrName.isEmpty() && attrValue != null && !attrValue.isEmpty()) {
        addAttribute(entry, attrName, Arrays.asList(attrValue));
        dialog.close();
      }
    });
    addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    FormLayout form = new FormLayout(attrNameField, attrValueField);
    HorizontalLayout buttons = new HorizontalLayout(addButton, cancelButton);

    dialog.add(form, buttons);
    dialog.open();
  }

  private void showEditAttributeDialog(LdapEntry entry, String attrName, List<String> values) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Edit Attribute: " + attrName);

    TextField valueField = new TextField("Value");
    valueField.setValue(String.join(", ", values));
    valueField.setWidthFull();

    Button saveButton = new Button("Save", e -> {
      String newValue = valueField.getValue();
      if (newValue != null && !newValue.isEmpty()) {
        modifyAttribute(entry, attrName, Arrays.asList(newValue.split(",\\s*")));
        dialog.close();
      }
    });
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    FormLayout form = new FormLayout(valueField);
    HorizontalLayout buttons = new HorizontalLayout(saveButton, cancelButton);

    dialog.add(form, buttons);
    dialog.open();
  }

  private void showTestLoginDialog(LdapEntry entry) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Test Login");

    TextField dnField = new TextField("DN");
    dnField.setValue(entry.getDn());
    dnField.setReadOnly(true);
    dnField.setWidthFull();

    PasswordField passwordField = new PasswordField("Password");
    passwordField.setWidthFull();

    Button testButton = new Button("Test", e -> {
      String password = passwordField.getValue();
      if (password != null && !password.isEmpty()) {
        testLogin(entry, password);
        dialog.close();
      }
    });
    testButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    FormLayout form = new FormLayout(dnField, passwordField);
    HorizontalLayout buttons = new HorizontalLayout(testButton, cancelButton);

    dialog.add(form, buttons);
    dialog.open();
  }

  private void addAttribute(LdapEntry entry, String attrName, List<String> values) {
    List<LdapServerConfig> configs = configService.loadConfigurations();
    configs.stream()
        .filter(c -> c.getName().equals(entry.getServerName()))
        .findFirst()
        .ifPresent(config -> {
          try {
            ldapService.addAttribute(config, entry.getDn(), attrName, values);
            Notification.show(
                "Attribute added successfully",
                3000,
                Notification.Position.BOTTOM_END
            ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshEntry(entry);
          } catch (LDAPException | GeneralSecurityException e) {
            Notification.show(
                "Failed to add attribute: " + e.getMessage(),
                5000,
                Notification.Position.MIDDLE
            ).addThemeVariants(NotificationVariant.LUMO_ERROR);
            logger.error("Failed to add attribute", e);
          }
        });
  }

  private void modifyAttribute(LdapEntry entry, String attrName, List<String> values) {
    List<LdapServerConfig> configs = configService.loadConfigurations();
    configs.stream()
        .filter(c -> c.getName().equals(entry.getServerName()))
        .findFirst()
        .ifPresent(config -> {
          try {
            ldapService.modifyAttribute(config, entry.getDn(), attrName, values);
            Notification.show(
                "Attribute modified successfully",
                3000,
                Notification.Position.BOTTOM_END
            ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshEntry(entry);
          } catch (LDAPException | GeneralSecurityException e) {
            Notification.show(
                "Failed to modify attribute: " + e.getMessage(),
                5000,
                Notification.Position.MIDDLE
            ).addThemeVariants(NotificationVariant.LUMO_ERROR);
            logger.error("Failed to modify attribute", e);
          }
        });
  }

  private void deleteAttribute(LdapEntry entry, String attrName) {
    List<LdapServerConfig> configs = configService.loadConfigurations();
    configs.stream()
        .filter(c -> c.getName().equals(entry.getServerName()))
        .findFirst()
        .ifPresent(config -> {
          try {
            ldapService.deleteAttribute(config, entry.getDn(), attrName);
            Notification.show(
                "Attribute deleted successfully",
                3000,
                Notification.Position.BOTTOM_END
            ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshEntry(entry);
          } catch (LDAPException | GeneralSecurityException e) {
            Notification.show(
                "Failed to delete attribute: " + e.getMessage(),
                5000,
                Notification.Position.MIDDLE
            ).addThemeVariants(NotificationVariant.LUMO_ERROR);
            logger.error("Failed to delete attribute", e);
          }
        });
  }

  private void refreshEntry(LdapEntry entry) {
    List<LdapServerConfig> configs = configService.loadConfigurations();
    configs.stream()
        .filter(c -> c.getName().equals(entry.getServerName()))
        .findFirst()
        .ifPresent(config -> {
          try {
            LdapEntry refreshed = ldapService.readEntry(config, entry.getDn(), false);
            showEntryDetails(refreshed);
            Notification.show(
                "Entry refreshed",
                2000,
                Notification.Position.BOTTOM_END
            );
          } catch (LDAPException | GeneralSecurityException e) {
            Notification.show(
                "Failed to refresh entry: " + e.getMessage(),
                5000,
                Notification.Position.MIDDLE
            ).addThemeVariants(NotificationVariant.LUMO_ERROR);
            logger.error("Failed to refresh entry", e);
          }
        });
  }

  private void deleteEntry(LdapEntry entry) {
    List<LdapServerConfig> configs = configService.loadConfigurations();
    configs.stream()
        .filter(c -> c.getName().equals(entry.getServerName()))
        .findFirst()
        .ifPresent(config -> {
          try {
            ldapService.deleteEntry(config, entry.getDn());
            Notification.show(
                "Entry deleted successfully",
                3000,
                Notification.Position.BOTTOM_END
            ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            currentResults.remove(entry);
            resultsGrid.setItems(currentResults);
            entryDetailsPanel.setVisible(false);
          } catch (LDAPException | GeneralSecurityException e) {
            Notification.show(
                "Failed to delete entry: " + e.getMessage(),
                5000,
                Notification.Position.MIDDLE
            ).addThemeVariants(NotificationVariant.LUMO_ERROR);
            logger.error("Failed to delete entry", e);
          }
        });
  }

  private void testLogin(LdapEntry entry, String password) {
    List<LdapServerConfig> configs = configService.loadConfigurations();
    configs.stream()
        .filter(c -> c.getName().equals(entry.getServerName()))
        .findFirst()
        .ifPresent(config -> {
          boolean success = ldapService.testBind(config, entry.getDn(), password);
          if (success) {
            Notification.show(
                "Login test successful",
                3000,
                Notification.Position.MIDDLE
            ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
          } else {
            Notification.show(
                "Login test failed - invalid credentials",
                3000,
                Notification.Position.MIDDLE
            ).addThemeVariants(NotificationVariant.LUMO_ERROR);
          }
        });
  }
}
