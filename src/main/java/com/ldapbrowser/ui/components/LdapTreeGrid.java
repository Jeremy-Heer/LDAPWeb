package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.BrowseResult;
import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.unboundid.ldap.sdk.LDAPException;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tree grid component for browsing LDAP entries.
 */
public class LdapTreeGrid extends TreeGrid<LdapEntry> {

  private final LdapService ldapService;
  private final Map<String, LdapServerConfig> serverConfigMap = new HashMap<>();
  private TreeData<LdapEntry> treeData;
  private TreeDataProvider<LdapEntry> dataProvider;

  // Paging support
  private final Map<String, Integer> entryPageState = new HashMap<>();
  private static final int PAGE_SIZE = 100;

  // Track which server config each entry belongs to
  private final Map<LdapEntry, LdapServerConfig> entryServerMap = new HashMap<>();

  /**
   * Creates a new LDAP tree grid.
   *
   * @param ldapService the LDAP service
   */
  public LdapTreeGrid(LdapService ldapService) {
    this.ldapService = ldapService;
    initializeGrid();
  }

  private void initializeGrid() {
    setSizeFull();
    setSelectionMode(Grid.SelectionMode.SINGLE);

    // Initialize tree data
    treeData = new TreeData<>();
    dataProvider = new TreeDataProvider<>(treeData);
    setDataProvider(dataProvider);

    // Add icon column
    addComponentColumn(this::createIconComponent)
        .setHeader("")
        .setWidth("40px")
        .setFlexGrow(0)
        .setSortable(false);

    // Configure hierarchy column
    addHierarchyColumn(this::getEntryDisplayName)
        .setHeader("")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(false);

    // Hide header
    hideGridHeader();

    // Style
    addClassName("ldap-tree-grid");
    getStyle().set("border", "1px solid var(--lumo-contrast-20pct)");
    getStyle().set("margin", "0px");
    getStyle().set("padding", "0px");

    // Add attach listener to ensure header stays hidden
    addAttachListener(event -> {
      getUI().ifPresent(ui -> ui.access(() -> {
        ui.getElement().executeJs(
            "setTimeout(() => { "
            + "if ($0.shadowRoot && $0.shadowRoot.querySelector('thead')) { "
            + "$0.shadowRoot.querySelector('thead').style.display = 'none'; "
            + "} }, 50)",
            getElement());
      }));
    });

    // Keyboard navigation
    getElement().setAttribute("tabindex", "0");
    getElement().addEventListener("keydown", e -> {
      String key = e.getEventData().getString("event.key");
      if ("Enter".equals(key) || " ".equals(key)) {
        LdapEntry selectedEntry = asSingleSelect().getValue();
        if (selectedEntry != null && selectedEntry.isHasChildren()) {
          if (isExpanded(selectedEntry)) {
            collapse(selectedEntry);
          } else {
            expandEntry(selectedEntry);
          }
        }
      }
    }).addEventData("event.key");

    // Expand listener for lazy loading
    addExpandListener(event -> {
      event.getItems().forEach(item -> {
        if (isExpanded(item)) {
          loadChildren(item);
          // Don't auto-select on expand - this was causing the dialog to close
        }
      });
    });

    // Collapse listener
    addCollapseListener(event -> {
      // Don't auto-select on collapse - this was causing unwanted selections
    });

    // Selection listener to handle Root DSE expansion and pagination controls
    asSingleSelect().addValueChangeListener(event -> {
      LdapEntry selectedEntry = event.getValue();
      if (selectedEntry != null) {
        // Handle pagination control clicks
        if (!selectedEntry.getAttributeValues("isPagination").isEmpty()) {
          handlePaginationClick(selectedEntry);
          return;
        }
        
        // Handle Root DSE expansion
        if (selectedEntry.getDn().isEmpty() || "Root DSE".equals(selectedEntry.getRdn())) {
          expandEntry(selectedEntry);
        }
      }
    });
  }

  private void hideGridHeader() {
    getElement().executeJs(
        "this.shadowRoot.querySelector('thead').style.display = 'none'");
  }

