package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.components.GlobalAccessControlTab;
import com.ldapbrowser.ui.components.EntryAccessControlTab;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Access control view.
 * Manages LDAP access control and permissions.
 */
@Route(value = "access", layout = MainLayout.class)
@PageTitle("Access | LDAP Browser")
public class AccessView extends VerticalLayout {

  private final GlobalAccessControlTab globalAccessControlTab;
  private final EntryAccessControlTab entryAccessControlTab;
  private final Tabs topLevelTabs;
  private final VerticalLayout contentContainer;
  private final ConfigurationService configService;

  /**
   * Creates the Access view.
   *
   * @param ldapService LDAP service
   * @param configService configuration service
   */
  public AccessView(LdapService ldapService, ConfigurationService configService) {
    this.configService = configService;
    
    setSizeFull();
    setPadding(false);
    setSpacing(false);

    // Create top-level tabs
    topLevelTabs = new Tabs();
    Tab globalAccessControlTabItem = new Tab("Global Access Control");
    Tab entryAccessControlTabItem = new Tab("Entry Access Control");
    // Placeholder tabs for future implementation
    Tab effectiveRightsTabItem = new Tab("Effective Rights");
    Tab aciEditorTabItem = new Tab("ACI Editor");
    
    topLevelTabs.add(globalAccessControlTabItem, entryAccessControlTabItem, 
        effectiveRightsTabItem, aciEditorTabItem);
    
    // Disable future tabs
    effectiveRightsTabItem.setEnabled(false);
    aciEditorTabItem.setEnabled(false);

    // Create tab content components
    globalAccessControlTab = new GlobalAccessControlTab(ldapService);
    entryAccessControlTab = new EntryAccessControlTab(ldapService);

    // Content container
    contentContainer = new VerticalLayout();
    contentContainer.setSizeFull();
    contentContainer.setPadding(false);
    contentContainer.setSpacing(false);

    // Show global access control tab by default
    contentContainer.add(globalAccessControlTab);

    // Tab selection listener
    topLevelTabs.addSelectedChangeListener(event -> {
      Tab selectedTab = event.getSelectedTab();
      contentContainer.removeAll();

      if (selectedTab == globalAccessControlTabItem) {
        contentContainer.add(globalAccessControlTab);
        // Load ACIs when tab is selected
        updateGlobalAccessControlTabServers();
      } else if (selectedTab == entryAccessControlTabItem) {
        contentContainer.add(entryAccessControlTab);
        // Load ACIs when tab is selected
        updateEntryAccessControlTabServers();
      }
      // Future tabs will be handled here
    });

    add(topLevelTabs, contentContainer);
    setFlexGrow(1, contentContainer);

    // Load ACIs on initial view
    updateGlobalAccessControlTabServers();
  }

  /**
   * Updates the global access control tab with currently selected servers from main layout.
   */
  private void updateGlobalAccessControlTabServers() {
    Set<LdapServerConfig> selectedServers = getSelectedServers();
    globalAccessControlTab.setSelectedServers(selectedServers);
  }

  /**
   * Updates the entry access control tab with currently selected servers from main layout.
   */
  private void updateEntryAccessControlTabServers() {
    Set<LdapServerConfig> selectedServers = getSelectedServers();
    entryAccessControlTab.setSelectedServers(selectedServers);
    entryAccessControlTab.loadData();
  }

  /**
   * Gets the currently selected servers from the main layout.
   *
   * @return set of selected server configurations
   */
  private Set<LdapServerConfig> getSelectedServers() {
    Set<String> selectedServerNames = MainLayout.getSelectedServers();
    Set<LdapServerConfig> selectedServers = new HashSet<>();
    
    // Load all configurations and filter by selected names
    List<LdapServerConfig> allConfigs = configService.loadConfigurations();
    for (LdapServerConfig config : allConfigs) {
      if (selectedServerNames.contains(config.getName())) {
        selectedServers.add(config);
      }
    }
    
    return selectedServers;
  }
}
