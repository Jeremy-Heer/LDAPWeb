package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.utils.NotificationHelper;
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
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Import tab for importing LDAP data from LDIF and CSV files.
 */
public class ImportTab extends VerticalLayout {

  // LDAP Control OIDs
  private static final String NO_OPERATION_CONTROL_OID = "1.3.6.1.4.1.4203.1.10.2";
  private static final String PERMISSIVE_MODIFY_CONTROL_OID = "1.2.840.113556.1.4.1413";

  private final LdapService ldapService;
  private final LoggingService loggingService;
  private final ConfigurationService configService;

  // Server configurations (multi-server support)
  private List<LdapServerConfig> serverConfigs;

  // UI Components
  private ComboBox<String> importModeSelector;
  private VerticalLayout modeContainer;

  // LDIF Mode Components
  private VerticalLayout ldifModeLayout;
  private VerticalLayout ldifInputContainer;
  private Upload ldifUpload;
  private MemoryBuffer ldifBuffer;
  private TextArea ldifTextArea;
  private Checkbox ldifContinueOnError;
  private Checkbox ldifPermissiveModify;
  private Checkbox ldifNoOperation;
  private ComboBox<String> ldifOperationModeCombo;
  private Button ldifRunButton;

  // CSV Mode Components
  private VerticalLayout csvModeLayout;
  private VerticalLayout csvInputContainer;
  private Upload csvUpload;
  private MemoryBuffer csvBuffer;
  private TextArea csvTextArea;
  private Checkbox csvExcludeHeader;
  private Checkbox csvQuotedValues;
  private Checkbox csvContinueOnError;
  private Checkbox csvPermissiveModify;
  private Checkbox csvNoOperation;
  private VerticalLayout csvPreviewContainer;
  private Grid<Map<String, String>> csvPreviewGrid;
  private ComboBox<String> dnMethodSelector;
  private VerticalLayout dnMethodContainer;
  private TextField searchBaseField;
  private TextField searchFilterField;
  private TextArea ldifTemplateArea;
  private TextArea previewLdifArea;
  private ComboBox<String> csvOperationModeCombo;
  private Button csvRunButton;

  // CSV data and settings
  private List<Map<String, String>> csvData;
  private String rawCsvContent;
  private String rawLdifContent;
  private List<String> csvColumnOrder; // Maintain original CSV column order

  // Progress
  private ProgressBar progressBar;
  private VerticalLayout progressContainer;
  private Anchor ldifDownloadLink;
  private Anchor csvDownloadLink;

  public ImportTab(LdapService ldapService, LoggingService loggingService, 
      ConfigurationService configService) {
    this.ldapService = ldapService;
    this.loggingService = loggingService;
    this.configService = configService;
    this.csvData = new ArrayList<>();
    this.csvColumnOrder = new ArrayList<>();
    initializeComponents();
    setupLayout();
  }

  private void initializeComponents() {
    // Import Mode Selector
    importModeSelector = new ComboBox<>("Import Type");
    importModeSelector.setItems("Upload LDIF", "Enter LDIF", "Upload CSV", "Enter CSV");
    importModeSelector.setValue("Upload LDIF");
    importModeSelector.addValueChangeListener(e -> switchMode(e.getValue()));

    // Initialize download links first (before mode components that use them)
    ldifDownloadLink = new Anchor();
    ldifDownloadLink.getElement().setAttribute("download", true);
    ldifDownloadLink.setVisible(false);
    ldifDownloadLink.add(new Button("Download LDIF", new Icon(VaadinIcon.DOWNLOAD)));

    csvDownloadLink = new Anchor();
    csvDownloadLink.getElement().setAttribute("download", true);
    csvDownloadLink.setVisible(false);
    csvDownloadLink.add(new Button("Download LDIF", new Icon(VaadinIcon.DOWNLOAD)));

    // Initialize mode-specific components
    initializeLdifModeComponents();
    initializeCsvModeComponents();

    // Progress components
    progressBar = new ProgressBar();
    progressBar.setIndeterminate(true);

    progressContainer = new VerticalLayout();
    progressContainer.setPadding(false);
    progressContainer.setSpacing(true);
    progressContainer.setDefaultHorizontalComponentAlignment(Alignment.CENTER);
    progressContainer.add(new Span("Processing import..."), progressBar);
    progressContainer.setVisible(false);
  }

