package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.components.EntryEditor;
import com.ldapbrowser.ui.components.LdapTreeBrowser;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.List;
import java.util.Set;

/**
 * Browse view for navigating LDAP directory tree.
 * Displays tree navigator on the left and entry details on the right.
 */
@Route(value = "browse", layout = MainLayout.class)
@PageTitle("Browse | LDAP Browser")
public class BrowseView extends VerticalLayout implements BeforeEnterObserver {

  private final LdapService ldapService;
  private final ConfigurationService configService;

  private LdapTreeBrowser treeBrowser;
  private EntryEditor entryEditor;
  private SplitLayout splitLayout;
  private Set<String> lastLoadedServers;

  /**
   * Creates the Browse view.
   *
   * @param ldapService LDAP service
   * @param configService configuration service
   */
  public BrowseView(LdapService ldapService, ConfigurationService configService) {
    this.ldapService = ldapService;
    this.configService = configService;

    setSizeFull();
    setPadding(false);
    setSpacing(false);

    initializeComponents();
    setupLayout();
  }

  @Override
  protected void onAttach(AttachEvent attachEvent) {
    super.onAttach(attachEvent);
    
    // Start polling for server selection changes every second
    UI ui = attachEvent.getUI();
    ui.setPollInterval(1000); // Poll every second
    
    ui.addPollListener(pollEvent -> {
      checkServerSelectionChanges();
    });
  }

  @Override
  protected void onDetach(DetachEvent detachEvent) {
    super.onDetach(detachEvent);
    
    // Stop polling when view is detached
    UI ui = detachEvent.getUI();
    ui.setPollInterval(-1); // Disable polling
  }

  private void checkServerSelectionChanges() {
    Set<String> currentServers = MainLayout.getSelectedServers();
    if (lastLoadedServers == null || !lastLoadedServers.equals(currentServers)) {
      getUI().ifPresent(ui -> ui.access(() -> {
        loadTree();
        lastLoadedServers = currentServers != null ? Set.copyOf(currentServers) : Set.of();
      }));
    }
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    // Reload tree if server selection has changed
    checkServerSelectionChanges();
  }

  private void initializeComponents() {
    // Create tree browser
    treeBrowser = new LdapTreeBrowser(ldapService);
    treeBrowser.addSelectionListener(event -> {
      LdapEntry selectedEntry = event.getSelectedEntry();
      onEntrySelected(selectedEntry);
    });

    // Create attribute editor
    entryEditor = new EntryEditor(ldapService, configService);
  }

  private void setupLayout() {
    splitLayout = new SplitLayout();
    splitLayout.setSizeFull();
    splitLayout.setSplitterPosition(25); // 25% for tree, 75% for details

    splitLayout.addToPrimary(treeBrowser);
    splitLayout.addToSecondary(entryEditor);

    add(splitLayout);
    setFlexGrow(1, splitLayout);
  }

  private void loadTree() {
    // Get selected servers
    Set<String> selectedServers = MainLayout.getSelectedServers();

    if (selectedServers.isEmpty()) {
      showNoServersSelected();
      return;
    }

    // Restore the normal layout (in case the "no servers" message was shown)
    restoreNormalLayout();

    // Load all selected servers
    List<LdapServerConfig> configs = configService.loadConfigurations();
    List<LdapServerConfig> selectedConfigs = configs.stream()
        .filter(c -> selectedServers.contains(c.getName()))
        .collect(java.util.stream.Collectors.toList());

    if (!selectedConfigs.isEmpty()) {
      treeBrowser.setServerConfigs(selectedConfigs);
      treeBrowser.loadServers();
    } else {
      showServerNotFound("No matching server configurations found");
    }
  }

  private void onEntrySelected(LdapEntry entry) {
    if (entry == null) {
      entryEditor.clear();
      return;
    }

    // Don't show details for server nodes
    if (entry.getDn().startsWith("SERVER:")) {
      entryEditor.clear();
      return;
    }

    try {
      // Find which server this entry belongs to by checking the DN or walking the tree
      Set<String> selectedServers = MainLayout.getSelectedServers();
      List<LdapServerConfig> configs = configService.loadConfigurations();
      
      // Try each selected server config to read the entry
      LdapEntry fullEntry = null;
      for (String serverName : selectedServers) {
        LdapServerConfig config = configs.stream()
            .filter(c -> c.getName().equals(serverName))
            .findFirst()
            .orElse(null);
        
        if (config != null) {
          try {
            // Try to read the entry with this config
            fullEntry = ldapService.readEntry(config, entry.getDn(), false);
            if (fullEntry != null) {
              entryEditor.setServerConfig(config);
              entryEditor.editEntry(fullEntry);
              return;
            }
          } catch (Exception ignored) {
            // Try next server
          }
        }
      }
      
      // If we couldn't read from any server, show basic entry info
      if (fullEntry == null) {
        entryEditor.editEntry(entry);
      }
    } catch (Exception e) {
      Notification.show("Failed to load entry details: " + e.getMessage(),
          3000, Notification.Position.BOTTOM_END)
          .addThemeVariants(NotificationVariant.LUMO_ERROR);
      // Show basic entry info
      entryEditor.editEntry(entry);
    }
  }

  private void showNoServersSelected() {
    // Remove all components from split layout
    splitLayout.removeAll();
    
    // Create and add the "no servers" message to the left (primary)
    Span message = new Span("No servers selected. Please select one or more servers "
        + "from the navbar to browse the LDAP directory.");
    message.getStyle()
        .set("padding", "var(--lumo-space-l)")
        .set("text-align", "center")
        .set("color", "var(--lumo-secondary-text-color)");

    splitLayout.addToPrimary(message);
    // Keep attribute editor on the right (secondary)
    splitLayout.addToSecondary(entryEditor);
  }

  private void restoreNormalLayout() {
    // Remove all components from split layout
    splitLayout.removeAll();
    
    // Re-add in correct order: tree browser on left (primary), attribute editor on right (secondary)
    splitLayout.addToPrimary(treeBrowser);
    splitLayout.addToSecondary(entryEditor);
  }

  private void showServerNotFound(String serverName) {
    Notification.show("Server not found: " + serverName,
        3000, Notification.Position.BOTTOM_END)
        .addThemeVariants(NotificationVariant.LUMO_ERROR);
  }
}
