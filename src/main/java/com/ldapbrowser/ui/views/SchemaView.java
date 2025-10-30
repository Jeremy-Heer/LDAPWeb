package com.ldapbrowser.ui.views;

import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.components.SchemaCompareTab;
import com.ldapbrowser.ui.components.SchemaManageTab;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

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

  /**
   * Creates the Schema view.
   *
   * @param ldapService LDAP service
   * @param configService configuration service
   */
  public SchemaView(LdapService ldapService, ConfigurationService configService) {
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
    compareTab = new SchemaCompareTab();

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
      }
    });

    add(topLevelTabs, contentContainer);
    setFlexGrow(1, contentContainer);

    // Load schemas on initial view
    manageTab.loadSchemas();
  }
}
