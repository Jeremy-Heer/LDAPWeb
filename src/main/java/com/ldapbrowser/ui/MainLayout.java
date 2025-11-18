package com.ldapbrowser.ui;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.dialogs.HelpDialog;
import com.ldapbrowser.ui.views.AccessView;
import com.ldapbrowser.ui.views.BrowseView;
import com.ldapbrowser.ui.views.BulkView;
import com.ldapbrowser.ui.views.Create;
import com.ldapbrowser.ui.views.ExportView;
import com.ldapbrowser.ui.views.SchemaView;
import com.ldapbrowser.ui.views.SearchView;
import com.ldapbrowser.ui.views.ServerView;
import com.ldapbrowser.ui.views.SettingsView;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main layout for LDAP Browser application.
 * Provides the navbar and drawer navigation structure.
 */
public class MainLayout extends AppLayout {

  private static final Logger logger = LoggerFactory.getLogger(MainLayout.class);
  private static final String SELECTED_SERVERS_KEY = "selectedServers";
  private final ConfigurationService configService;
  private final LdapService ldapService;
  private MultiSelectComboBox<String> serverSelect;
  private HorizontalLayout selectedServersContainer;

  /**
   * Creates the main layout with navbar and drawer.
   *
   * @param configService configuration service
   * @param ldapService LDAP service for connection management
   */
  public MainLayout(ConfigurationService configService, LdapService ldapService) {
    this.configService = configService;
    this.ldapService = ldapService;
    createHeader();
    createDrawer();
    
    // Clean up UI-scoped state on detach
    UI.getCurrent().addDetachListener(event -> {
      String uiKey = getSelectedServersKey();
      VaadinSession.getCurrent().setAttribute(uiKey, null);
    });
  }
  
  /**
   * Gets the UI-scoped key for selected servers.
   * Each browser tab gets its own UI instance and thus its own state.
   *
   * @return UI-specific key for selected servers
   */
  private String getSelectedServersKey() {
    return SELECTED_SERVERS_KEY + "_" + UI.getCurrent().getUIId();
  }

  /**
   * Creates the navbar header with title and server selector.
   */
  private void createHeader() {
    H1 title = new H1("LDAP Browser");
    title.getStyle()
        .set("font-size", "var(--lumo-font-size-l)")
        .set("margin", "0");

    // Server selector multi-select combo box
    serverSelect = new MultiSelectComboBox<>();
    serverSelect.setPlaceholder("Select servers...");
    serverSelect.setWidth("250px");
    refreshServerList();

    // Container for selected server badges
    selectedServersContainer = new HorizontalLayout();
    selectedServersContainer.setSpacing(true);
    selectedServersContainer.getStyle()
        .set("margin-left", "var(--lumo-space-m)")
        .set("flex-wrap", "wrap")
        .set("gap", "var(--lumo-space-xs)");

    // Listen for selection changes
    serverSelect.addSelectionListener(event -> {
      Set<String> previousSelected = event.getOldValue();
      Set<String> currentSelected = event.getValue();
      
      // Find servers that were deselected
      Set<String> deselected = new HashSet<>(previousSelected);
      deselected.removeAll(currentSelected);
      
      // Close connection pools for deselected servers
      for (String serverName : deselected) {
        ldapService.closeConnectionPool(serverName);
        logger.info("Closed connection pool for deselected server: {}", serverName);
      }
      
      VaadinSession.getCurrent().setAttribute(getSelectedServersKey(), new HashSet<>(currentSelected));
      updateSelectedServersDisplay();
    });

    // Help button for top right
    Button helpButton = new Button(VaadinIcon.QUESTION_CIRCLE.create());
    helpButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    helpButton.setTooltipText("Help");
    helpButton.addClickListener(event -> {
      HelpDialog helpDialog = new HelpDialog();
      helpDialog.open();
    });

    HorizontalLayout header = new HorizontalLayout(
        new DrawerToggle(),
        title,
        serverSelect,
        selectedServersContainer
    );

    header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
    header.setWidth("100%");
    header.expand(selectedServersContainer); // Make this expand to push help button to the right
    header.add(helpButton); // Add help button at the end
    header.addClassNames("py-0", "px-m");

    addToNavbar(header);
  }

