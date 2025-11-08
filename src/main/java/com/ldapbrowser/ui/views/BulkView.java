package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.components.ImportTab;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Bulk operations view.
 * Allows performing bulk LDAP operations.
 */
@Route(value = "bulk", layout = MainLayout.class)
@PageTitle("Bulk | LDAP Browser")
public class BulkView extends VerticalLayout {

  private final ConfigurationService configService;
  private final ImportTab importTab;

  /**
   * Creates the Bulk view.
   *
   * @param configService the configuration service
   * @param ldapService the LDAP service
   * @param loggingService the logging service
   */
  public BulkView(ConfigurationService configService, LdapService ldapService, 
      LoggingService loggingService) {
    this.configService = configService;
    this.importTab = new ImportTab(ldapService, loggingService);

    setSpacing(true);
    setPadding(true);

    H2 title = new H2("Bulk Operations");
    add(title);

    // Create the tab sheet
    TabSheet tabSheet = new TabSheet();
    tabSheet.setSizeFull();

    // Add Import tab
    tabSheet.add("Import", importTab);

    // Add placeholder tabs for future features
    VerticalLayout searchPlaceholder = createPlaceholder("Search");
    tabSheet.add("Search", searchPlaceholder);

    VerticalLayout generatePlaceholder = createPlaceholder("Generate");
    tabSheet.add("Generate", generatePlaceholder);

    VerticalLayout groupMembershipsPlaceholder = createPlaceholder("Group Memberships");
    tabSheet.add("Group Memberships", groupMembershipsPlaceholder);

    add(tabSheet);

    // Initialize server configurations
    updateImportTabServers();
  }

  private VerticalLayout createPlaceholder(String feature) {
    VerticalLayout layout = new VerticalLayout();
    layout.setSizeFull();
    layout.setPadding(true);

    Paragraph status = new Paragraph("🚧 " + feature + " - Under Construction 🚧");
    status.getStyle()
        .set("font-size", "var(--lumo-font-size-xl)")
        .set("font-weight", "bold")
        .set("color", "var(--lumo-warning-color)")
        .set("text-align", "center")
        .set("padding", "var(--lumo-space-xl)");

    layout.add(status);
    return layout;
  }

  /**
   * Updates the server configurations for the import tab.
   */
  public void updateImportTabServers() {
    List<LdapServerConfig> selectedServers = getSelectedServers();
    importTab.setServerConfigs(selectedServers);
  }

  private List<LdapServerConfig> getSelectedServers() {
    Set<String> selectedServerNames = MainLayout.getSelectedServers();
    List<LdapServerConfig> selectedServers = new ArrayList<>();
    
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


