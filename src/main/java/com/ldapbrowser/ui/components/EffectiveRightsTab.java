package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.dialogs.DnBrowserDialog;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.unboundidds.controls.EffectiveRightsEntry;
import com.unboundid.ldap.sdk.unboundidds.controls.AttributeRight;
import com.unboundid.ldap.sdk.unboundidds.controls.EntryRight;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tab component for checking effective rights using GetEffectiveRightsRequestControl.
 * Allows users to search for entries and view their effective access rights.
 * Supports multiple LDAP servers.
 */
public class EffectiveRightsTab extends VerticalLayout {

  private static final Logger logger = LoggerFactory.getLogger(EffectiveRightsTab.class);

  private final LdapService ldapService;
  private Set<LdapServerConfig> selectedServers;
  
  // Form fields
  private TextField searchBaseField;
  private Button searchBaseBrowseButton;
  private ComboBox<SearchScope> scopeComboBox;
  private TextField searchFilterField;
  private TextField attributesField;
  private TextField effectiveRightsForField;
  private Button effectiveRightsForBrowseButton;
  private TextField searchSizeLimitField;
  private Button searchButton;
  
  // Results display
  private Grid<EffectiveRightsResult> resultsGrid;
  private ProgressBar progressBar;
  private Div loadingContainer;
  private Div resultsContainer;

  /**
   * Constructs a new EffectiveRightsTab.
   *
   * @param ldapService the LDAP service
   */
  public EffectiveRightsTab(LdapService ldapService) {
    this.ldapService = ldapService;
    this.selectedServers = Collections.emptySet();
    initUI();
  }

