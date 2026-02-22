package com.ldapbrowser.ui;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.ui.components.LogsDrawer;
import com.ldapbrowser.ui.dialogs.HelpDialog;
import com.ldapbrowser.ui.utils.NotificationHelper;
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
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
  private final LoggingService loggingService;
  private MultiSelectComboBox<String> serverSelect;
  private HorizontalLayout selectedServersContainer;
  private Dialog logsDialog;
  private Button logsButton;
  private int unreadLogCount = 0;
  private Map<String, LdapServerConfig> serverConfigMap = new HashMap<>();

  /**
   * Creates the main layout with navbar and drawer.
   *
   * @param configService configuration service
   * @param ldapService LDAP service for connection management
   * @param loggingService logging service for activity logs
   */
  public MainLayout(ConfigurationService configService, LdapService ldapService, 
      LoggingService loggingService) {
    this.configService = configService;
    this.ldapService = ldapService;
    this.loggingService = loggingService;
    
    // Set the logging service for NotificationHelper to use
    NotificationHelper.setLoggingService(loggingService);
    
    createHeader();
    createDrawer();
    
    // Register for log updates to show badge count
    loggingService.addListener(entry -> {
      getUI().ifPresent(ui -> ui.access(() -> {
        unreadLogCount++;
        updateLogsBadge();
      }));
    });
    
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
      
      // Notify current view if it needs to update
      notifyCurrentViewOfServerChange();
    });

    // Logs button with bell icon and badge
    logsButton = new Button(VaadinIcon.BELL.create());
    logsButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    logsButton.setTooltipText("Activity Logs");
    logsButton.getStyle().set("position", "relative");
    logsButton.addClickListener(event -> {
      if (logsDialog == null) {
        createLogsDialog();
      }
      logsDialog.open();
      // Reset unread count when dialog is opened
      unreadLogCount = 0;
      updateLogsBadge();
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
    header.expand(selectedServersContainer); // Make this expand to push buttons to the right
    header.add(logsButton, helpButton); // Add buttons at the end
    header.addClassNames("py-0", "px-m");

    addToNavbar(header);
  }

  /**
   * Creates the logs dialog with activity logs drawer.
   */
  private void createLogsDialog() {
    logsDialog = new Dialog();
    logsDialog.setHeaderTitle("Activity Logs");
    logsDialog.setModal(false);
    logsDialog.setDraggable(true);
    logsDialog.setResizable(true);
    
    // Set dialog size
    logsDialog.setWidth("600px");
    logsDialog.setHeight("800px");
    
    LogsDrawer logsDrawer = new LogsDrawer(loggingService);
    logsDialog.add(logsDrawer);
    
    // Refresh logs when dialog opens
    logsDialog.addOpenedChangeListener(event -> {
      if (event.isOpened()) {
        logsDrawer.refresh();
      }
    });
    
    Button closeButton = new Button("Close", e -> logsDialog.close());
    closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    logsDialog.getFooter().add(closeButton);
  }

  /**
   * Creates the drawer with navigation links using SideNav.
   */
  private void createDrawer() {
    SideNav sideNav = new SideNav();
    
    // Create navigation items with colored icons
    com.vaadin.flow.component.icon.Icon serverIcon = VaadinIcon.SERVER.create();
    serverIcon.getStyle().set("color", "#2196F3");
    SideNavItem serverItem = new SideNavItem("Server", ServerView.class, serverIcon);
    Tooltip.forComponent(serverItem).setText("Manage Server Connections");

    com.vaadin.flow.component.icon.Icon searchIcon = VaadinIcon.SEARCH.create();
    searchIcon.getStyle().set("color", "#FF9800");
    SideNavItem searchItem = new SideNavItem("Search", SearchView.class, searchIcon);
    Tooltip.forComponent(searchItem).setText("Search LDAP");

    com.vaadin.flow.component.icon.Icon browseIcon = VaadinIcon.TREE_TABLE.create();
    browseIcon.getStyle().set("color", "#4CAF50");
    SideNavItem browseItem = new SideNavItem("Browse", BrowseView.class, browseIcon);
    Tooltip.forComponent(browseItem).setText("Browse the LDAP Tree");

    com.vaadin.flow.component.icon.Icon schemaIcon = VaadinIcon.DATABASE.create();
    schemaIcon.getStyle().set("color", "#9C27B0");
    SideNavItem schemaItem = new SideNavItem("Schema", SchemaView.class, schemaIcon);
    Tooltip.forComponent(schemaItem).setText("Manage LDAP Schema");

    com.vaadin.flow.component.icon.Icon createIcon = VaadinIcon.PLUS_CIRCLE.create();
    createIcon.getStyle().set("color", "#66BB6A");
    SideNavItem createItem = new SideNavItem("Create", Create.class, createIcon);
    Tooltip.forComponent(createItem).setText("Create LDAP Entries");

    com.vaadin.flow.component.icon.Icon accessIcon = VaadinIcon.KEY.create();
    accessIcon.getStyle().set("color", "#FFC107");
    SideNavItem accessItem = new SideNavItem("Access", AccessView.class, accessIcon);
    Tooltip.forComponent(accessItem).setText("Manage ACIs");

    com.vaadin.flow.component.icon.Icon bulkIcon = VaadinIcon.PACKAGE.create();
    bulkIcon.getStyle().set("color", "#FF5722");
    SideNavItem bulkItem = new SideNavItem("Bulk", BulkView.class, bulkIcon);
    Tooltip.forComponent(bulkItem).setText("Perform bulk modify operations");

    com.vaadin.flow.component.icon.Icon exportIcon = VaadinIcon.DOWNLOAD.create();
    exportIcon.getStyle().set("color", "#009688");
    SideNavItem exportItem = new SideNavItem("Export", ExportView.class, exportIcon);
    Tooltip.forComponent(exportItem).setText("Export LDAP data to file");

    com.vaadin.flow.component.icon.Icon settingsIcon = VaadinIcon.COG.create();
    settingsIcon.getStyle().set("color", "#607D8B");
    SideNavItem settingsItem = new SideNavItem("Settings", SettingsView.class, settingsIcon);
    Tooltip.forComponent(settingsItem).setText("TLS and encryption");

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
    
    // Store mapping for tooltips and details
    serverConfigMap.clear();
    for (LdapServerConfig config : configs) {
      serverConfigMap.put(config.getName(), config);
    }
    
    serverSelect.setItems(serverNames);
    
    // Set renderer to show server details in dropdown
    serverSelect.setRenderer(new ComponentRenderer<>(serverName -> {
      Div container = new Div();
      Span nameSpan = new Span(serverName);
      nameSpan.getStyle().set("font-weight", "500");
      
      LdapServerConfig config = serverConfigMap.get(serverName);
      if (config != null) {
        String details = config.getHost() + ":" + config.getPort();
        if (config.getBindDn() != null && !config.getBindDn().isEmpty()) {
          details += " (" + config.getBindDn() + ")";
        }
        Span detailSpan = new Span(details);
        detailSpan.getStyle()
            .set("font-size", "var(--lumo-font-size-s)")
            .set("color", "var(--lumo-secondary-text-color)");
        container.add(nameSpan, new Div(detailSpan));
      } else {
        container.add(nameSpan);
      }
      
      return container;
    }));

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
    
    // Add tooltip with server details
    LdapServerConfig config = serverConfigMap.get(serverName);
    if (config != null) {
      String tooltipText = config.getHost() + ":" + config.getPort();
      if (config.getBindDn() != null && !config.getBindDn().isEmpty()) {
        tooltipText += "\nBind DN: " + config.getBindDn();
      }
      Tooltip.forComponent(badge).setText(tooltipText);
    }

    return badge;
  }

  /**
   * Notifies the current view that server selection has changed.
   * This allows views like BulkView to update their tabs immediately.
   */
  private void notifyCurrentViewOfServerChange() {
    com.vaadin.flow.component.Component content = getContent();
    if (content instanceof BulkView) {
      ((BulkView) content).updateTabServers();
      logger.debug("Notified BulkView of server selection change");
    }
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

  /**
   * Updates the badge on the logs button to show unread count.
   */
  private void updateLogsBadge() {
    if (unreadLogCount > 0) {
      logsButton.getElement().setAttribute("badge", String.valueOf(unreadLogCount));
      logsButton.getElement().getThemeList().add("badge");
    } else {
      logsButton.getElement().removeAttribute("badge");
      logsButton.getElement().getThemeList().remove("badge");
    }
  }
}
