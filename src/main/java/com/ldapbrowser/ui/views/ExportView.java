package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.components.ExportTab;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Export view.
 * Handles exporting LDAP data in various formats.
 */
@Route(value = "export", layout = MainLayout.class)
@PageTitle("Export | LDAP Browser")
public class ExportView extends VerticalLayout {

  private final ConfigurationService configurationService;
  private ExportTab exportTab;

  /**
   * Creates the Export view.
   *
   * @param ldapService LDAP service
   * @param loggingService logging service
   * @param configurationService configuration service
   */
  public ExportView(
      LdapService ldapService,
      LoggingService loggingService,
      ConfigurationService configurationService
  ) {
    this.configurationService = configurationService;

    setSizeFull();
    setPadding(false);
    setSpacing(false);

    // Create export tab with services
    exportTab = new ExportTab(ldapService, loggingService, configurationService);
    exportTab.setSizeFull();

    // Update selected servers from MainLayout
    updateSelectedServers();

    add(exportTab);
  }

  /**
   * Updates the export tab with currently selected servers from MainLayout.
   */
  private void updateSelectedServers() {
    Set<String> selectedServerNames = MainLayout.getSelectedServers();
    
    if (selectedServerNames.isEmpty()) {
      // No servers selected, clear the export tab
      exportTab.clear();
      return;
    }

    // Load server configurations
    List<LdapServerConfig> allConfigs = configurationService.loadConfigurations();
    Set<LdapServerConfig> selectedConfigs = new HashSet<>();

    for (String serverName : selectedServerNames) {
      allConfigs.stream()
          .filter(config -> config.getName().equals(serverName))
          .findFirst()
          .ifPresent(selectedConfigs::add);
    }

    // Set the servers in the export tab
    if (selectedConfigs.size() == 1) {
      exportTab.setServerConfig(selectedConfigs.iterator().next());
    } else if (selectedConfigs.size() > 1) {
      exportTab.setGroupServers(selectedConfigs);
    }
  }
}