  private LdapEntry createPlaceholderEntry() {
    LdapEntry placeholder = new LdapEntry();
    placeholder.setDn("_placeholder_" + System.nanoTime());
    placeholder.addAttribute("displayName", "Loading...");
    placeholder.setHasChildren(false);
    return placeholder;
  }

  /**
   * Adds pagination control entries (Previous Page, Page Info, Next Page).
   *
   * @param parent the parent entry
   * @param currentPage the current page number (0-indexed)
   * @param hasNextPage whether there is a next page
   * @param hasPrevPage whether there is a previous page
   */
  private void addPaginationControls(LdapEntry parent, int currentPage, boolean hasNextPage, 
      boolean hasPrevPage) {
    // Only show pagination controls if there are multiple pages
    if (!hasNextPage && !hasPrevPage) {
      return;
    }
    
    String parentDn = parent.getDn();
    
    // Add "◀ Previous Page" control if available
    if (hasPrevPage) {
      LdapEntry prevControl = new LdapEntry();
      prevControl.setDn("_pagination_prev_" + parentDn + "_" + System.nanoTime());
      prevControl.setRdn("◀ Previous Page");
      prevControl.setHasChildren(false);
      prevControl.addAttribute("isPagination", "true");
      prevControl.addAttribute("action", "previous");
      prevControl.addAttribute("parentDn", parentDn);
      prevControl.addAttribute("currentPage", String.valueOf(currentPage));
      treeData.addItem(parent, prevControl);
    }
    
    // Add "— Page X —" info control
    LdapEntry pageInfo = new LdapEntry();
    pageInfo.setDn("_pagination_info_" + parentDn + "_" + System.nanoTime());
    pageInfo.setRdn("— Page " + (currentPage + 1) + " —");
    pageInfo.setHasChildren(false);
    pageInfo.addAttribute("isPagination", "true");
    pageInfo.addAttribute("action", "info");
    pageInfo.addAttribute("parentDn", parentDn);
    pageInfo.addAttribute("currentPage", String.valueOf(currentPage));
    treeData.addItem(parent, pageInfo);
    
    // Add "Next Page ▶" control if available
    if (hasNextPage) {
      LdapEntry nextControl = new LdapEntry();
      nextControl.setDn("_pagination_next_" + parentDn + "_" + System.nanoTime());
      nextControl.setRdn("Next Page ▶");
      nextControl.setHasChildren(false);
      nextControl.addAttribute("isPagination", "true");
      nextControl.addAttribute("action", "next");
      nextControl.addAttribute("parentDn", parentDn);
      nextControl.addAttribute("currentPage", String.valueOf(currentPage));
      treeData.addItem(parent, nextControl);
    }
  }

  /**
   * Handles clicking a pagination control entry.
   *
   * @param paginationEntry the pagination control that was clicked
   */
  private void handlePaginationClick(LdapEntry paginationEntry) {
    String action = paginationEntry.getFirstAttributeValue("action");
    
    // Ignore info entries (non-clickable)
    if ("info".equals(action)) {
      return;
    }
    
    String parentDn = paginationEntry.getFirstAttributeValue("parentDn");
    int currentPage = Integer.parseInt(paginationEntry.getFirstAttributeValue("currentPage"));
    
    // Find parent entry
    LdapEntry parent = findEntryByDn(parentDn);
    if (parent == null) {
      showNotification("Unable to find parent entry", NotificationVariant.LUMO_ERROR);
      return;
    }
    
    // Get server config
    LdapServerConfig serverConfig = entryServerMap.get(parent);
    if (serverConfig == null) {
      showNotification("Unable to determine server for entry", NotificationVariant.LUMO_ERROR);
      return;
    }
    
    // Calculate new page
    int newPage = currentPage;
    if ("next".equals(action)) {
      newPage = currentPage + 1;
    } else if ("previous".equals(action)) {
      newPage = currentPage - 1;
    }
    
    // Load new page
    if (newPage != currentPage) {
      entryPageState.put(parentDn, newPage);
      loadChildrenPage(parent, serverConfig, newPage);
    }
  }


  private Icon createIconComponent(LdapEntry entry) {
    return getIconForEntry(entry);
  }