  private void initializeLdifModeComponents() {
    ldifModeLayout = new VerticalLayout();
    ldifModeLayout.setPadding(true);
    ldifModeLayout.setSpacing(true);
    ldifModeLayout.addClassName("import-field-group");

    // Container for LDIF input components
    ldifInputContainer = new VerticalLayout();
    ldifInputContainer.setPadding(false);
    ldifInputContainer.setSpacing(true);

    // LDIF Upload
    ldifBuffer = new MemoryBuffer();
    ldifUpload = new Upload(ldifBuffer);
    ldifUpload.setAcceptedFileTypes("text/ldif", ".ldif", "text/plain", ".txt");
    ldifUpload.setMaxFiles(1);
    ldifUpload.setWidthFull();
    ldifUpload.setDropLabel(new Span("Drop LDIF file here or click to browse"));

    ldifUpload.addSucceededListener(event -> {
      try {
        processLdifFile();
      } catch (Exception ex) {
        NotificationHelper.showError("Error processing LDIF file: " + ex.getMessage());
      }
    });

    // LDIF Text Area
    ldifTextArea = new TextArea("LDIF Content");
    ldifTextArea.setWidthFull();
    ldifTextArea.setHeight("300px");
    ldifTextArea.setPlaceholder("Enter LDIF content here...\n\nExample:\ndn: cn=John Doe,ou=People,dc=example,dc=com\nobjectClass: person\nobjectClass: organizationalPerson\nobjectClass: inetOrgPerson\ncn: John Doe\nsn: Doe\ngivenName: John\nmail: john.doe@example.com");
    ldifTextArea.addValueChangeListener(event -> {
      rawLdifContent = event.getValue();
      updateLdifImportButtonState();
    });

    // LDIF Options
    ldifContinueOnError = new Checkbox("Continue on error");
    ldifContinueOnError.setValue(false);

    ldifPermissiveModify = new Checkbox("Permissive modify request control");
    ldifPermissiveModify.setValue(false);

    ldifNoOperation = new Checkbox("No operation request control");
    ldifNoOperation.setValue(false);

    // LDIF Operation mode selector
    ldifOperationModeCombo = new ComboBox<>("Operation Mode");
    ldifOperationModeCombo.setItems("Execute Change", "Create LDIF");
    ldifOperationModeCombo.setValue("Execute Change");
    ldifOperationModeCombo.setWidthFull();

    ldifRunButton = new Button("Run", new Icon(VaadinIcon.PLAY));
    ldifRunButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    ldifRunButton.addClickListener(e -> performLdifImport());
    ldifRunButton.setEnabled(false);

    // Initially show upload mode components
    ldifInputContainer.add(
        new Span("Upload an LDIF file to import LDAP entries"),
        ldifUpload);
    ldifTextArea.setVisible(false);

    // LDIF Action layout
    HorizontalLayout ldifActionLayout = new HorizontalLayout();
    ldifActionLayout.setDefaultVerticalComponentAlignment(Alignment.END);
    ldifActionLayout.setSpacing(true);
    ldifActionLayout.add(ldifOperationModeCombo, ldifRunButton);

    ldifModeLayout.add(
        new H4("LDIF Import"),
        ldifInputContainer,
        ldifTextArea,
        ldifContinueOnError,
        ldifPermissiveModify,
        ldifNoOperation,
        ldifActionLayout,
        ldifDownloadLink);
  }

