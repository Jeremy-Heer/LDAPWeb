package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.unboundid.ldif.LDIFChangeRecord;
import com.unboundid.ldif.LDIFReader;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.server.StreamResource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Generate sub-tab for bulk generation of LDAP entries.
 */
public class BulkGenerateTab extends VerticalLayout {

  private final LdapService ldapService;
  private final LoggingService loggingService;

  // Server configurations (multi-server support)
  private List<LdapServerConfig> serverConfigs;

  // UI Components
  private IntegerField countStartField;
  private IntegerField countEndField;
  private TextArea ldifTemplateArea;
  private TextArea ldifPreviewArea;
  private ComboBox<String> operationModeCombo;
  private Button runButton;

  // Progress
  private ProgressBar progressBar;
  private VerticalLayout progressContainer;
  private Anchor downloadLink;

  public BulkGenerateTab(LdapService ldapService, LoggingService loggingService) {
    this.ldapService = ldapService;
    this.loggingService = loggingService;
    this.serverConfigs = new ArrayList<>();

    initializeComponents();
    setupLayout();
  }

  private void initializeComponents() {
    // Count fields
    countStartField = new IntegerField("Count Start");
    countStartField.setWidthFull();
    countStartField.setPlaceholder("1");
    countStartField.setMin(1);
    countStartField.setValue(1);
    countStartField.setStepButtonsVisible(true);

    countEndField = new IntegerField("Count End");
    countEndField.setWidthFull();
    countEndField.setPlaceholder("100");
    countEndField.setMin(1);
    countEndField.setValue(100);
    countEndField.setStepButtonsVisible(true);

    // LDIF Template
    ldifTemplateArea = new TextArea("LDIF Template");
    ldifTemplateArea.setWidthFull();
    ldifTemplateArea.setHeight("200px");
    ldifTemplateArea.setValue("dn: uid=user.{COUNT},ou=people,dc=example,dc=com\n" +
        "changetype: add\n" +
        "objectClass: inetOrgPerson\n" +
        "objectClass: organizationalPerson\n" +
        "objectClass: person\n" +
        "objectClass: top\n" +
        "uid: user.{COUNT}\n" +
        "cn: user.{COUNT}\n" +
        "sn: user.{COUNT}");
    ldifTemplateArea.setPlaceholder("Enter LDIF template with {COUNT} placeholder");

    // Add value change listener to update preview
    countStartField.addValueChangeListener(e -> updatePreview());
    countEndField.addValueChangeListener(e -> updatePreview());
    ldifTemplateArea.addValueChangeListener(e -> updatePreview());

    // LDIF Preview
    ldifPreviewArea = new TextArea("LDIF Preview");
    ldifPreviewArea.setWidthFull();
    ldifPreviewArea.setHeight("150px");
    ldifPreviewArea.setReadOnly(true);
    ldifPreviewArea.setHelperText("Preview of the first few LDIF entries to be generated");

    // Operation mode selector
    operationModeCombo = new ComboBox<>("Operation Mode");
    operationModeCombo.setItems("Execute Change", "Create LDIF");
    operationModeCombo.setValue("Execute Change");
    operationModeCombo.setWidthFull();

    // Run button
    runButton = new Button("Run", new Icon(VaadinIcon.PLAY));
    runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    runButton.addClickListener(e -> performBulkGenerate());

    // Progress components
    progressBar = new ProgressBar();
    progressBar.setIndeterminate(true);

    progressContainer = new VerticalLayout();
    progressContainer.setPadding(false);
    progressContainer.setSpacing(true);
    progressContainer.setDefaultHorizontalComponentAlignment(Alignment.CENTER);
    progressContainer.add(new Span("Generating and importing entries..."), progressBar);
    progressContainer.setVisible(false);

    // Download link (for LDIF output)
    downloadLink = new Anchor();
    downloadLink.getElement().setAttribute("download", true);
    downloadLink.setVisible(false);
    downloadLink.add(new Button("Download LDIF", new Icon(VaadinIcon.DOWNLOAD)));

    // Initial preview update
    updatePreview();
  }