  private Icon getIconForEntry(LdapEntry entry) {
    // Pagination control entries
    if (!entry.getAttributeValues("isPagination").isEmpty()) {
      Icon icon = new Icon(VaadinIcon.ELLIPSIS_DOTS_H);
      icon.getStyle().set("color", "var(--lumo-primary-color)");
      icon.getStyle().set("cursor", "pointer");
      return icon;
    }
    
    // Placeholder entries
    if (entry.getDn().startsWith("_placeholder_")) {
      Icon icon = new Icon(VaadinIcon.ELLIPSIS_DOTS_H);
      icon.getStyle().set("color", "#BDBDBD");
      return icon;
    }

    // Server node
    if (entry.getDn().startsWith("SERVER:")) {
      Icon icon = new Icon(VaadinIcon.SERVER);
      icon.getStyle().set("color", "#9C27B0");
      return icon;
    }

    // Root DSE
    if (entry.getDn().isEmpty() || "Root DSE".equals(entry.getRdn())) {
      Icon icon = new Icon(VaadinIcon.DATABASE);
      icon.getStyle().set("color", "#FF5722");
      return icon;
    }

    List<String> objectClasses = entry.getAttributeValues("objectClass");
    if (objectClasses != null && !objectClasses.isEmpty()) {
      for (String objectClass : objectClasses) {
        String lowerClass = objectClass.toLowerCase();

        // Users
        if (lowerClass.contains("person") || lowerClass.contains("user")
            || lowerClass.contains("inetorgperson")) {
          Icon icon = new Icon(VaadinIcon.USER);
          icon.getStyle().set("color", "#2196F3");
          return icon;
        }

        // Groups
        if (lowerClass.contains("group") || lowerClass.contains("groupofnames")) {
          Icon icon = new Icon(VaadinIcon.USERS);
          icon.getStyle().set("color", "#4CAF50");
          return icon;
        }
      }
    }

    // Container types
    if (entry.isHasChildren() && objectClasses != null) {
      for (String objectClass : objectClasses) {
        String lowerClass = objectClass.toLowerCase();
        if (lowerClass.contains("organizationalunit") || lowerClass.contains("ou")) {
          Icon icon = new Icon(VaadinIcon.FOLDER_OPEN);
          icon.getStyle().set("color", "#FFA726");
          return icon;
        }
        if (lowerClass.contains("organization")) {
          Icon icon = new Icon(VaadinIcon.BUILDING);
          icon.getStyle().set("color", "#FF9800");
          return icon;
        }
        if (lowerClass.contains("domain") || lowerClass.contains("dcobject")) {
          Icon icon = new Icon(VaadinIcon.GLOBE);
          icon.getStyle().set("color", "#66BB6A");
          return icon;
        }
      }

      // Default container
      Icon icon = new Icon(VaadinIcon.FOLDER);
      icon.getStyle().set("color", "#90A4AE");
      return icon;
    }

    // Default leaf
    Icon icon = new Icon(VaadinIcon.FILE_TEXT);
    icon.getStyle().set("color", "#757575");
    return icon;
  }

  private String getEntryDisplayName(LdapEntry entry) {
    if (entry.getDn().startsWith("_placeholder_")) {
      return "Loading...";
    }

    if (entry.getDn().isEmpty() || "Root DSE".equals(entry.getRdn())) {
      return "Root DSE";
    }

    // Show full DN for naming contexts (direct children of Root DSE)
    LdapEntry parent = treeData.getParent(entry);
    if (parent == null || parent.getDn().isEmpty() || "Root DSE".equals(parent.getRdn())) {
      return entry.getDn();
    }

    // Show RDN for other entries
    String rdn = entry.getRdn();
    return rdn != null && !rdn.isEmpty() ? rdn : entry.getDn();
  }

  /**
   * Sets the server configuration (for single server mode).
   *
   * @param serverConfig the server configuration
   */
  public void setServerConfig(LdapServerConfig serverConfig) {
    serverConfigMap.clear();
    serverConfigMap.put(serverConfig.getName(), serverConfig);
  }

