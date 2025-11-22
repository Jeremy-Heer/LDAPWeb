package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.dialogs.DnBrowserDialog;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldif.LDIFChangeRecord;
import com.unboundid.ldif.LDIFReader;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.StreamResource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Search sub-tab for bulk operations on search results.
 */
public class BulkSearchTab extends VerticalLayout {

  // LDAP Control OIDs
  private static final String NO_OPERATION_CONTROL_OID = "1.3.6.1.4.1.4203.1.10.2";
  private static final String PERMISSIVE_MODIFY_CONTROL_OID = "1.2.840.113556.1.4.1413";

  private final LdapService ldapService;
  private final LoggingService loggingService;
  private final ConfigurationService configService;

  // Server configurations (multi-server support)
  private List<LdapServerConfig> serverConfigs;

  // UI Components
  private TextField searchBaseField;
  private Button searchBaseBrowseButton;
  private TextArea searchFilterField;
  private Checkbox continueOnErrorCheckbox;
  private Checkbox permissiveModifyCheckbox;
  private Checkbox noOperationCheckbox;
  private ComboBox<String> operationModeCombo;
  private TextArea ldifTemplateArea;
  private Button runButton;

  // Progress and download
  private ProgressBar progressBar;
  private VerticalLayout progressContainer;
  private Anchor downloadLink;

  /**
   * Constructs a new BulkSearchTab.
   *
   * @param ldapService    the LDAP service
   * @param loggingService the logging service
   */
  public BulkSearchTab(LdapService ldapService, LoggingService loggingService,
      ConfigurationService configService) {
    this.ldapService = ldapService;
    this.loggingService = loggingService;
    this.configService = configService;

    initializeComponents();
    setupLayout();
  }

  private void initializeComponents() {
    // Search fields
    searchBaseField = new TextField("Search Base");
    searchBaseField.setWidthFull();
    searchBaseField.setPlaceholder("dc=example,dc=com");

    searchBaseBrowseButton = new Button(new Icon(VaadinIcon.FOLDER_OPEN));
    searchBaseBrowseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    searchBaseBrowseButton.setTooltipText("Browse LDAP directory");
    searchBaseBrowseButton.addClickListener(e -> showDnBrowserDialog(searchBaseField));

    searchFilterField = new TextArea("Search Filter");
    searchFilterField.setWidthFull();
    searchFilterField.setHeight("100px");
    searchFilterField.setPlaceholder("(objectClass=person)");

    // Option checkboxes
    continueOnErrorCheckbox = new Checkbox("Continue on error");
    continueOnErrorCheckbox.setValue(false);

    permissiveModifyCheckbox = new Checkbox("Permissive modify request control");
    permissiveModifyCheckbox.setValue(false);

    noOperationCheckbox = new Checkbox("No operation request control");
    noOperationCheckbox.setValue(false);

    // Operation mode selector
    operationModeCombo = new ComboBox<>("Operation Mode");
    operationModeCombo.setItems("Execute Change", "Create LDIF");
    operationModeCombo.setValue("Execute Change");
    operationModeCombo.setWidthFull();

    // LDIF Template
    ldifTemplateArea = new TextArea("LDIF Template");
    ldifTemplateArea.setWidthFull();
    ldifTemplateArea.setHeight("150px");
    ldifTemplateArea.setValue("changetype: modify\nreplace: userpassword\nuserpassword: Secret123");
    ldifTemplateArea.setPlaceholder("changetype: modify\nreplace: userpassword\nuserpassword: Secret123");

    // Run button
    runButton = new Button("Run", new Icon(VaadinIcon.PLAY));
    runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    runButton.addClickListener(e -> performBulkOperation());

    // Progress components
    progressBar = new ProgressBar();
    progressBar.setIndeterminate(true);

    progressContainer = new VerticalLayout();
    progressContainer.setPadding(false);
    progressContainer.setSpacing(true);
    progressContainer.setDefaultHorizontalComponentAlignment(Alignment.CENTER);
    progressContainer.add(new Span("Processing bulk operation..."), progressBar);
    progressContainer.setVisible(false);

    // Download link
    downloadLink = new Anchor();
    downloadLink.getElement().setAttribute("download", true);
    downloadLink.setVisible(false);
    downloadLink.add(new Button("Download LDIF", new Icon(VaadinIcon.DOWNLOAD)));
  }