  private void setupLayout() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);
    addClassName("bulk-generate-tab");

    // Main content layout
    VerticalLayout contentLayout = new VerticalLayout();
    contentLayout.setPadding(true);
    contentLayout.setSpacing(true);
    contentLayout.addClassName("bulk-generate-field-group");

    // Count fields layout
    HorizontalLayout countLayout = new HorizontalLayout();
    countLayout.setWidthFull();
    countLayout.setSpacing(true);
    countLayout.add(countStartField, countEndField);

    // Action layout
    HorizontalLayout actionLayout = new HorizontalLayout();
    actionLayout.setDefaultVerticalComponentAlignment(Alignment.END);
    actionLayout.setSpacing(true);
    actionLayout.add(operationModeCombo, runButton);

    contentLayout.add(
        new H4("Bulk Generate Operations"),
        new Span("Generate multiple LDAP entries using a template with count placeholders"),
        countLayout,
        ldifTemplateArea,
        ldifPreviewArea,
        actionLayout,
        progressContainer,
        downloadLink);

    add(contentLayout);
    setFlexGrow(1, contentLayout);
  }

  private void updatePreview() {
    try {
      Integer startCount = countStartField.getValue();
      Integer endCount = countEndField.getValue();
      String template = ldifTemplateArea.getValue();

      if (startCount == null || endCount == null || template == null || template.trim().isEmpty()) {
        ldifPreviewArea.setValue("");
        return;
      }

      if (startCount > endCount) {
        ldifPreviewArea.setValue("Error: Count Start must be less than or equal to Count End");
        return;
      }

      StringBuilder preview = new StringBuilder();
      int previewCount = Math.min(3, endCount - startCount + 1); // Show first 3 entries

      for (int i = 0; i < previewCount; i++) {
        int currentCount = startCount + i;
        String ldif = template.replace("{COUNT}", String.valueOf(currentCount));
        preview.append(ldif);
        if (i < previewCount - 1) {
          preview.append("\n\n");
        }
      }

      if (endCount - startCount + 1 > 3) {
        preview.append("\n\n... and ").append(endCount - startCount + 1 - 3).append(" more entries");
      }

      ldifPreviewArea.setValue(preview.toString());

    } catch (Exception e) {
      ldifPreviewArea.setValue("Error generating preview: " + e.getMessage());
    }
  }

  private void performBulkGenerate() {
    if (serverConfigs == null || serverConfigs.isEmpty()) {
      NotificationHelper.showError("Please select at least one LDAP server");
      return;
    }

    Integer startCount = countStartField.getValue();
    Integer endCount = countEndField.getValue();
    String template = ldifTemplateArea.getValue();

    if (startCount == null) {
      NotificationHelper.showError("Count Start is required");
      return;
    }

    if (endCount == null) {
      NotificationHelper.showError("Count End is required");
      return;
    }

    if (startCount > endCount) {
      NotificationHelper.showError("Count Start must be less than or equal to Count End");
      return;
    }

    if (template == null || template.trim().isEmpty()) {
      NotificationHelper.showError("LDIF Template is required");
      return;
    }

    String operationMode = operationModeCombo.getValue();
    int totalEntries = endCount - startCount + 1;

    loggingService.logInfo("BULK_GENERATE", "Starting bulk generation - Mode: " + operationMode 
        + ", Servers: " + serverConfigs.size() + ", Start: " + startCount + ", End: " + endCount 
        + ", Total: " + totalEntries);

    showProgress();

    // Check if we're in Create LDIF mode
    if ("Create LDIF".equals(operationMode)) {
      try {
        StringBuilder combinedLdif = new StringBuilder();

        // Generate LDIF for all entries
        for (int currentCount = startCount; currentCount <= endCount; currentCount++) {
          String ldif = template.replace("{COUNT}", String.valueOf(currentCount));
          if (combinedLdif.length() > 0) {
            combinedLdif.append("\n\n");
          }
          combinedLdif.append(ldif);
        }

        hideProgress();
        createDownloadLink(combinedLdif.toString(), "bulk-generate.ldif");
        NotificationHelper.showSuccess("LDIF generated successfully for " + totalEntries + " entries");
        loggingService.logInfo("BULK_GENERATE", "LDIF generated for " + totalEntries + " entries");
        return;
      } catch (Exception e) {
        hideProgress();
        NotificationHelper.showError("Failed to generate LDIF: " + e.getMessage());
        loggingService.logError("BULK_GENERATE", "LDIF generation failed", e.getMessage());
        return;
      }
    }

    int totalSuccessCount = 0;
    int totalErrorCount = 0;
    StringBuilder errorDetails = new StringBuilder();

    // Process each server
    for (LdapServerConfig config : serverConfigs) {
      try {
        int successCount = 0;
        int errorCount = 0;

        for (int currentCount = startCount; currentCount <= endCount; currentCount++) {
          try {
            // Generate LDIF for this count
            String ldif = template.replace("{COUNT}", String.valueOf(currentCount));

            // Parse and execute the LDIF
            byte[] contentBytes = ldif.getBytes(StandardCharsets.UTF_8);
            LDIFReader ldifReader = new LDIFReader(new ByteArrayInputStream(contentBytes));

            try {
              LDIFChangeRecord changeRecord;
              while ((changeRecord = ldifReader.readChangeRecord()) != null) {
                switch (changeRecord.getChangeType()) {
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

                  case MODIFY:
                    if (changeRecord instanceof com.unboundid.ldif.LDIFModifyChangeRecord) {
                      com.unboundid.ldif.LDIFModifyChangeRecord modifyRecord = 
                          (com.unboundid.ldif.LDIFModifyChangeRecord) changeRecord;

                      ldapService.modifyEntry(config,
                          modifyRecord.getDN(),
                          java.util.Arrays.asList(modifyRecord.getModifications()));
                    }
                    break;

                  case DELETE:
                    ldapService.deleteEntry(config, changeRecord.getDN());
                    break;

                  case MODIFY_DN:
                    if (changeRecord instanceof com.unboundid.ldif.LDIFModifyDNChangeRecord) {
                      com.unboundid.ldif.LDIFModifyDNChangeRecord modDnRecord =
                          (com.unboundid.ldif.LDIFModifyDNChangeRecord) changeRecord;
                      ldapService.modifyDN(config,
                          modDnRecord.getDN(),
                          modDnRecord.getNewRDN(),
                          modDnRecord.getNewSuperiorDN(),
                          modDnRecord.deleteOldRDN());
                    }
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
            loggingService.logError("BULK_GENERATE", "Error generating entry for count " 
                + currentCount + " on server " + config.getName(), e.getMessage());
            // Continue processing remaining entries
          }
        }

        totalSuccessCount += successCount;
        totalErrorCount += errorCount;

        if (errorCount > 0) {
          errorDetails.append("\nServer '").append(config.getName()).append("': ")
              .append(successCount).append(" successes, ")
              .append(errorCount).append(" errors");
        }

      } catch (Exception e) {
        totalErrorCount += (endCount - startCount + 1);
        loggingService.logError("BULK_GENERATE", "Bulk generation failed on server: " + config.getName(),
            e.getMessage());
        errorDetails.append("\nServer '").append(config.getName())
            .append("': Complete failure - ").append(e.getMessage());
      }
    }

    hideProgress();

    // Show final results
    if (totalErrorCount > 0) {
      loggingService.logWarning("BULK_GENERATE", "Bulk generation completed with errors - " +
          totalSuccessCount + " successes and " + totalErrorCount + " errors across " + 
          serverConfigs.size() + " server(s)");
      NotificationHelper.showInfo("Bulk generation completed with " + totalSuccessCount + " successes and " + 
          totalErrorCount + " errors across " + serverConfigs.size() + " server(s)" + 
          (errorDetails.length() > 0 ? errorDetails.toString() : ""));
    } else {
      loggingService.logInfo("BULK_GENERATE", "Bulk generation completed successfully - " +
          totalSuccessCount + " entries created across " + serverConfigs.size() + " server(s)");
      NotificationHelper.showSuccess("Bulk generation completed successfully. " + totalSuccessCount + 
          " entries created across " + serverConfigs.size() + " server(s)");
    }
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
   * Sets the server configurations for multi-server support.
   *
   * @param serverConfigs the list of LDAP server configurations
   */
  public void setServerConfigs(List<LdapServerConfig> serverConfigs) {
    this.serverConfigs = serverConfigs != null ? serverConfigs : new ArrayList<>();
  }

  public void clear() {
    countStartField.setValue(1);
    countEndField.setValue(100);
    ldifTemplateArea.setValue("dn: uid=user.{COUNT},ou=people,dc=example,dc=com\n" +
        "changetype: add\n" +
        "objectClass: inetOrgPerson\n" +
        "objectClass: organizationalPerson\n" +
        "objectClass: person\n" +
        "objectClass: top\n" +
        "uid: user.{COUNT}\n" +
        "cn: user.{COUNT}\n" +
        "sn: user.{COUNT}");
    operationModeCombo.setValue("Execute Change");
    downloadLink.setVisible(false);
    hideProgress();
    updatePreview();
  }
}