  /**
   * Creates the drawer with navigation links using SideNav.
   */
  private void createDrawer() {
    SideNav sideNav = new SideNav();
    
    // Create navigation items with icons
    SideNavItem serverItem = new SideNavItem("Server", ServerView.class, 
        VaadinIcon.SERVER.create());
    SideNavItem searchItem = new SideNavItem("Search", SearchView.class, 
        VaadinIcon.SEARCH.create());
    SideNavItem browseItem = new SideNavItem("Browse", BrowseView.class, 
        VaadinIcon.TREE_TABLE.create());
    SideNavItem schemaItem = new SideNavItem("Schema", SchemaView.class, 
        VaadinIcon.DATABASE.create());
    SideNavItem createItem = new SideNavItem("Create", Create.class, 
        VaadinIcon.PLUS_CIRCLE.create());
    SideNavItem accessItem = new SideNavItem("Access", AccessView.class, 
        VaadinIcon.KEY.create());
    SideNavItem bulkItem = new SideNavItem("Bulk", BulkView.class, 
        VaadinIcon.PACKAGE.create());
    SideNavItem exportItem = new SideNavItem("Export", ExportView.class, 
        VaadinIcon.DOWNLOAD.create());
    SideNavItem settingsItem = new SideNavItem("Settings", SettingsView.class, 
        VaadinIcon.COG.create());

    // Add all items to SideNav
    sideNav.addItem(
        serverItem,
        searchItem,
        browseItem,
        schemaItem,
        createItem,
        accessItem,
        bulkItem,
        exportItem,
        settingsItem
    );

    addToDrawer(sideNav);
  }

  /**
   * Refreshes the server list from saved configurations.
   */
  public void refreshServerList() {
    List<LdapServerConfig> configs = configService.loadConfigurations();
    List<String> serverNames = configs.stream().map(LdapServerConfig::getName).toList();
    serverSelect.setItems(serverNames);

    if (serverNames.isEmpty()) {
      serverSelect.setEnabled(false);
    } else {
      serverSelect.setEnabled(true);
      // Restore previous selection for this UI/tab
      @SuppressWarnings("unchecked")
      Set<String> previousSelection = (Set<String>) VaadinSession.getCurrent()
          .getAttribute(getSelectedServersKey());
      if (previousSelection != null) {
        serverSelect.select(previousSelection.stream()
            .filter(serverNames::contains)
            .toArray(String[]::new));
      }
    }
  }

  /**
   * Updates the selected servers display with badges.
   */
  private void updateSelectedServersDisplay() {
    selectedServersContainer.removeAll();
    Set<String> selected = serverSelect.getSelectedItems();

    if (selected.isEmpty()) {
      Span emptyLabel = new Span("No servers selected");
      emptyLabel.getStyle()
          .set("font-size", "var(--lumo-font-size-s)")
          .set("color", "var(--lumo-secondary-text-color)");
      selectedServersContainer.add(emptyLabel);
    } else {
      // Create badge for each selected server
      for (String serverName : selected) {
        Span badge = createServerBadge(serverName);
        selectedServersContainer.add(badge);
      }
    }
  }

  /**
   * Creates a badge component for a selected server.
   *
   * @param serverName server name
   * @return badge component
   */
  private Span createServerBadge(String serverName) {
    // Server name label
    Span nameLabel = new Span(serverName);
    nameLabel.getStyle().set("margin-right", "var(--lumo-space-xs)");

    // Remove button
    Button removeButton = new Button(VaadinIcon.CLOSE_SMALL.create());
    removeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
    removeButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
    removeButton.getStyle()
        .set("margin", "0")
        .set("padding", "0")
        .set("min-width", "0");
    removeButton.addClickListener(event -> {
      Set<String> current = new HashSet<>(serverSelect.getSelectedItems());
      current.remove(serverName);
      serverSelect.setValue(current);
    });

    // Create and configure badge
    Span badge = new Span(nameLabel, removeButton);
    badge.getStyle()
        .set("display", "inline-flex")
        .set("align-items", "center")
        .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
        .set("background-color", "var(--lumo-primary-color-10pct)")
        .set("color", "var(--lumo-primary-text-color)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("font-size", "var(--lumo-font-size-s)");

    return badge;
  }

  /**
   * Gets the currently selected server names for this UI/tab.
   * Each browser tab maintains its own independent server selection.
   *
   * @return set of selected server names
   */
  public static Set<String> getSelectedServers() {
    String uiKey = SELECTED_SERVERS_KEY + "_" + UI.getCurrent().getUIId();
    @SuppressWarnings("unchecked")
    Set<String> selected = (Set<String>) VaadinSession.getCurrent()
        .getAttribute(uiKey);
    return selected != null ? selected : new HashSet<>();
  }
}