  private void initializeCsvModeComponents() {
    csvModeLayout = new VerticalLayout();
    csvModeLayout.setPadding(true);
    csvModeLayout.setSpacing(true);
    csvModeLayout.addClassName("import-field-group");

    // Container for CSV input components
    csvInputContainer = new VerticalLayout();
    csvInputContainer.setPadding(false);
    csvInputContainer.setSpacing(true);

    // CSV Upload
    csvBuffer = new MemoryBuffer();
    csvUpload = new Upload(csvBuffer);
    csvUpload.setAcceptedFileTypes("text/csv", ".csv");
    csvUpload.setMaxFiles(1);
    csvUpload.setWidthFull();
    csvUpload.setDropLabel(new Span("Drop CSV file here or click to browse"));

    csvUpload.addSucceededListener(event -> {
      try {
        processCsvFile();
      } catch (Exception ex) {
        NotificationHelper.showError("Error processing CSV file: " + ex.getMessage());
      }
    });

    // CSV Text Area
    csvTextArea = new TextArea("CSV Content");
    csvTextArea.setWidthFull();
    csvTextArea.setHeight("300px");
    csvTextArea.setPlaceholder("Enter CSV content here...\n\nExample:\nuid,password\njdoe,Secret123\nmsmith,Pass456");
    csvTextArea.addValueChangeListener(event -> {
      rawCsvContent = event.getValue();
      if (rawCsvContent != null && !rawCsvContent.trim().isEmpty()) {
        try {
          processCsvFile();
        } catch (Exception ex) {
          NotificationHelper.showError("Error processing CSV content: " + ex.getMessage());
        }
      } else {
        // Clear preview if content is empty
        csvData.clear();
        csvPreviewGrid.setItems(csvData);
        csvPreviewContainer.setVisible(false);
        previewLdifArea.setValue("");
        updateCsvImportButtonState();
      }
    });

    // CSV Options
    csvExcludeHeader = new Checkbox("Exclude first row (header row)");
    csvExcludeHeader.setValue(false);
    csvExcludeHeader.addValueChangeListener(e -> {
      if (csvData != null && !csvData.isEmpty()) {
        try {
          processCsvFile();
        } catch (Exception ex) {
          NotificationHelper.showError("Error reprocessing CSV file: " + ex.getMessage());
        }
      }
    });

    csvQuotedValues = new Checkbox("Values are surrounded by quotes");
    csvQuotedValues.setValue(true);
    csvQuotedValues.addValueChangeListener(e -> {
      if (csvData != null && !csvData.isEmpty()) {
        try {
          processCsvFile();
        } catch (Exception ex) {
          NotificationHelper.showError("Error reprocessing CSV file: " + ex.getMessage());
        }
      }
    });

    csvContinueOnError = new Checkbox("Continue on error");
    csvContinueOnError.setValue(false);

    csvPermissiveModify = new Checkbox("Permissive modify request control");
    csvPermissiveModify.setValue(false);

    csvNoOperation = new Checkbox("No operation request control");
    csvNoOperation.setValue(false);

    // CSV Preview
    csvPreviewContainer = new VerticalLayout();
    csvPreviewContainer.setPadding(false);
    csvPreviewContainer.setSpacing(true);
    csvPreviewContainer.setVisible(false);

    csvPreviewGrid = new Grid<>();
    csvPreviewGrid.setHeight("200px");
    csvPreviewGrid.setWidthFull();
    csvPreviewGrid.addClassName("csv-preview-grid");

    csvPreviewContainer.add(
        new H4("File Preview"),
        csvPreviewGrid);

    // DN Method Selector
    dnMethodSelector = new ComboBox<>("DN Method");
    dnMethodSelector.setItems("CSV Column", "CSV Column and Search");
    dnMethodSelector.setValue("CSV Column");
    dnMethodSelector.setHelperText("Select how to determine the DN for each entry");
    dnMethodSelector.addValueChangeListener(e -> switchDnMethod(e.getValue()));

    dnMethodContainer = new VerticalLayout();
    dnMethodContainer.setPadding(false);
    dnMethodContainer.setSpacing(true);

    // Search fields for "CSV Column and Search" method
    searchBaseField = new TextField("Search Base");
    searchBaseField.setWidthFull();
    searchBaseField.setPlaceholder("dc=example,dc=com");
    searchBaseField.setVisible(false);

    searchFilterField = new TextField("Filter");
    searchFilterField.setWidthFull();
    searchFilterField.setValue("(&(objectClass=person)(uid={C1}))");
    searchFilterField.setHelperText("Use {C1}, {C2}, {C3}, etc. to reference CSV columns");
    searchFilterField.setVisible(false);
    searchFilterField.addValueChangeListener(e -> updatePreviewLdif());

    dnMethodContainer.add(searchBaseField, searchFilterField);

    // LDIF Template
    ldifTemplateArea = new TextArea("LDIF Template");
    ldifTemplateArea.setWidthFull();
    ldifTemplateArea.setHeight("150px");
    ldifTemplateArea.setValue("dn: {DN}\nchangetype: modify\nreplace: userpassword\nuserpassword: {C2}");
    ldifTemplateArea.setHelperText("Use {DN} for the computed DN and {C1}, {C2}, etc. for CSV columns");
    ldifTemplateArea.addValueChangeListener(e -> updatePreviewLdif());

    // Preview LDIF
    previewLdifArea = new TextArea("Preview LDIF");
    previewLdifArea.setWidthFull();
    previewLdifArea.setHeight("150px");
    previewLdifArea.setReadOnly(true);
    previewLdifArea.setHelperText("Preview of the LDIF to be generated");

    // CSV Operation mode selector
    csvOperationModeCombo = new ComboBox<>("Operation Mode");
    csvOperationModeCombo.setItems("Execute Change", "Create LDIF");
    csvOperationModeCombo.setValue("Execute Change");
    csvOperationModeCombo.setWidthFull();

    csvRunButton = new Button("Run", new Icon(VaadinIcon.PLAY));
    csvRunButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    csvRunButton.addClickListener(e -> performCsvImport());
    csvRunButton.setEnabled(false);

    // Initially show upload mode components
    csvInputContainer.add(
        new Span("Upload a CSV file to import LDAP entries"),
        csvUpload);
    csvTextArea.setVisible(false);

    // CSV Action layout
    HorizontalLayout csvActionLayout = new HorizontalLayout();
    csvActionLayout.setDefaultVerticalComponentAlignment(Alignment.END);
    csvActionLayout.setSpacing(true);
    csvActionLayout.add(csvOperationModeCombo, csvRunButton);

    csvModeLayout.add(
        new H4("CSV Import"),
        csvInputContainer,
        csvTextArea,
        csvExcludeHeader,
        csvQuotedValues,
        csvContinueOnError,
        csvPermissiveModify,
        csvNoOperation,
        csvPreviewContainer,
        dnMethodSelector,
        dnMethodContainer,
        ldifTemplateArea,
        previewLdifArea,
        csvActionLayout,
        csvDownloadLink);
  }