  /**
   * Sets multiple server configurations (for multi-server mode).
   *
   * @param serverConfigs the list of server configurations
   */
  public void setServerConfigs(List<LdapServerConfig> serverConfigs) {
    serverConfigMap.clear();
    for (LdapServerConfig config : serverConfigs) {
      serverConfigMap.put(config.getName(), config);
    }
  }

  /**
   * Loads server entries as top-level nodes.
   * Each server will have a Root DSE child with naming contexts.
   *
   * @throws LDAPException if an error occurs
   */
  public void loadServers() throws LDAPException {
    if (serverConfigMap.isEmpty()) {
      throw new IllegalStateException("No server configs set");
    }

    clear();

    // Add each server as a top-level entry
    for (LdapServerConfig config : serverConfigMap.values()) {
      // Create server entry
      LdapEntry serverEntry = new LdapEntry();
      serverEntry.setDn("SERVER:" + config.getName()); // Unique DN for server node
      serverEntry.setRdn(config.getName());
      serverEntry.setHasChildren(true);
      serverEntry.addAttribute("objectClass", "serverNode");
      
      entryServerMap.put(serverEntry, config);
      treeData.addItem(null, serverEntry);

      // Add placeholder to show expander
      LdapEntry placeholder = createPlaceholderEntry();
      treeData.addItem(serverEntry, placeholder);
    }

    dataProvider.refreshAll();
    showNotification("Loaded " + serverConfigMap.size() + " server(s)", 
        NotificationVariant.LUMO_SUCCESS);
  }

  /**
   * Loads root DSE with naming contexts (legacy single server mode).
   *
   * @throws LDAPException if an error occurs
   * @deprecated Use loadServers() for multi-server support
   */
  @Deprecated
  public void loadRootDSE() throws LDAPException {
    if (serverConfigMap.isEmpty()) {
      throw new IllegalStateException("Server config not set");
    }

    // Use first server config for backward compatibility
    LdapServerConfig config = serverConfigMap.values().iterator().next();
    
    clear();

    // Create Root DSE entry
    LdapEntry rootDse = new LdapEntry();
    rootDse.setDn("");
    rootDse.setRdn("Root DSE");
    rootDse.setHasChildren(true);
    rootDse.addAttribute("objectClass", "top");
    
    entryServerMap.put(rootDse, config);
    treeData.addItem(null, rootDse);

    // Add placeholder to show expander
    LdapEntry placeholder = createPlaceholderEntry();
    treeData.addItem(rootDse, placeholder);

    dataProvider.refreshAll();
    showNotification("Loaded Root DSE", NotificationVariant.LUMO_SUCCESS);
  }

  private void loadChildren(LdapEntry parent) {
    int currentPage = entryPageState.getOrDefault(parent.getDn(), 0);
    loadChildren(parent, currentPage);
  }

  private void loadChildren(LdapEntry parent, int page) {
    // Get the server config for this entry
    LdapServerConfig serverConfig = entryServerMap.get(parent);
    if (serverConfig == null) {
      // Try to find by walking up the tree
      serverConfig = findServerConfigForEntry(parent);
      if (serverConfig == null) {
        showNotification("Cannot determine server for entry", NotificationVariant.LUMO_ERROR);
        return;
      }
    }

    entryPageState.put(parent.getDn(), page);

    try {
      // Handle Server node - load Root DSE
      if (parent.getDn().startsWith("SERVER:")) {
        loadRootDSEForServer(parent, serverConfig);
        return;
      }

      // Handle Root DSE specially - load naming contexts
      if (parent.getDn().isEmpty() || "Root DSE".equals(parent.getRdn())) {
        loadNamingContexts(parent, serverConfig);
        return;
      }

      // Load regular children
      loadRegularChildren(parent, serverConfig);

    } catch (Exception ex) {
      showNotification("Failed to load children: " + ex.getMessage(),
          NotificationVariant.LUMO_ERROR);
    }
  }

  private LdapServerConfig findServerConfigForEntry(LdapEntry entry) {
    // Walk up the tree to find the server node
    LdapEntry current = entry;
    while (current != null) {
      LdapServerConfig config = entryServerMap.get(current);
      if (config != null) {
        return config;
      }
      current = treeData.getParent(current);
    }
    return null;
  }

