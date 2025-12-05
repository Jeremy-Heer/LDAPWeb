package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.TruststoreService;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.util.List;

/**
 * Reusable LDAP tree browser component.
 */
public class LdapTreeBrowser extends VerticalLayout {

  /**
   * Selection event fired when an entry is selected.
   */
  public static class SelectionEvent extends ComponentEvent<LdapTreeBrowser> {
    private final LdapEntry selectedEntry;

    /**
     * Creates a selection event.
     *
     * @param source the source component
     * @param selectedEntry the selected entry
     * @param fromClient whether from client
     */
    public SelectionEvent(LdapTreeBrowser source, LdapEntry selectedEntry, boolean fromClient) {
      super(source, fromClient);
      this.selectedEntry = selectedEntry;
    }

    public LdapEntry getSelectedEntry() {
      return selectedEntry;
    }

    public String getSelectedDn() {
      return selectedEntry != null ? selectedEntry.getDn() : null;
    }
  }

  private final LdapService ldapService;
  private final TruststoreService truststoreService;

  // Components
  private LdapTreeGrid treeGrid;
  private HorizontalLayout headerLayout;
  private Button refreshButton;
  private Checkbox privateNamingContextsCheckbox;

  // Configuration
  private LdapServerConfig serverConfig;

  /**
   * Creates a new LDAP tree browser.
   *
   * @param ldapService the LDAP service
   * @param truststoreService the truststore service
   */
  public LdapTreeBrowser(LdapService ldapService, TruststoreService truststoreService) {
    this.ldapService = ldapService;
    this.truststoreService = truststoreService;
    initializeComponents();
    setupLayout();
    setupEventHandlers();
  }

  private void initializeComponents() {
    // Create tree grid
    treeGrid = new LdapTreeGrid(ldapService, truststoreService);
    treeGrid.addClassName("ldap-tree");
    treeGrid.getStyle().set("margin", "0px");
    treeGrid.getStyle().set("padding", "0px");
    treeGrid.getStyle().set("border-top", "none");

    // Create header
    createBrowserHeader();
  }

  private void createBrowserHeader() {
    headerLayout = new HorizontalLayout();
    headerLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    headerLayout.setPadding(true);
    headerLayout.addClassName("ds-panel-header");
    headerLayout.getStyle().set("margin-bottom", "0px");

    Icon treeIcon = new Icon(VaadinIcon.TREE_TABLE);
    treeIcon.setSize("16px");
    treeIcon.getStyle().set("color", "#4a90e2");

    H3 browserTitle = new H3("LDAP Browser");
    browserTitle.addClassNames(LumoUtility.Margin.NONE);
    browserTitle.getStyle()
        .set("font-size", "0.9em")
        .set("font-weight", "600")
        .set("color", "#333");

    // Private naming contexts checkbox
    privateNamingContextsCheckbox = new Checkbox("Include private naming contexts");
    privateNamingContextsCheckbox.getStyle()
        .set("font-size", "0.85em")
        .set("margin-left", "1em");

    // Refresh button
    Icon refreshIcon = new Icon(VaadinIcon.REFRESH);
    refreshButton = new Button(refreshIcon);
    refreshButton.addThemeVariants(
        ButtonVariant.LUMO_ICON,
        ButtonVariant.LUMO_TERTIARY,
        ButtonVariant.LUMO_SMALL
    );
    refreshButton.setTooltipText("Refresh LDAP Browser");
    refreshButton.getStyle().set("color", "#4a90e2");

    headerLayout.add(treeIcon, browserTitle, privateNamingContextsCheckbox, refreshButton);
    headerLayout.setFlexGrow(1, browserTitle);
  }

  private void setupLayout() {
    setSizeFull();
    setPadding(false);
    setSpacing(false);
    addClassName("ds-panel");

    add(headerLayout);
    add(treeGrid);
    setFlexGrow(1, treeGrid);

    getStyle().set("gap", "0px");
  }

  private void setupEventHandlers() {
    // Forward tree grid selection events
    treeGrid.asSingleSelect().addValueChangeListener(event -> {
      LdapEntry selectedEntry = event.getValue();

      // Don't fire selection events for placeholders or pagination controls
      if (selectedEntry != null 
          && !selectedEntry.getDn().startsWith("_placeholder_")
          && !selectedEntry.getDn().startsWith("_pagination_")) {
        fireEvent(new SelectionEvent(this, selectedEntry, event.isFromClient()));
      }
    });

    // Refresh button handler
    if (refreshButton != null) {
      refreshButton.addClickListener(e -> refreshTree());
    }

    // Private naming contexts checkbox handler
    if (privateNamingContextsCheckbox != null) {
      privateNamingContextsCheckbox.addValueChangeListener(e -> {
        treeGrid.setIncludePrivateNamingContexts(e.getValue());
        refreshTree();
      });
    }
  }

  /**
   * Adds a selection listener.
   *
   * @param listener the listener
   * @return registration for removing the listener
   */
  public Registration addSelectionListener(ComponentEventListener<SelectionEvent> listener) {
    return addListener(SelectionEvent.class, listener);
  }

  /**
   * Sets the server configuration (for single server mode).
   *
   * @param serverConfig the server configuration
   */
  public void setServerConfig(LdapServerConfig serverConfig) {
    this.serverConfig = serverConfig;
    treeGrid.setServerConfig(serverConfig);
  }

  /**
   * Sets multiple server configurations.
   *
   * @param serverConfigs the list of server configurations
   */
  public void setServerConfigs(List<LdapServerConfig> serverConfigs) {
    this.serverConfig = null; // Clear single server config
    treeGrid.setServerConfigs(serverConfigs);
  }

  /**
   * Loads servers as top-level nodes (multi-server mode).
   */
  public void loadServers() {
    if (treeGrid != null) {
      try {
        treeGrid.loadServers();
      } catch (Exception e) {
        // Error handled by tree grid
      }
    }
  }

  /**
   * Loads the root DSE with naming contexts (single server mode).
   * 
   * @deprecated Use loadServers() for multi-server support
   */
  @Deprecated
  public void loadRootDSE() {
    if (treeGrid != null) {
      try {
        treeGrid.loadRootDSE();
      } catch (Exception e) {
        // Error handled by tree grid
      }
    }
  }

  /**
   * Refreshes the tree data.
   */
  public void refreshTree() {
    if (treeGrid != null) {
      try {
        treeGrid.collapseAll();
        treeGrid.loadServers();
      } catch (Exception e) {
        // Error handled by tree grid
      }
    }
  }

  /**
   * Clears the tree data.
   */
  public void clear() {
    if (treeGrid != null) {
      treeGrid.clear();
    }
  }

  /**
   * Gets the currently selected entry.
   *
   * @return the selected entry or null
   */
  public LdapEntry getSelectedEntry() {
    return treeGrid != null ? treeGrid.asSingleSelect().getValue() : null;
  }

  /**
   * Gets the currently selected DN.
   *
   * @return the selected DN or null
   */
  public String getSelectedDn() {
    LdapEntry selected = getSelectedEntry();
    return selected != null ? selected.getDn() : null;
  }

  /**
   * Gets the server configuration for a given entry.
   *
   * @param entry the LDAP entry
   * @return the server configuration, or null if not found
   */
  public LdapServerConfig getServerConfigForEntry(LdapEntry entry) {
    return treeGrid != null ? treeGrid.findServerConfigForEntry(entry) : null;
  }
}
