package com.ldapbrowser.ui;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.ui.views.AccessView;
import com.ldapbrowser.ui.views.BrowseView;
import com.ldapbrowser.ui.views.BulkView;
import com.ldapbrowser.ui.views.ExportView;
import com.ldapbrowser.ui.views.SchemaView;
import com.ldapbrowser.ui.views.SearchView;
import com.ldapbrowser.ui.views.ServerView;
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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main layout for LDAP Browser application.
 * Provides the navbar and drawer navigation structure.
 */
public class MainLayout extends AppLayout {

  private static final String SELECTED_SERVERS_KEY = "selectedServers";
  private final ConfigurationService configService;
  private MultiSelectComboBox<String> serverSelect;
  private HorizontalLayout selectedServersContainer;

  /**
   * Creates the main layout with navbar and drawer.
   *
   * @param configService configuration service
   */
  public MainLayout(ConfigurationService configService) {
    this.configService = configService;
    createHeader();
    createDrawer();
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
      Set<String> selected = event.getValue();
      VaadinSession.getCurrent().setAttribute(SELECTED_SERVERS_KEY, new HashSet<>(selected));
      updateSelectedServersDisplay();
    });

    HorizontalLayout header = new HorizontalLayout(
        new DrawerToggle(),
        title,
        serverSelect,
        selectedServersContainer
    );

    header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
    header.setWidth("100%");
    header.addClassNames("py-0", "px-m");

    addToNavbar(header);
  }

  /**
   * Creates the drawer with navigation links.
   */
  private void createDrawer() {
    VerticalLayout drawerLayout = new VerticalLayout();
    drawerLayout.setPadding(true);
    drawerLayout.setSpacing(true);

    // Navigation links
    RouterLink serverLink = new RouterLink("Server", ServerView.class);
    RouterLink searchLink = new RouterLink("Search", SearchView.class);
    RouterLink browseLink = new RouterLink("Browse", BrowseView.class);
    RouterLink schemaLink = new RouterLink("Schema", SchemaView.class);
    RouterLink accessLink = new RouterLink("Access", AccessView.class);
    RouterLink bulkLink = new RouterLink("Bulk", BulkView.class);
    RouterLink exportLink = new RouterLink("Export", ExportView.class);

    // Add all links to drawer
    drawerLayout.add(
        serverLink,
        searchLink,
        browseLink,
        schemaLink,
        accessLink,
        bulkLink,
        exportLink
    );

    addToDrawer(drawerLayout);
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
      // Restore previous selection
      @SuppressWarnings("unchecked")
      Set<String> previousSelection = (Set<String>) VaadinSession.getCurrent()
          .getAttribute(SELECTED_SERVERS_KEY);
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
   * Gets the currently selected server names.
   *
   * @return set of selected server names
   */
  public static Set<String> getSelectedServers() {
    @SuppressWarnings("unchecked")
    Set<String> selected = (Set<String>) VaadinSession.getCurrent()
        .getAttribute(SELECTED_SERVERS_KEY);
    return selected != null ? selected : new HashSet<>();
  }
}