  private void loadRootDSEForServer(LdapEntry serverNode, LdapServerConfig serverConfig) {
    getUI().ifPresent(ui -> ui.access(() -> {
      try {
        // Create Root DSE entry
        LdapEntry rootDse = new LdapEntry();
        rootDse.setDn("");
        rootDse.setRdn("Root DSE");
        rootDse.setHasChildren(true);
        rootDse.addAttribute("objectClass", "top");
        
        entryServerMap.put(rootDse, serverConfig);

        // Remove existing children (placeholders)
        List<LdapEntry> existingChildren = new ArrayList<>(treeData.getChildren(serverNode));
        for (LdapEntry child : existingChildren) {
          treeData.removeItem(child);
        }

        // Add Root DSE
        treeData.addItem(serverNode, rootDse);
        
        // Add placeholder for Root DSE
        LdapEntry placeholder = createPlaceholderEntry();
        treeData.addItem(rootDse, placeholder);

        dataProvider.refreshItem(serverNode, true);
        showNotification("Loaded Root DSE for " + serverConfig.getName(),
            NotificationVariant.LUMO_SUCCESS);
      } catch (Exception ex) {
        showNotification("Failed to load Root DSE: " + ex.getMessage(),
            NotificationVariant.LUMO_ERROR);
      }
    }));
  }

  private void loadNamingContexts(LdapEntry rootDse, LdapServerConfig serverConfig) {
    getUI().ifPresent(ui -> ui.access(() -> {
      try {
        List<String> namingContextDns = ldapService.getNamingContexts(serverConfig);
        List<LdapEntry> namingContexts = new ArrayList<>();

        for (String ctx : namingContextDns) {
          try {
            LdapEntry ctxEntry = ldapService.getEntryMinimal(serverConfig, ctx);
            if (ctxEntry != null) {
              ctxEntry.setHasChildren(true);
              entryServerMap.put(ctxEntry, serverConfig);
              namingContexts.add(ctxEntry);
            }
          } catch (Exception ignoredCtx) {
            // Create minimal entry
            LdapEntry ctxEntry = new LdapEntry();
            ctxEntry.setDn(ctx);
            ctxEntry.setRdn(ctx);
            ctxEntry.setHasChildren(true);
            ctxEntry.addAttribute("objectClass", "organizationalUnit");
            entryServerMap.put(ctxEntry, serverConfig);
            namingContexts.add(ctxEntry);
          }
        }

        // Remove existing children
        List<LdapEntry> existingChildren = new ArrayList<>(treeData.getChildren(rootDse));
        for (LdapEntry child : existingChildren) {
          treeData.removeItem(child);
        }

        // Add naming contexts
        for (LdapEntry nc : namingContexts) {
          treeData.addItem(rootDse, nc);
          LdapEntry placeholder = createPlaceholderEntry();
          treeData.addItem(nc, placeholder);
        }

        dataProvider.refreshItem(rootDse, true);
        showNotification("Loaded " + namingContexts.size() + " naming contexts",
            NotificationVariant.LUMO_SUCCESS);
      } catch (Exception ex) {
        showNotification("Failed to load naming contexts: " + ex.getMessage(),
            NotificationVariant.LUMO_ERROR);
      }
    }));
  }

  /**
   * Finds an entry by its DN in the tree.
   *
   * @param dn the DN to search for
   * @return the entry or null if not found
   */
  private LdapEntry findEntryByDn(String dn) {
    return findEntryByDnRecursive(dn, null);
  }
  