  private void initUI() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);

    H3 title = new H3("Effective Rights");
    add(title);

    Div description = new Div();
    description.setText("Check effective access rights for entries using GetEffectiveRightsRequestControl.");
    description.getStyle().set("color", "var(--lumo-secondary-text-color)")
        .set("margin-bottom", "var(--lumo-space-m)");
    add(description);

    initSearchForm();
    initResultsSection();
  }

  private void initSearchForm() {
    FormLayout formLayout = new FormLayout();
    formLayout.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1),
        new FormLayout.ResponsiveStep("600px", 2)
    );

    // Search Base
    searchBaseField = new TextField("Search Base");
    searchBaseField.setPlaceholder("dc=example,dc=com");
    searchBaseField.setWidthFull();
    
    searchBaseBrowseButton = new Button(new Icon(VaadinIcon.FOLDER_OPEN));
    searchBaseBrowseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    searchBaseBrowseButton.setTooltipText("Browse LDAP directory");
    searchBaseBrowseButton.addClickListener(e -> showDnBrowserDialog(searchBaseField));
    
    HorizontalLayout searchBaseLayout = new HorizontalLayout(searchBaseField, searchBaseBrowseButton);
    searchBaseLayout.setWidthFull();
    searchBaseLayout.setAlignItems(Alignment.END);
    searchBaseLayout.setSpacing(false);
    searchBaseLayout.expand(searchBaseField);

    // Search Scope
    scopeComboBox = new ComboBox<>("Search Scope");
    scopeComboBox.setItems(SearchScope.BASE, SearchScope.ONE, SearchScope.SUB);
    scopeComboBox.setValue(SearchScope.SUB);
    scopeComboBox.setItemLabelGenerator(scope -> {
      if (scope == SearchScope.BASE) return "Base";
      if (scope == SearchScope.ONE) return "One Level";
      if (scope == SearchScope.SUB) return "Subtree";
      return scope.getName();
    });

    // Search Filter
    searchFilterField = new TextField("Search Filter");
    searchFilterField.setPlaceholder("(objectClass=*)");
    searchFilterField.setValue("(objectClass=*)");
    searchFilterField.setWidthFull();

    // Attributes
    attributesField = new TextField("Attributes");
    attributesField.setPlaceholder("* (all attributes) or comma-separated list");
    attributesField.setValue("*");
    attributesField.setWidthFull();

    // Effective Rights For
    effectiveRightsForField = new TextField("Effective Rights For");
    effectiveRightsForField.setPlaceholder("Target user/entity DN (e.g., uid=admin,dc=example,dc=com)");
    effectiveRightsForField.setWidthFull();
    effectiveRightsForField.setRequiredIndicatorVisible(true);
    
    effectiveRightsForBrowseButton = new Button(new Icon(VaadinIcon.FOLDER_OPEN));
    effectiveRightsForBrowseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    effectiveRightsForBrowseButton.setTooltipText("Browse LDAP directory");
    effectiveRightsForBrowseButton.addClickListener(e -> showDnBrowserDialog(effectiveRightsForField));
    
    HorizontalLayout effectiveRightsForLayout = new HorizontalLayout(effectiveRightsForField, effectiveRightsForBrowseButton);
    effectiveRightsForLayout.setWidthFull();
    effectiveRightsForLayout.setAlignItems(Alignment.END);
    effectiveRightsForLayout.setSpacing(false);
    effectiveRightsForLayout.expand(effectiveRightsForField);

    // Search Size Limit
    searchSizeLimitField = new TextField("Search Size Limit");
    searchSizeLimitField.setPlaceholder("Maximum number of entries to return");
    searchSizeLimitField.setValue("100");
    searchSizeLimitField.setWidthFull();
    searchSizeLimitField.setHelperText("Maximum number of entries to return from LDAP search (0 = no limit)");
    searchSizeLimitField.setClearButtonVisible(true);

    // Search Button
    searchButton = new Button("Search", new Icon(VaadinIcon.SEARCH));
    searchButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    searchButton.addClickListener(e -> performSearch());

    formLayout.add(searchBaseLayout, scopeComboBox);
    formLayout.add(searchFilterField, 2);
    formLayout.add(attributesField, 2);
    formLayout.add(effectiveRightsForLayout, searchSizeLimitField);

    HorizontalLayout buttonLayout = new HorizontalLayout(searchButton);
    buttonLayout.setJustifyContentMode(JustifyContentMode.END);

    add(formLayout, buttonLayout);
  }

  private void initResultsSection() {
    resultsContainer = new Div();
    resultsContainer.setSizeFull();
    resultsContainer.getStyle().set("display", "flex");
    resultsContainer.getStyle().set("flex-direction", "column");
    resultsContainer.getStyle().set("gap", "10px");
    
    // Create loading indicator
    progressBar = new ProgressBar();
    progressBar.setIndeterminate(true);
    progressBar.setVisible(false);
    
    loadingContainer = new Div();
    loadingContainer.add(progressBar, new Div("Searching for effective rights..."));
    loadingContainer.getStyle().set("text-align", "center");
    loadingContainer.getStyle().set("padding", "20px");
    loadingContainer.setVisible(false);
    resultsContainer.add(loadingContainer);

    // Results grid
    resultsGrid = new Grid<>(EffectiveRightsResult.class, false);
    
    // Configure columns explicitly with better sizing - Server column first
    resultsGrid.addColumn(result -> result.getServerName())
        .setHeader("Server")
        .setWidth("150px")
        .setFlexGrow(0)
        .setSortable(true)
        .setResizable(true);
    
    resultsGrid.addColumn(result -> result.getDn())
        .setHeader("Entry DN")
        .setWidth("300px")
        .setFlexGrow(0)
        .setSortable(false)
        .setResizable(true);
    
    resultsGrid.addColumn(result -> result.getAttributeRights())
        .setHeader("Attribute Rights")
        .setAutoWidth(false)
        .setFlexGrow(1)
        .setSortable(false)
        .setResizable(true);
    
    resultsGrid.addColumn(result -> result.getEntryRights())
        .setHeader("Entry Rights")
        .setWidth("200px")
        .setFlexGrow(0)
        .setSortable(false)
        .setResizable(true);

    // Set grid properties for better visibility
    resultsGrid.setWidthFull();
    resultsGrid.setHeight("500px");
    resultsGrid.setVisible(false);
    resultsGrid.setMultiSort(false);
    
    resultsContainer.add(resultsGrid);

    add(resultsContainer);
    setFlexGrow(1, resultsContainer);
  }

  /**
   * Sets the selected servers for this tab.
   *
   * @param servers the set of selected servers
   */
  public void setSelectedServers(Set<LdapServerConfig> servers) {
    this.selectedServers = servers != null ? servers : Collections.emptySet();
    
    // Set default search base from first server config if available
    if (!this.selectedServers.isEmpty()) {
      LdapServerConfig firstServer = this.selectedServers.iterator().next();
      if (firstServer.getBaseDn() != null && !firstServer.getBaseDn().isEmpty()) {
        searchBaseField.setValue(firstServer.getBaseDn());
      }
    }
    
    // Clear results and hide grid
    resultsGrid.setItems(new ArrayList<>());
    resultsGrid.setVisible(false);
  }

  /**
   * Shows the DN browser dialog for selecting a DN from the LDAP directory.
   *
   * @param targetField the text field to populate with the selected DN
   */
  private void showDnBrowserDialog(TextField targetField) {
    if (selectedServers == null || selectedServers.isEmpty()) {
      NotificationHelper.showError("No servers selected");
      return;
    }

    new DnBrowserDialog(ldapService)
        .withServerConfigs(new ArrayList<>(selectedServers))
        .onDnSelected(dn -> targetField.setValue(dn))
        .open();
  }

  /**
   * Loads data for this tab (called when tab is selected).
   */
  public void loadData() {
    // Nothing to load automatically - user initiates search manually
  }

  private void performSearch() {
    if (selectedServers == null || selectedServers.isEmpty()) {
      NotificationHelper.showError("No servers selected");
      return;
    }

    String searchBase = searchBaseField.getValue();
    if (searchBase == null || searchBase.trim().isEmpty()) {
      NotificationHelper.showError("Search base is required");
      return;
    }

    String effectiveRightsFor = effectiveRightsForField.getValue();
    if (effectiveRightsFor == null || effectiveRightsFor.trim().isEmpty()) {
      NotificationHelper.showError("Effective Rights For DN is required");
      return;
    }

    // Auto-prepend "dn: " if not already present
    final String formattedRightsFor;
    String tempRightsFor = effectiveRightsFor.trim();
    if (!tempRightsFor.toLowerCase().startsWith("dn: ")) {
      formattedRightsFor = "dn: " + tempRightsFor;
    } else {
      formattedRightsFor = tempRightsFor;
    }

    // Parse and validate search size limit
    final int sizeLimit;
    String sizeLimitStr = searchSizeLimitField.getValue();
    if (sizeLimitStr != null && !sizeLimitStr.trim().isEmpty()) {
      try {
        int parsedLimit = Integer.parseInt(sizeLimitStr.trim());
        if (parsedLimit < 0) {
          NotificationHelper.showError("Search size limit must be 0 or greater");
          return;
        }
        sizeLimit = parsedLimit;
      } catch (NumberFormatException e) {
        NotificationHelper.showError("Search size limit must be a valid number");
        return;
      }
    } else {
      sizeLimit = 100; // default
    }

    loadingContainer.setVisible(true);
    progressBar.setVisible(true);
    resultsGrid.setVisible(false);

    // Perform search in background thread across all selected servers
    new Thread(() -> {
      List<EffectiveRightsResult> allResults = new ArrayList<>();
      boolean anyLimitExceeded = false;
      
      for (LdapServerConfig server : selectedServers) {
        try {
          LdapService.SearchResultWithEffectiveRights searchResult = ldapService.searchEffectiveRights(
              server,
              searchBase.trim(),
              scopeComboBox.getValue(),
              searchFilterField.getValue().trim(),
              attributesField.getValue().trim(),
              formattedRightsFor,
              sizeLimit
          );

          // Process the search results
          for (SearchResultEntry entry : searchResult.getEntries()) {
            processEffectiveRightsEntry(entry, formattedRightsFor, server.getName(), allResults);
          }
          
          if (searchResult.isSizeLimitExceeded()) {
            anyLimitExceeded = true;
          }
        } catch (Exception e) {
          logger.error("Error searching server {}: {}", server.getName(), e.getMessage(), e);
          // Show error to user but continue processing other servers
          final String errorMsg = e.getMessage();
          getUI().ifPresent(ui -> ui.access(() -> 
              NotificationHelper.showError("Error searching server " + server.getName() + ": " + errorMsg)));
        }
      }

      final List<EffectiveRightsResult> results = allResults;
      final boolean sizeLimitExceeded = anyLimitExceeded;

      getUI().ifPresent(ui -> ui.access(() -> {
          try {
            loadingContainer.setVisible(false);
            progressBar.setVisible(false);
            
            resultsGrid.setItems(results);
            resultsGrid.setVisible(true);
            resultsContainer.setVisible(true);
            
            if (results.isEmpty()) {
              if (sizeLimitExceeded) {
                NotificationHelper.showWarning("Search size limit (" + sizeLimit + ") exceeded but no entries were returned. Try adjusting your search criteria.");
              } else {
                NotificationHelper.showInfo("No entries found matching the search criteria");
              }
            } else {
              String successMessage = "Found " + results.size() + " entries with effective rights information";
              if (sizeLimitExceeded) {
                successMessage += " (size limit reached - more entries may be available)";
              }
              NotificationHelper.showSuccess(successMessage);
            }
          } catch (Exception gridException) {
            logger.error("Exception during grid update", gridException);
            loadingContainer.setVisible(false);
            progressBar.setVisible(false);
            NotificationHelper.showError("Error updating results: " + gridException.getMessage());
          }
          
          ui.push();
        }));
    }).start();
  }

  /**
   * Processes a single LDAP entry to extract effective rights information.
   */
  private void processEffectiveRightsEntry(Entry entry, String effectiveRightsFor, 
      String serverName, List<EffectiveRightsResult> results) {
    EffectiveRightsResult result = new EffectiveRightsResult();
    result.setServerName(serverName);
    result.setDn(entry.getDN());
    
    // Create EffectiveRightsEntry to check rights information availability
    EffectiveRightsEntry effectiveRightsEntry = new EffectiveRightsEntry(entry);
    
    StringBuilder attributeRights = new StringBuilder();
    StringBuilder entryRights = new StringBuilder();
    
    if (effectiveRightsEntry.rightsInformationAvailable()) {
      // Rights information is available - process attribute rights
      for (Attribute attribute : entry.getAttributes()) {
        String attrName = attribute.getName();
        if (!attrName.equals("aclRights") && !attrName.equals("entryLevelRights")) {
          // Check rights for this attribute
          StringBuilder attrRightsBuilder = new StringBuilder();
          
          if (effectiveRightsEntry.hasAttributeRight(AttributeRight.READ, attrName)) {
            attrRightsBuilder.append("r");
          }
          if (effectiveRightsEntry.hasAttributeRight(AttributeRight.WRITE, attrName)) {
            attrRightsBuilder.append("w");
          }
          if (effectiveRightsEntry.hasAttributeRight(AttributeRight.SELFWRITE_ADD, attrName)) {
            attrRightsBuilder.append("a");
          }
          if (effectiveRightsEntry.hasAttributeRight(AttributeRight.SELFWRITE_DELETE, attrName)) {
            attrRightsBuilder.append("d");
          }
          if (effectiveRightsEntry.hasAttributeRight(AttributeRight.COMPARE, attrName)) {
            attrRightsBuilder.append("c");
          }
          if (effectiveRightsEntry.hasAttributeRight(AttributeRight.SEARCH, attrName)) {
            attrRightsBuilder.append("s");
          }
          
          if (attrRightsBuilder.length() > 0) {
            if (attributeRights.length() > 0) {
              attributeRights.append("; ");
            }
            attributeRights.append(attrName).append(": ").append(attrRightsBuilder.toString());
          }
        }
      }
      
      // Check entry-level rights
      StringBuilder entryRightsBuilder = new StringBuilder();
      if (effectiveRightsEntry.hasEntryRight(EntryRight.ADD)) {
        entryRightsBuilder.append("a");
      }
      if (effectiveRightsEntry.hasEntryRight(EntryRight.DELETE)) {
        entryRightsBuilder.append("d");
      }
      if (effectiveRightsEntry.hasEntryRight(EntryRight.READ)) {
        entryRightsBuilder.append("r");
      }
      if (effectiveRightsEntry.hasEntryRight(EntryRight.WRITE)) {
        entryRightsBuilder.append("w");
      }
      if (effectiveRightsEntry.hasEntryRight(EntryRight.PROXY)) {
        entryRightsBuilder.append("p");
      }
      
      if (entryRightsBuilder.length() > 0) {
        entryRights.append("Entry rights: ").append(entryRightsBuilder.toString());
      } else {
        entryRights.append("No entry rights for: ").append(effectiveRightsFor);
      }
      
      // If no attribute rights found, provide informative message
      if (attributeRights.length() == 0) {
        attributeRights.append("No specific attribute rights available");
      }
      
    } else {
      // No effective rights information was returned
      attributeRights.append("No effective rights information available - control may be unsupported or user lacks privileges");
      entryRights.append("No entry rights information available for: ").append(effectiveRightsFor);
    }
    
    result.setAttributeRights(attributeRights.toString());
    result.setEntryRights(entryRights.toString());
    
    results.add(result);
  }

  /**
   * Result class for effective rights search results.
   */
  public static class EffectiveRightsResult {
    private String serverName;
    private String dn;
    private String attributeRights;
    private String entryRights;
    
    public EffectiveRightsResult() {
      // Default constructor
    }

    public String getServerName() {
      return serverName != null ? serverName : "";
    }

    public void setServerName(String serverName) {
      this.serverName = serverName;
    }

    public String getDn() {
      return dn != null ? dn : "";
    }

    public void setDn(String dn) {
      this.dn = dn;
    }

    public String getAttributeRights() {
      return attributeRights != null ? attributeRights : "";
    }

    public void setAttributeRights(String attributeRights) {
      this.attributeRights = attributeRights;
    }

    public String getEntryRights() {
      return entryRights != null ? entryRights : "";
    }

    public void setEntryRights(String entryRights) {
      this.entryRights = entryRights;
    }
    
    @Override
    public String toString() {
      return "EffectiveRightsResult{" +
          "dn='" + dn + '\'' +
          ", attributeRights='" + attributeRights + '\'' +
          ", entryRights='" + entryRights + '\'' +
          '}';
    }
  }
}