  private void setupLayout() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);
    addClassName("bulk-search-tab");

    // Main content layout
    VerticalLayout contentLayout = new VerticalLayout();
    contentLayout.setPadding(true);
    contentLayout.setSpacing(true);
    contentLayout.addClassName("bulk-search-field-group");

    // Options layout
    HorizontalLayout optionsLayout = new HorizontalLayout();
    optionsLayout.setWidthFull();
    optionsLayout.setSpacing(true);
    optionsLayout.add(continueOnErrorCheckbox, permissiveModifyCheckbox, noOperationCheckbox);

    // Action layout
    HorizontalLayout actionLayout = new HorizontalLayout();
    actionLayout.setDefaultVerticalComponentAlignment(Alignment.END);
    actionLayout.setSpacing(true);
    actionLayout.add(operationModeCombo, runButton);

    // Search base with browse button
    HorizontalLayout searchBaseLayout = new HorizontalLayout(searchBaseField, searchBaseBrowseButton);
    searchBaseLayout.setWidthFull();
    searchBaseLayout.setAlignItems(Alignment.END);
    searchBaseLayout.setSpacing(false);
    searchBaseLayout.expand(searchBaseField);

    contentLayout.add(
        new H4("Bulk Search Operations"),
        new Span("Perform bulk operations on LDAP search results"),
        searchBaseLayout,
        searchFilterField,
        optionsLayout,
        ldifTemplateArea,
        actionLayout,
        progressContainer,
        downloadLink);    add(contentLayout);
    setFlexGrow(1, contentLayout);
  }

  private void performBulkOperation() {
    // Refresh server configs to get the latest selection
    refreshServerConfigs();
    
    if (serverConfigs == null || serverConfigs.isEmpty()) {
      showError("Please select at least one LDAP server");
      return;
    }

    String searchBase = searchBaseField.getValue();
    String searchFilter = searchFilterField.getValue();
    String ldifTemplate = ldifTemplateArea.getValue();
    String operationMode = operationModeCombo.getValue();

    if (searchBase == null || searchBase.trim().isEmpty()) {
      showError("Search Base is required");
      return;
    }

    if (searchFilter == null || searchFilter.trim().isEmpty()) {
      showError("Search Filter is required");
      return;
    }

    if (ldifTemplate == null || ldifTemplate.trim().isEmpty()) {
      showError("LDIF Template is required");
      return;
    }

    loggingService.logInfo("BULK_SEARCH", "Starting bulk operation on " + serverConfigs.size() 
        + " server(s) - Base: " + searchBase + ", Filter: " + searchFilter + ", Mode: " + operationMode);

    showProgress();

    int totalSuccesses = 0;
    int totalErrors = 0;
    int totalEntries = 0;
    StringBuilder errorDetails = new StringBuilder();
    StringBuilder combinedLdif = new StringBuilder();

    try {
      // Process each server
      for (LdapServerConfig config : serverConfigs) {
        try {
          // Perform search to get entries
          List<LdapEntry> entries = ldapService.search(
              config,
              searchBase.trim(),
              searchFilter.trim(),
              SearchScope.SUB);

          if (entries.isEmpty()) {
            loggingService.logInfo("BULK_SEARCH", "No entries found on server: " + config.getName());
            continue;
          }

          loggingService.logInfo("BULK_SEARCH", "Found " + entries.size() 
              + " entries on server: " + config.getName());

          if ("Execute Change".equals(operationMode)) {
            int[] results = performExecuteChanges(config, entries, ldifTemplate);
            totalSuccesses += results[0];
            totalErrors += results[1];
            if (results[1] > 0) {
              errorDetails.append(config.getName()).append(": ").append(results[1]).append(" errors; ");
            }
          } else {
            // Generate LDIF for this server
            String serverLdif = generateLdifForServer(config, entries, ldifTemplate);
            if (combinedLdif.length() > 0) {
              combinedLdif.append("\n\n");
            }
            combinedLdif.append(serverLdif);
            totalEntries += entries.size();
          }

        } catch (Exception e) {
          totalErrors++;
          errorDetails.append(config.getName()).append(": ").append(e.getMessage()).append("; ");
          loggingService.logError("BULK_SEARCH", "Bulk operation failed on server: " + config.getName(),
              e.getMessage());
          if (!continueOnErrorCheckbox.getValue()) {
            throw e;
          }
        }
      }

      hideProgress();

      // Show final results
      if ("Execute Change".equals(operationMode)) {
        if (totalErrors > 0) {
          showInfo("Bulk operation completed with " + totalSuccesses + " successes and " 
              + totalErrors + " errors across " + serverConfigs.size() + " server(s). " + errorDetails.toString());
        } else {
          showSuccess("Bulk operation completed successfully. " + totalSuccesses 
              + " entries processed across " + serverConfigs.size() + " server(s)");
        }
      } else {
        // Create LDIF mode - create combined download
        if (totalEntries > 0) {
          createDownloadLink(combinedLdif.toString(), "bulk-operation.ldif");
          showSuccess("LDIF generated successfully for " + totalEntries 
              + " entries across " + serverConfigs.size() + " server(s)");
        } else {
          showInfo("No entries found matching the search criteria");
        }
      }

    } catch (Exception e) {
      hideProgress();
      showError("Bulk operation failed: " + e.getMessage());
    }
  }

  private int[] performExecuteChanges(LdapServerConfig config, List<LdapEntry> entries, 
      String ldifTemplate) throws Exception {
    int successCount = 0;
    int errorCount = 0;

    for (LdapEntry entry : entries) {
      try {
        // Generate LDIF for this entry
        String ldif = generateLdifForEntry(entry, ldifTemplate);

        // Parse and execute the LDIF
        byte[] contentBytes = ldif.getBytes(StandardCharsets.UTF_8);
        LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(contentBytes));

        try {
          LDIFChangeRecord changeRecord;
          while ((changeRecord = ldifReader.readChangeRecord()) != null) {
            switch (changeRecord.getChangeType()) {
              case MODIFY:
                if (changeRecord instanceof com.unboundid.ldif.LDIFModifyChangeRecord) {
                  com.unboundid.ldif.LDIFModifyChangeRecord modifyRecord = 
                      (com.unboundid.ldif.LDIFModifyChangeRecord) changeRecord;

                  // Prepare controls based on checkbox selections
                  List<Control> controls = new ArrayList<>();

                  // Check and add No Operation control if requested
                  if (noOperationCheckbox.getValue()) {
                    try {
                      boolean isSupported = ldapService.isControlSupported(config,
                          NO_OPERATION_CONTROL_OID);
                      if (isSupported) {
                        // Create No Operation control (OID: 1.3.6.1.4.1.4203.1.10.2)
                        Control noOpControl = new Control(NO_OPERATION_CONTROL_OID, false);
                        controls.add(noOpControl);
                      } else {
                        throw new Exception("LDAP server does not support No Operation request control (OID: "
                            + NO_OPERATION_CONTROL_OID + ")");
                      }
                    } catch (LDAPException e) {
                      throw new Exception("Failed to check control support: " + e.getMessage());
                    }
                  }

                  // Check and add Permissive Modify control if requested
                  if (permissiveModifyCheckbox.getValue()) {
                    try {
                      boolean isSupported = ldapService.isControlSupported(config,
                          PERMISSIVE_MODIFY_CONTROL_OID);
                      if (isSupported) {
                        // Create Permissive Modify control (OID: 1.2.840.113556.1.4.1413)
                        Control permissiveModifyControl = new Control(PERMISSIVE_MODIFY_CONTROL_OID, false);
                        controls.add(permissiveModifyControl);
                      } else {
                        throw new Exception("LDAP server does not support Permissive Modify request control (OID: "
                            + PERMISSIVE_MODIFY_CONTROL_OID + ")");
                      }
                    } catch (LDAPException e) {
                      throw new Exception("Failed to check control support: " + e.getMessage());
                    }
                  }

                  // Perform modify with controls
                  ldapService.modifyEntry(config,
                      modifyRecord.getDN(),
                      Arrays.asList(modifyRecord.getModifications()),
                      controls.isEmpty() ? null : controls.toArray(new Control[0]));
                }
                break;

              case ADD:
                if (changeRecord instanceof com.unboundid.ldif.LDIFAddChangeRecord) {
                  com.unboundid.ldif.LDIFAddChangeRecord addRecord = 
                      (com.unboundid.ldif.LDIFAddChangeRecord) changeRecord;

                  LdapEntry newEntry = new LdapEntry();
                  newEntry.setDn(addRecord.getDN());

                  for (com.unboundid.ldap.sdk.Attribute attr : addRecord.getAttributes()) {
                    for (String value : attr.getValues()) {
                      newEntry.addAttribute(attr.getName(), value);
                    }
                  }

                  ldapService.addEntry(config, newEntry);
                }
                break;

              case DELETE:
                ldapService.deleteEntry(config, changeRecord.getDN());
                break;

              default:
                throw new Exception("Unsupported change type: " + changeRecord.getChangeType());
            }
          }
        } finally {
          ldifReader.close();
        }

        successCount++;

      } catch (Exception e) {
        errorCount++;
        if (!continueOnErrorCheckbox.getValue()) {
          throw e;
        }
        // Log error but continue if continue on error is enabled
        loggingService.logError("BULK_SEARCH", "Error processing entry " + entry.getDn() 
            + " on server " + config.getName(), e.getMessage());
      }
    }

    return new int[]{successCount, errorCount};
  }

  private String generateLdifForServer(LdapServerConfig config, List<LdapEntry> entries, 
      String ldifTemplate) throws Exception {
    StringBuilder ldifContent = new StringBuilder();

    // Add server comment header
    ldifContent.append("# Server: ").append(config.getName()).append("\n");
    ldifContent.append("# Entries: ").append(entries.size()).append("\n\n");

    for (int i = 0; i < entries.size(); i++) {
      LdapEntry entry = entries.get(i);
      String ldif = generateLdifForEntry(entry, ldifTemplate);
      ldifContent.append(ldif);

      // Add empty line between LDIF change records (except after the last one)
      if (i < entries.size() - 1) {
        ldifContent.append("\n\n");
      } else {
        ldifContent.append("\n");
      }
    }

    loggingService.logInfo("BULK_SEARCH", "LDIF generated for " + entries.size() 
        + " entries on server: " + config.getName());

    return ldifContent.toString();
  }

  private String generateLdifForEntry(LdapEntry entry, String ldifTemplate) {
    String ldif = ldifTemplate;

    // Replace DN placeholder
    ldif = ldif.replace("{DN}", entry.getDn());

    // Replace attribute placeholders
    for (String attrName : entry.getAttributes().keySet()) {
      List<String> values = entry.getAttributes().get(attrName);
      if (!values.isEmpty()) {
        String placeholder = "{" + attrName.toUpperCase() + "}";
        ldif = ldif.replace(placeholder, values.get(0));
      }
    }

    // Ensure DN is present
    if (!ldif.startsWith("dn:")) {
      ldif = "dn: " + entry.getDn() + "\n" + ldif;
    }

    return ldif;
  }

  private void createDownloadLink(String content, String fileName) {
    StreamResource resource = new StreamResource(fileName,
        () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

    downloadLink.setHref(resource);
    downloadLink.setVisible(true);
  }

  private void showProgress() {
    progressContainer.setVisible(true);
    runButton.setEnabled(false);
    downloadLink.setVisible(false);
  }

  private void hideProgress() {
    progressContainer.setVisible(false);
    runButton.setEnabled(true);
  }

  /**
   * Sets the list of server configurations for multi-server operations.
   *
   * @param serverConfigs list of server configurations
   */
  public void setServerConfigs(List<LdapServerConfig> serverConfigs) {
    this.serverConfigs = serverConfigs;
    
    // Set default search base from first server if available
    if (searchBaseField != null && serverConfigs != null && !serverConfigs.isEmpty()) {
      LdapServerConfig firstServer = serverConfigs.get(0);
      if (firstServer.getBaseDn() != null && !firstServer.getBaseDn().isEmpty()) {
        searchBaseField.setValue(firstServer.getBaseDn());
      }
    }
  }

  /**
   * Refreshes the server configurations from the current session.
   * This ensures we always have the latest server selection.
   */
  private void refreshServerConfigs() {
    Set<String> selectedServerNames = MainLayout.getSelectedServers();
    List<LdapServerConfig> selectedServers = new ArrayList<>();
    
    // Load all configurations and filter by selected names
    List<LdapServerConfig> allConfigs = configService.loadConfigurations();
    for (LdapServerConfig config : allConfigs) {
      if (selectedServerNames.contains(config.getName())) {
        selectedServers.add(config);
      }
    }
    
    setServerConfigs(selectedServers);
  }

  public void clear() {
    searchBaseField.clear();
    searchFilterField.clear();
    ldifTemplateArea.setValue("changetype: modify\nreplace: userpassword\nuserpassword: Secret123");
    continueOnErrorCheckbox.setValue(false);
    permissiveModifyCheckbox.setValue(false);
    noOperationCheckbox.setValue(false);
    operationModeCombo.setValue("Execute Change");
    downloadLink.setVisible(false);
    hideProgress();
  }

  /**
   * Shows a DN browser dialog to select a DN for the given field.
   *
   * @param targetField the text field to populate with the selected DN
   */
  private void showDnBrowserDialog(TextField targetField) {
    if (serverConfigs == null || serverConfigs.isEmpty()) {
      showError("Please select at least one LDAP server");
      return;
    }

    new DnBrowserDialog(ldapService)
        .withServerConfigs(serverConfigs)
        .onDnSelected(dn -> targetField.setValue(dn))
        .open();
  }

  private void showSuccess(String message) {
    Notification notification = Notification.show(message, 3000, Notification.Position.TOP_END);
    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  private void showError(String message) {
    Notification notification = Notification.show(message, 5000, Notification.Position.TOP_END);
    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
  }

  private void showInfo(String message) {
    Notification notification = Notification.show(message, 4000, Notification.Position.TOP_END);
    notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
  }
}