  /**
   * Recursively searches for an entry by DN.
   *
   * @param dn the DN to search for
   * @param parent the parent entry to search under (null for root)
   * @return the entry or null if not found
   */
  private LdapEntry findEntryByDnRecursive(String dn, LdapEntry parent) {
    List<LdapEntry> children = treeData.getChildren(parent);
    for (LdapEntry child : children) {
      if (dn.equals(child.getDn())) {
        return child;
      }
      LdapEntry found = findEntryByDnRecursive(dn, child);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private void loadRegularChildren(LdapEntry parent, LdapServerConfig serverConfig) {
    loadChildrenPage(parent, serverConfig, 0);
  }
  
  /**
   * Loads a specific page of children for a parent entry.
   *
   * @param parent the parent entry
   * @param serverConfig the server configuration
   * @param pageNumber the page number to load (0-indexed)
   */
  private void loadChildrenPage(LdapEntry parent, LdapServerConfig serverConfig, int pageNumber) {
    try {
      BrowseResult result = ldapService.browseEntriesWithPage(serverConfig, parent.getDn(), pageNumber);
      List<LdapEntry> children = result.getEntries();

      getUI().ifPresent(ui -> ui.access(() -> {
        // Remove all existing children and pagination controls
        List<LdapEntry> existingChildren = new ArrayList<>(treeData.getChildren(parent));
        for (LdapEntry child : existingChildren) {
          treeData.removeItem(child);
        }

        if (children.isEmpty() && pageNumber == 0) {
          if (!shouldShowExpanderForEntry(parent)) {
            parent.setHasChildren(false);
          }
        } else {
          // Add pagination controls at the top
          addPaginationControls(parent, pageNumber, result.hasNextPage(), result.hasPrevPage());
          
          // Add new children from this page
          for (LdapEntry child : children) {
            ensureHasChildrenFlagIsSet(child);
            entryServerMap.put(child, serverConfig); // Track server for this entry
            treeData.addItem(parent, child);

            if (child.isHasChildren() || shouldShowExpanderForEntry(child)) {
              LdapEntry placeholder = createPlaceholderEntry();
              treeData.addItem(child, placeholder);
            }
          }
        }

        dataProvider.refreshItem(parent, true);
        
        // Show notification about pagination
        if (pageNumber > 0 || result.hasNextPage() || result.hasPrevPage()) {
          String message = String.format("Loaded page %d (%d entries) - Use pagination controls to navigate",
              pageNumber + 1, children.size());
          showNotification(message, NotificationVariant.LUMO_SUCCESS);
        }
      }));
    } catch (Exception ex) {
      showNotification("Failed to load children: " + ex.getMessage(),
          NotificationVariant.LUMO_ERROR);
    }
  }

  /**
   * Clears the tree data.
   */
  public void clear() {
    treeData.clear();
    dataProvider.refreshAll();
    entryPageState.clear();
    entryServerMap.clear();

    // Clear paging state for all servers
    for (LdapServerConfig config : serverConfigMap.values()) {
      ldapService.clearPagingState(config.getName());
    }
  }

  /**
   * Expands a specific entry and loads its children.
   *
   * @param entry the entry to expand
   */
  public void expandEntry(LdapEntry entry) {
    if (entry.isHasChildren() && !isExpanded(entry)) {
      loadChildren(entry);
      expand(entry);
    }
  }

  /**
   * Collapses all expanded entries.
   */
  public void collapseAll() {
    treeData.getRootItems().forEach(this::collapseRecursively);
  }

  private void collapseRecursively(LdapEntry entry) {
    treeData.getChildren(entry).forEach(this::collapseRecursively);
    if (isExpanded(entry)) {
      collapse(entry);
    }
  }

  private void ensureHasChildrenFlagIsSet(LdapEntry entry) {
    if (shouldShowExpanderForEntry(entry)) {
      entry.setHasChildren(true);
    }
  }

  private boolean shouldShowExpanderForEntry(LdapEntry entry) {
    List<String> objectClasses = entry.getAttributeValues("objectClass");
    if (objectClasses == null || objectClasses.isEmpty()) {
      return false;
    }

    boolean isDefinitelyLeaf = false;
    for (String oc : objectClasses) {
      String lowerOc = oc.toLowerCase();
      if (lowerOc.contains("person") || lowerOc.contains("user")
          || lowerOc.contains("inetorgperson") || lowerOc.contains("computer")
          || lowerOc.contains("device") || lowerOc.contains("printer")) {
        isDefinitelyLeaf = true;
        break;
      }
    }

    return !isDefinitelyLeaf;
  }

  private void showNotification(String message, NotificationVariant variant) {
    Notification notification = Notification.show(message, 3000,
        Notification.Position.BOTTOM_END);
    notification.addThemeVariants(variant);
  }
}