  private void setupLayout() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);
    addClassName("import-tab");

    // Title with icon
    HorizontalLayout titleLayout = new HorizontalLayout();
    titleLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    titleLayout.setSpacing(true);

    Icon importIcon = new Icon(VaadinIcon.UPLOAD);
    importIcon.setSize("20px");
    importIcon.getStyle().set("color", "#28a745");

    H3 title = new H3("LDAP Import");
    title.addClassNames(LumoUtility.Margin.NONE);
    title.getStyle().set("color", "#333");

    titleLayout.add(importIcon, title);

    // Mode container
    modeContainer = new VerticalLayout();
    modeContainer.setPadding(false);
    modeContainer.setSpacing(false);
    modeContainer.setSizeFull();

    // Initially show LDIF mode
    modeContainer.add(ldifModeLayout);

    add(titleLayout, importModeSelector, modeContainer, progressContainer);
    setFlexGrow(1, modeContainer);
  }

  private void switchMode(String mode) {
    modeContainer.removeAll();

    if ("Upload LDIF".equals(mode)) {
      // Show upload components, hide text area
      ldifInputContainer.removeAll();
      ldifInputContainer.add(
          new Span("Upload an LDIF file to import LDAP entries"),
          ldifUpload);
      ldifTextArea.setVisible(false);
      ldifTextArea.setValue("");
      rawLdifContent = null;
      modeContainer.add(ldifModeLayout);
    } else if ("Enter LDIF".equals(mode)) {
      // Show text area, hide upload components
      ldifInputContainer.removeAll();
      ldifInputContainer.add(new Span("Enter LDIF content directly below"));
      ldifTextArea.setVisible(true);
      rawLdifContent = ldifTextArea.getValue();
      modeContainer.add(ldifModeLayout);
    } else if ("Upload CSV".equals(mode)) {
      // Show upload components, hide text area
      csvInputContainer.removeAll();
      csvInputContainer.add(
          new Span("Upload a CSV file to import LDAP entries"),
          csvUpload);
      csvTextArea.setVisible(false);
      csvTextArea.setValue("");
      rawCsvContent = null;
      modeContainer.add(csvModeLayout);
    } else if ("Enter CSV".equals(mode)) {
      // Show text area, hide upload components
      csvInputContainer.removeAll();
      csvInputContainer.add(new Span("Enter CSV content directly below"));
      csvTextArea.setVisible(true);
      rawCsvContent = csvTextArea.getValue();
      modeContainer.add(csvModeLayout);
    }
    
    updateLdifImportButtonState();
  }

  private void updateLdifImportButtonState() {
    boolean hasContent = false;
    
    String currentMode = importModeSelector.getValue();
    if ("Upload LDIF".equals(currentMode)) {
      // Check if file has been uploaded
      hasContent = rawLdifContent != null && !rawLdifContent.trim().isEmpty();
    } else if ("Enter LDIF".equals(currentMode)) {
      // Check if text area has content
      String textContent = ldifTextArea.getValue();
      hasContent = textContent != null && !textContent.trim().isEmpty();
      rawLdifContent = textContent;
    }
    
    ldifRunButton.setEnabled(hasContent);
  }

  private void updateCsvImportButtonState() {
    boolean hasContent = false;
    
    String currentMode = importModeSelector.getValue();
    if ("Upload CSV".equals(currentMode)) {
      // Check if file has been uploaded
      hasContent = rawCsvContent != null && !rawCsvContent.trim().isEmpty();
    } else if ("Enter CSV".equals(currentMode)) {
      // Check if text area has content
      String textContent = csvTextArea.getValue();
      hasContent = textContent != null && !textContent.trim().isEmpty();
      rawCsvContent = textContent;
    }
    
    csvRunButton.setEnabled(hasContent && !csvData.isEmpty());
  }

  private void switchDnMethod(String method) {
    boolean showSearchFields = "CSV Column and Search".equals(method);
    searchBaseField.setVisible(showSearchFields);
    searchFilterField.setVisible(showSearchFields);
    updatePreviewLdif();
  }

  private void processLdifFile() throws Exception {
    String content = new String(ldifBuffer.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    rawLdifContent = content;

    // Basic LDIF validation
    if (content.trim().isEmpty()) {
      NotificationHelper.showError("LDIF file is empty");
      return;
    }

    // Count entries for validation
    long entryCount = content.lines()
        .filter(line -> line.startsWith("dn:"))
        .count();

    updateLdifImportButtonState();
    NotificationHelper.showSuccess("LDIF file loaded successfully. Found " + entryCount + " entries.");
  }

  private void processCsvFile() throws Exception {
    String content;

    String currentMode = importModeSelector.getValue();
    if ("Enter CSV".equals(currentMode)) {
      // Use text area content
      content = csvTextArea.getValue();
      rawCsvContent = content;
    } else if (rawCsvContent == null) {
      // If this is the first time processing from upload, read from the input stream
      content = new String(csvBuffer.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      rawCsvContent = content;
    } else {
      // Use stored content for reprocessing
      content = rawCsvContent;
    }

    String[] lines = content.split("\n");

    csvData.clear();
    csvColumnOrder.clear();
    csvPreviewGrid.removeAllColumns();

    if (lines.length == 0) {
      NotificationHelper.showError("CSV file is empty");
      return;
    }

    // Skip header row if checkbox is checked
    int startIndex = csvExcludeHeader.getValue() ? 1 : 0;
    boolean removeQuotes = csvQuotedValues.getValue();

    // Parse CSV data
    boolean isFirstRow = true;
    for (int i = startIndex; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty())
        continue;

      List<String> values = parseCSVLine(line, removeQuotes);
      Map<String, String> row = new LinkedHashMap<>();

      // On the first row, establish the column order
      if (isFirstRow) {
        for (int j = 0; j < values.size(); j++) {
          String columnName = "C" + (j + 1);
          csvColumnOrder.add(columnName);
        }
        isFirstRow = false;
      }

      for (int j = 0; j < values.size(); j++) {
        String columnName = "C" + (j + 1);
        String value = values.get(j);
        row.put(columnName, value);
      }
      csvData.add(row);
    }

    if (csvData.isEmpty()) {
      NotificationHelper.showError("No valid data found in CSV file");
      return;
    }

    // Set up preview grid columns using the stored column order
    for (String columnName : csvColumnOrder) {
      csvPreviewGrid.addColumn(row -> row.get(columnName))
          .setHeader(columnName)
          .setFlexGrow(1)
          .setResizable(true)
          .setSortable(true);
    }

    csvPreviewGrid.setItems(csvData);
    csvPreviewContainer.setVisible(true);
    updateCsvImportButtonState();

    String excludeText = csvExcludeHeader.getValue() ? " (header row excluded)" : "";
    String quoteText = csvQuotedValues.getValue() ? " (quotes removed)" : "";

    NotificationHelper.showSuccess("CSV file processed successfully. " + csvData.size() + " rows loaded" + excludeText + quoteText + ".");

    updatePreviewLdif();
  }

  private void updatePreviewLdif() {
    if (csvData == null || csvData.isEmpty()) {
      previewLdifArea.setValue("");
      return;
    }

    // Take the first row as an example
    Map<String, String> sampleRow = csvData.get(0);
    String template = ldifTemplateArea.getValue();
    String dnMethod = dnMethodSelector.getValue();

    try {
      String sampleDn;
      if ("CSV Column".equals(dnMethod)) {
        // Assume C1 contains the DN
        sampleDn = sampleRow.getOrDefault("C1", "cn=sample,dc=example,dc=com");
      } else {
        // For search method, show a placeholder
        sampleDn = "cn=foundUser,ou=people,dc=example,dc=com";
      }

      // Replace variables in template
      Map<String, String> variables = new HashMap<>(sampleRow);
      variables.put("DN", sampleDn);

      String previewLdif = substituteVariables(template, variables);
      previewLdifArea.setValue(previewLdif);
    } catch (Exception e) {
      previewLdifArea.setValue("Error generating preview: " + e.getMessage());
    }
  }

  private List<String> parseCSVLine(String line, boolean removeQuotes) {
    List<String> values = new ArrayList<>();
    boolean inQuotes = false;
    StringBuilder currentValue = new StringBuilder();

    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);

      if (ch == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          // Handle escaped quotes ("")
          currentValue.append('"');
          i++; // Skip the next quote
        } else {
          // Toggle quote state
          inQuotes = !inQuotes;
          if (!removeQuotes) {
            currentValue.append(ch);
          }
        }
      } else if (ch == ',' && !inQuotes) {
        // End of field
        values.add(currentValue.toString().trim());
        currentValue = new StringBuilder();
      } else {
        currentValue.append(ch);
      }
    }

    // Add the last field
    values.add(currentValue.toString().trim());

    return values;
  }

  private String substituteVariables(String template, Map<String, String> variables) {
    String result = template;
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      String placeholder = "{" + entry.getKey() + "}";
      String value = entry.getValue() != null ? entry.getValue() : "";
      result = result.replace(placeholder, value);
    }
    return result;
  }

  private void performLdifImport() {
    // Refresh server configs to get the latest selection
    refreshServerConfigs();
    
    if (serverConfigs == null || serverConfigs.isEmpty()) {
      NotificationHelper.showError("Please select at least one LDAP server first");
      return;
    }

    if (rawLdifContent == null || rawLdifContent.trim().isEmpty()) {
      NotificationHelper.showError("Please provide LDIF content to import");
      return;
    }

    String operationMode = ldifOperationModeCombo.getValue();

    // Check if we're in Create LDIF mode
    if ("Create LDIF".equals(operationMode)) {
      // Simply provide the LDIF content for download
      createLdifDownloadLink(rawLdifContent, "ldif-import.ldif");
      NotificationHelper.showSuccess("LDIF prepared for download");
      loggingService.logInfo("IMPORT", "LDIF content prepared for download");
      return;
    }

    loggingService.logInfo("IMPORT", "Starting LDIF import to " + serverConfigs.size() + " server(s)");
    showProgress();

    int totalSuccessCount = 0;
    int totalErrorCount = 0;
    StringBuilder errorDetails = new StringBuilder();

    for (LdapServerConfig config : serverConfigs) {
      try {
        // Parse LDIF content using UnboundID SDK with InputStream
        byte[] contentBytes = rawLdifContent.getBytes(StandardCharsets.UTF_8);
        LDIFReader ldifReader = new LDIFReader(new java.io.ByteArrayInputStream(contentBytes));

        int successCount = 0;
        int errorCount = 0;

        try {
          while (true) {
            LDIFChangeRecord changeRecord = ldifReader.readChangeRecord();
            if (changeRecord == null) {
              break; // End of file
            }

            try {
              // Process the change record
              switch (changeRecord.getChangeType()) {
                case ADD:
                  // Convert to LdapEntry and add
                  LdapEntry entry = new LdapEntry();
                  entry.setDn(changeRecord.getDN());

                  // Get attributes from the add change record
                  if (changeRecord instanceof com.unboundid.ldif.LDIFAddChangeRecord) {
                    com.unboundid.ldif.LDIFAddChangeRecord addRecord = 
                        (com.unboundid.ldif.LDIFAddChangeRecord) changeRecord;

                    for (com.unboundid.ldap.sdk.Attribute attr : addRecord.getAttributes()) {
                      for (String value : attr.getValues()) {
                        entry.addAttribute(attr.getName(), value);
                      }
                    }
                  }

                  ldapService.addEntry(config, entry);
                  break;

                case MODIFY:
                  if (changeRecord instanceof com.unboundid.ldif.LDIFModifyChangeRecord) {
                    com.unboundid.ldif.LDIFModifyChangeRecord modifyRecord = 
                        (com.unboundid.ldif.LDIFModifyChangeRecord) changeRecord;

                    // Prepare controls based on checkbox selections
                    List<Control> controls = new ArrayList<>();

                    // Check and add No Operation control if requested
                    if (ldifNoOperation.getValue()) {
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
                    if (ldifPermissiveModify.getValue()) {
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
                        controls.isEmpty() ? new Control[0] : controls.toArray(new Control[0]));
                  }
                  break;

                case DELETE:
                  ldapService.deleteEntry(config, changeRecord.getDN());
                  break;

                default:
                  throw new Exception("Unsupported change type: " + changeRecord.getChangeType());
              }

              successCount++;

            } catch (Exception e) {
              errorCount++;
              if (!ldifContinueOnError.getValue()) {
                throw e;
              }
              // Log error but continue if continue on error is enabled
              loggingService.logError("IMPORT", "Error processing LDIF record " 
                  + changeRecord.getDN() + ": " + e.getMessage());
            }
          }
        } finally {
          ldifReader.close();
        }

        totalSuccessCount += successCount;
        totalErrorCount += errorCount;

        if (errorCount > 0) {
          loggingService.logImport(config.getName(), "LDIF file", successCount);
          loggingService.logWarning("IMPORT", "LDIF import completed with errors - Server: " + config.getName()
              + ", Successes: " + successCount + ", Errors: " + errorCount);
          errorDetails.append(config.getName()).append(": ").append(errorCount).append(" errors; ");
        } else {
          loggingService.logImport(config.getName(), "LDIF file", successCount);
        }

      } catch (Exception e) {
        totalErrorCount++;
        loggingService.logError("IMPORT", "LDIF import failed - Server: " + config.getName(), e.getMessage());
        errorDetails.append(config.getName()).append(": failed - ").append(e.getMessage()).append("; ");
      }
    }

    hideProgress();
    
    // Show notification dialog with results
    String message = String.format("LDIF import completed with %d successes and %d errors across %d server(s).",
        totalSuccessCount, totalErrorCount, serverConfigs.size());
    
    if (totalErrorCount > 0) {
      String details = errorDetails.toString();
      if (!details.isEmpty()) {
        message += "\n\n" + details;
      }
      showCompletionDialog("LDIF Import Results", message, totalErrorCount > 0);
    } else {
      showCompletionDialog("LDIF Import Results", message, false);
    }
  }

  private void performCsvImport() {
    // Refresh server configs to get the latest selection
    refreshServerConfigs();
    
    if (serverConfigs == null || serverConfigs.isEmpty()) {
      NotificationHelper.showError("Please select at least one LDAP server first");
      return;
    }

    if (csvData.isEmpty()) {
      NotificationHelper.showError("Please upload a CSV file first");
      return;
    }

    String operationMode = csvOperationModeCombo.getValue();

    // Check if we're in Create LDIF mode
    if ("Create LDIF".equals(operationMode)) {
      try {
        // Generate LDIF for all CSV rows
        StringBuilder fullLdif = new StringBuilder();
        String template = ldifTemplateArea.getValue();
        String dnMethod = dnMethodSelector.getValue();
        
        fullLdif.append("# LDIF generated from CSV import\n");
        fullLdif.append("# Server: ").append(serverConfigs.get(0).getName()).append("\n");
        fullLdif.append("# Rows: ").append(csvData.size()).append("\n");
        fullLdif.append("# Generated: ").append(java.time.LocalDateTime.now()).append("\n\n");

        for (Map<String, String> row : csvData) {
          String dn;
          if ("CSV Column".equals(dnMethod)) {
            // Use first column as DN
            dn = row.getOrDefault("C1", "");
            if (dn.isEmpty()) {
              throw new Exception("DN column (C1) is empty");
            }
          } else {
            // For search method in Create LDIF mode, we can't actually search
            // So use a placeholder
            dn = "cn=USER_FROM_SEARCH,ou=people,dc=example,dc=com";
          }

          // Generate LDIF for this row
          Map<String, String> variables = new HashMap<>(row);
          variables.put("DN", dn);
          String ldifEntry = substituteVariables(template, variables);
          
          if (ldifEntry != null && !ldifEntry.trim().isEmpty()) {
            fullLdif.append(ldifEntry);
            // Ensure proper separation between LDIF records (double newline)
            if (!ldifEntry.endsWith("\n\n")) {
              if (ldifEntry.endsWith("\n")) {
                fullLdif.append("\n");
              } else {
                fullLdif.append("\n\n");
              }
            }
          }
        }

        createCsvDownloadLink(fullLdif.toString(), "csv-import.ldif");
        NotificationHelper.showSuccess("LDIF generated from " + csvData.size() + " CSV rows and prepared for download");
        loggingService.logInfo("IMPORT", "CSV converted to LDIF for download (" + csvData.size() + " rows)");
        return;
      } catch (Exception e) {
        NotificationHelper.showError("Error generating LDIF from CSV: " + e.getMessage());
        loggingService.logError("IMPORT", "Failed to generate LDIF from CSV: " + e.getMessage());
        return;
      }
    }

    showProgress();

    int totalSuccessCount = 0;
    int totalErrorCount = 0;
    StringBuilder errorDetails = new StringBuilder();

    for (LdapServerConfig config : serverConfigs) {
      try {
        String template = ldifTemplateArea.getValue();
        String dnMethod = dnMethodSelector.getValue();
        int successCount = 0;
        int errorCount = 0;

        for (Map<String, String> row : csvData) {
          try {
            String dn;
            if ("CSV Column".equals(dnMethod)) {
              // Use first column as DN
              dn = row.getOrDefault("C1", "");
              if (dn.isEmpty()) {
                throw new Exception("DN column (C1) is empty");
              }
            } else {
              // Search for DN using LDAP search
              String searchFilter = substituteVariables(searchFilterField.getValue(), row);
              String searchBase = searchBaseField.getValue();

              // Perform actual LDAP search to find DN
              List<LdapEntry> results = ldapService.search(
                  config, searchBase, searchFilter, SearchScope.SUB);

              if (results.isEmpty()) {
                throw new Exception("No entry found matching search filter: " + searchFilter);
              } else if (results.size() > 1) {
                throw new Exception("Multiple entries found matching search filter: " + searchFilter);
              }

              dn = results.get(0).getDn();
            }

            // Generate LDIF for this row
            Map<String, String> variables = new HashMap<>(row);
            variables.put("DN", dn);
            String ldifEntry = substituteVariables(template, variables);

            // Process the generated LDIF using UnboundID LDIF parser
            if (ldifEntry != null && !ldifEntry.trim().isEmpty()) {
              byte[] contentBytes = ldifEntry.getBytes(StandardCharsets.UTF_8);
              LDIFReader ldifReader = new LDIFReader(new java.io.ByteArrayInputStream(contentBytes));

              try {
                while (true) {
                  LDIFChangeRecord changeRecord = ldifReader.readChangeRecord();
                  if (changeRecord == null) {
                    break; // End of LDIF content
                  }

                  // Process the change record (same logic as LDIF import)
                  switch (changeRecord.getChangeType()) {
                    case ADD:
                      LdapEntry entry = new LdapEntry();
                      entry.setDn(changeRecord.getDN());

                      if (changeRecord instanceof com.unboundid.ldif.LDIFAddChangeRecord) {
                        com.unboundid.ldif.LDIFAddChangeRecord addRecord = 
                            (com.unboundid.ldif.LDIFAddChangeRecord) changeRecord;

                        for (com.unboundid.ldap.sdk.Attribute attr : addRecord.getAttributes()) {
                          for (String value : attr.getValues()) {
                            entry.addAttribute(attr.getName(), value);
                          }
                        }
                      }

                      ldapService.addEntry(config, entry);
                      break;

                    case MODIFY:
                      if (changeRecord instanceof com.unboundid.ldif.LDIFModifyChangeRecord) {
                        com.unboundid.ldif.LDIFModifyChangeRecord modifyRecord = 
                            (com.unboundid.ldif.LDIFModifyChangeRecord) changeRecord;

                        // Prepare controls based on checkbox selections
                        List<Control> controls = new ArrayList<>();

                        // Check and add No Operation control if requested
                        if (csvNoOperation.getValue()) {
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
                        if (csvPermissiveModify.getValue()) {
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
                            controls.isEmpty() ? new Control[0] : controls.toArray(new Control[0]));
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
            }

            successCount++;

          } catch (Exception e) {
            errorCount++;
            if (!csvContinueOnError.getValue()) {
              throw e;
            }
            // Log error but continue if continue on error is enabled
            loggingService.logError("IMPORT", "Error processing CSV row: " + e.getMessage());
          }
        }

        totalSuccessCount += successCount;
        totalErrorCount += errorCount;

        if (errorCount > 0) {
          errorDetails.append(config.getName()).append(": ").append(errorCount).append(" errors; ");
        }

      } catch (Exception e) {
        totalErrorCount++;
        loggingService.logError("IMPORT", "CSV import failed - Server: " + config.getName(), e.getMessage());
        errorDetails.append(config.getName()).append(": failed - ").append(e.getMessage()).append("; ");
      }
    }

    hideProgress();

    // Show notification dialog with results
    String message = String.format("LDIF import completed with %d successes and %d errors across %d server(s).",
        totalSuccessCount, totalErrorCount, serverConfigs.size());
    
    if (totalErrorCount > 0) {
      String details = errorDetails.toString();
      if (!details.isEmpty()) {
        message += "\n\n" + details;
      }
      showCompletionDialog("CSV Import Results", message, totalErrorCount > 0);
    } else {
      showCompletionDialog("CSV Import Results", message, false);
    }
  }

  private void createLdifDownloadLink(String content, String fileName) {
    StreamResource resource = new StreamResource(fileName,
        () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

    ldifDownloadLink.setHref(resource);
    ldifDownloadLink.setVisible(true);
  }

  private void createCsvDownloadLink(String content, String fileName) {
    StreamResource resource = new StreamResource(fileName,
        () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));

    csvDownloadLink.setHref(resource);
    csvDownloadLink.setVisible(true);
  }

  private void showProgress() {
    progressContainer.setVisible(true);
    ldifRunButton.setEnabled(false);
    csvRunButton.setEnabled(false);
    ldifDownloadLink.setVisible(false);
    csvDownloadLink.setVisible(false);
  }

  private void hideProgress() {
    progressContainer.setVisible(false);
    updateLdifImportButtonState();
    updateCsvImportButtonState();
  }

  /**
   * Sets the list of server configurations for multi-server import.
   *
   * @param serverConfigs list of server configurations
   */
  public void setServerConfigs(List<LdapServerConfig> serverConfigs) {
    this.serverConfigs = serverConfigs;
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
    
    this.serverConfigs = selectedServers;
  }

  public void clear() {
    rawLdifContent = null;
    rawCsvContent = null;
    csvData.clear();
    csvColumnOrder.clear();
    ldifTextArea.setValue("");
    csvTextArea.setValue("");
    ldifRunButton.setEnabled(false);
    csvRunButton.setEnabled(false);
    csvPreviewContainer.setVisible(false);
    previewLdifArea.setValue("");
    ldifTemplateArea.setValue("dn: {DN}\nchangetype: modify\nreplace: userpassword\nuserpassword: {C2}");
    searchFilterField.setValue("(&(objectClass=person)(uid={C1}))");
    hideProgress();
  }

  /**
   * Shows a completion dialog with import results.
   *
   * @param title the dialog title
   * @param message the message to display
   * @param hasErrors whether there were errors
   */
  private void showCompletionDialog(String title, String message, boolean hasErrors) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(title);
    dialog.setWidth("600px");

    TextArea messageArea = new TextArea();
    messageArea.setValue(message);
    messageArea.setWidthFull();
    messageArea.setHeight("200px");
    messageArea.setReadOnly(true);

    Button closeButton = new Button("Close", e -> dialog.close());
    if (hasErrors) {
      closeButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
    } else {
      closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    }

    VerticalLayout layout = new VerticalLayout(messageArea);
    layout.setPadding(false);
    dialog.add(layout);
    dialog.getFooter().add(closeButton);
    dialog.open();
  }
}