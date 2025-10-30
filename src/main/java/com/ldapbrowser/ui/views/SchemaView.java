package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.components.SchemaCompareTab;
import com.ldapbrowser.ui.components.SchemaManageTab;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.HashSet;
import java.util.Set;

/**
 * Schema view for exploring and managing LDAP schema.
 * Displays object classes, attribute types, and other schema elements.
 */
@Route(value = "schema", layout = MainLayout.class)
@PageTitle("Schema | LDAP Browser")
public class SchemaView extends VerticalLayout {

  private final SchemaManageTab manageTab;
  private final SchemaCompareTab compareTab;
  private final Tabs topLevelTabs;
  private final VerticalLayout contentContainer;
  private final ConfigurationService configService;

  /**
   * Creates the Schema view.
   *
   * @param ldapService LDAP service
   * @param configService configuration service
   * @param loggingService logging service
   */
  public SchemaView(LdapService ldapService, ConfigurationService configService,
      LoggingService loggingService) {
    this.configService = configService;
    
    setSizeFull();
    setPadding(false);
    setSpacing(false);

    // Create top-level tabs
    topLevelTabs = new Tabs();
    Tab manageTabItem = new Tab("Manage");
    Tab compareTabItem = new Tab("Compare");
    topLevelTabs.add(manageTabItem, compareTabItem);

    // Create tab content components
    manageTab = new SchemaManageTab(ldapService, configService);
    compareTab = new SchemaCompareTab(ldapService, loggingService);

    // Content container
    contentContainer = new VerticalLayout();
    contentContainer.setSizeFull();
    contentContainer.setPadding(false);
    contentContainer.setSpacing(false);

    // Show manage tab by default
    contentContainer.add(manageTab);

    // Tab selection listener
    topLevelTabs.addSelectedChangeListener(event -> {
      Tab selectedTab = event.getSelectedTab();
      contentContainer.removeAll();

      if (selectedTab == manageTabItem) {
        contentContainer.add(manageTab);
        // Load schemas when manage tab is selected
        manageTab.loadSchemas();
      } else if (selectedTab == compareTabItem) {
        contentContainer.add(compareTab);
        // Update compare tab with selected servers
        updateCompareTabServers();
      }
    });

    add(topLevelTabs, contentContainer);
    setFlexGrow(1, contentContainer);

    // Load schemas on initial view
    manageTab.loadSchemas();
  }

  /**
   * Updates the compare tab with currently selected servers from main layout.
   */
  private void updateCompareTabServers() {
    Set<LdapServerConfig> selectedServers = getSelectedServers();
    compareTab.setEnvironments(selectedServers);
  }

  /**
   * Gets the currently selected servers from the main layout.
   *
   * @return set of selected server configurations
   */
  private Set<LdapServerConfig> getSelectedServers() {
    Set<String> selectedNames = MainLayout.getSelectedServers();
    Set<LdapServerConfig> selectedConfigs = new HashSet<>();
    
    // Get all configurations and filter by selected names
    for (LdapServerConfig config : configService.loadConfigurations()) {
      if (selectedNames.contains(config.getName())) {
        selectedConfigs.add(config);
      }
    }
    
    return selectedConfigs;
  }
}
