package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.service.TruststoreService;
import com.ldapbrowser.ui.dialogs.DnBrowserDialog;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchScope;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
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
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Export tab for exporting LDAP search results in various formats.
 */
public class ExportTab extends VerticalLayout {

  private final LdapService ldapService;
  private final LoggingService loggingService;
  private final ConfigurationService configurationService;
  private final TruststoreService truststoreService;

  // Server configuration
  private LdapServerConfig serverConfig;
  private Set<LdapServerConfig> groupServers;

  // Track current export mode for CSV generation
  private boolean isSearchMode = true;

  // UI Components
  private ComboBox<String> itemSelectionMode;
  private VerticalLayout modeContainer;

  // Search Mode Components
  private VerticalLayout searchModeLayout;
  private TextField searchBaseField;
  private Button searchBaseBrowseButton;
  private TextField searchFilterField;
  private TextField returnAttributesField;
  private ComboBox<String> outputFormatCombo;
  private Checkbox searchIncludeHeaderCheckbox;
  private Checkbox searchIncludeDnCheckbox;
  private Checkbox searchSurroundValuesCheckbox;
  private Button exportButton;

  // Input CSV Mode Components
  private VerticalLayout csvModeLayout;
  private TextField csvSearchBaseField;
  private Button csvSearchBaseBrowseButton;
  private TextField csvSearchFilterField;
  private TextField csvReturnAttributesField;
  private ComboBox<String> csvOutputFormatCombo;
  private Checkbox excludeHeaderCheckbox;
  private Checkbox quotedValuesCheckbox;
  private Checkbox csvIncludeHeaderCheckbox;
  private Checkbox csvIncludeDnCheckbox;
  private Checkbox csvSurroundValuesCheckbox;
  private Upload csvUpload;
  private MemoryBuffer csvBuffer;
  private VerticalLayout csvPreviewContainer;
  private Grid<Map<String, String>> csvPreviewGrid;
  private List<Map<String, String>> csvData;
  private String rawCsvContent;
  private List<String> csvColumnOrder;
  private Button csvExportButton;

  // Progress and download
  private ProgressBar progressBar;
  private VerticalLayout progressContainer;
  private Anchor downloadLink;

  /**
   * Create a new ExportTab.
   *
   * @param ldapService          LDAP service used to run searches
   * @param loggingService       logging service for export events
   * @param configurationService configuration service
   * @param truststoreService    truststore service for DN browser
   */
  public ExportTab(LdapService ldapService, LoggingService loggingService,
      ConfigurationService configurationService, TruststoreService truststoreService) {
    this.ldapService = ldapService;
    this.loggingService = loggingService;
    this.configurationService = configurationService;
    this.truststoreService = truststoreService;
    this.csvData = new ArrayList<>();
    this.csvColumnOrder = new ArrayList<>();

    initializeComponents();
    setupLayout();
  }

  private void initializeComponents() {
    // Item Selection Mode
    itemSelectionMode = new ComboBox<>("Item Selection Mode");
    itemSelectionMode.setItems("Search", "Input CSV");
    itemSelectionMode.setValue("Search");
    itemSelectionMode.addValueChangeListener(e -> switchMode(e.getValue()));

    // Progress components
    progressBar = new ProgressBar();
    progressBar.setVisible(false);
    progressContainer = new VerticalLayout();
    progressContainer.setVisible(false);
    progressContainer.addClassName("export-progress");
    progressContainer.add(new Span("Processing export..."), progressBar);

    // Initialize search mode components
    initializeSearchModeComponents();

    // Initialize CSV mode components
    initializeCsvModeComponents();

    // Initialize DN selection dialog
    initializeDnSelectionDialog();

    // Mode container
    modeContainer = new VerticalLayout();
    modeContainer.setPadding(false);
    modeContainer.setSpacing(false);
    modeContainer.add(searchModeLayout);
  }

  private void initializeSearchModeComponents() {
    searchModeLayout = new VerticalLayout();
    searchModeLayout.setPadding(true);
    searchModeLayout.setSpacing(true);
    searchModeLayout.addClassName("export-field-group");

    // Search base field with browse button
    searchBaseField = new TextField("Search Base");
    searchBaseField.setWidthFull();
    searchBaseField.setPlaceholder("dc=example,dc=com");

    searchBaseBrowseButton = new Button(new Icon(VaadinIcon.FOLDER_OPEN));
    searchBaseBrowseButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
    searchBaseBrowseButton.setTooltipText("Select DN from Directory");
    searchBaseBrowseButton.addClickListener(e -> showDnSelectionDialog(false));

    // Search filter field
    searchFilterField = new TextField("Search Filter");
    searchFilterField.setWidthFull();
    searchFilterField.setPlaceholder("(objectClass=person)");

    // Return attributes field
    returnAttributesField = new TextField("Return Attributes");
    returnAttributesField.setWidthFull();
    returnAttributesField.setPlaceholder("cn,mail,telephoneNumber (leave empty for all)");

    // Combine Search Base, Browse Button, Search Filter, and Return Attributes in one row
    HorizontalLayout searchFieldsLayout = new HorizontalLayout();
    searchFieldsLayout.setWidthFull();
    searchFieldsLayout.setDefaultVerticalComponentAlignment(Alignment.END);
    searchFieldsLayout.add(searchBaseField, searchBaseBrowseButton, searchFilterField, returnAttributesField);
    searchFieldsLayout.setFlexGrow(1, searchBaseField);
    searchFieldsLayout.setFlexGrow(1, searchFilterField);
    searchFieldsLayout.setFlexGrow(1, returnAttributesField);

    // Output format selector
    outputFormatCombo = new ComboBox<>("Output Format");
    outputFormatCombo.setItems("CSV", "JSON", "LDIF", "DN List");
    outputFormatCombo.setValue("CSV");
    outputFormatCombo.addValueChangeListener(e -> updateCsvCheckboxVisibility(false));

    // CSV output options checkboxes
    searchIncludeHeaderCheckbox = new Checkbox("Include Header");
    searchIncludeHeaderCheckbox.setValue(true);
    searchIncludeHeaderCheckbox.setVisible(true);

    searchIncludeDnCheckbox = new Checkbox("Include DN");
    searchIncludeDnCheckbox.setValue(true);
    searchIncludeDnCheckbox.setVisible(true);

    searchSurroundValuesCheckbox = new Checkbox("Surround Values in Quotes");
    searchSurroundValuesCheckbox.setValue(true);
    searchSurroundValuesCheckbox.setVisible(true);

    HorizontalLayout csvOptionsLayout = new HorizontalLayout();
    csvOptionsLayout.setSpacing(true);
    csvOptionsLayout.add(searchIncludeHeaderCheckbox, searchIncludeDnCheckbox, searchSurroundValuesCheckbox);

    // Export button
    exportButton = new Button("Export", new Icon(VaadinIcon.DOWNLOAD));
    exportButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    exportButton.addClickListener(e -> performSearchExport());

    // Format and export button layout
    HorizontalLayout formatLayout = new HorizontalLayout();
    formatLayout.setDefaultVerticalComponentAlignment(Alignment.END);
    formatLayout.add(outputFormatCombo, exportButton);

    searchModeLayout.add(
        new H4("Search Export"),
        searchFieldsLayout,
        csvOptionsLayout,
        formatLayout);
  }

  private void initializeCsvModeComponents() {
    csvModeLayout = new VerticalLayout();
    csvModeLayout.setPadding(true);
    csvModeLayout.setSpacing(true);
    csvModeLayout.addClassName("export-field-group");

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

    // CSV Search fields
    csvSearchBaseField = new TextField("Search Base");
    csvSearchBaseField.setWidthFull();
    csvSearchBaseField.setPlaceholder("dc=example,dc=com");

    csvSearchBaseBrowseButton = new Button(new Icon(VaadinIcon.FOLDER_OPEN));
    csvSearchBaseBrowseButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
    csvSearchBaseBrowseButton.setTooltipText("Select DN from Directory");
    csvSearchBaseBrowseButton.addClickListener(e -> showDnSelectionDialog(true));

    csvSearchFilterField = new TextField("Search Filter (Use {C1}, {C2}, ... to reference CSV columns)");
    csvSearchFilterField.setWidthFull();
    csvSearchFilterField.setPlaceholder("(&(objectClass=person)(uid={C1})(sn={C2}))");

    csvReturnAttributesField = new TextField("Return Attributes");
    csvReturnAttributesField.setWidthFull();
    csvReturnAttributesField.setPlaceholder("cn,mail,telephoneNumber (leave empty for all)");

    csvOutputFormatCombo = new ComboBox<>("Output Format");
    csvOutputFormatCombo.setItems("CSV", "JSON", "LDIF", "DN List");
    csvOutputFormatCombo.setValue("CSV");
    csvOutputFormatCombo.addValueChangeListener(e -> updateCsvCheckboxVisibility(true));

    excludeHeaderCheckbox = new Checkbox("Exclude first row (header row)");
    excludeHeaderCheckbox.setValue(false);
    excludeHeaderCheckbox.getStyle().set("margin-top", "8px");
    excludeHeaderCheckbox.addValueChangeListener(e -> {
      if (csvData != null && !csvData.isEmpty()) {
        try {
          processCsvFile();
        } catch (Exception ex) {
          NotificationHelper.showError("Error reprocessing CSV file: " + ex.getMessage());
        }
      }
    });

    quotedValuesCheckbox = new Checkbox("Values are surrounded by quotes");
    quotedValuesCheckbox.setValue(true);
    quotedValuesCheckbox.getStyle().set("margin-bottom", "8px");
    quotedValuesCheckbox.addValueChangeListener(e -> {
      if (csvData != null && !csvData.isEmpty()) {
        try {
          processCsvFile();
        } catch (Exception ex) {
          NotificationHelper.showError("Error reprocessing CSV file: " + ex.getMessage());
        }
      }
    });

    csvExportButton = new Button("Export", new Icon(VaadinIcon.DOWNLOAD));
    csvExportButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    csvExportButton.addClickListener(e -> performCsvExport());
    csvExportButton.setEnabled(false);

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

    // Combine Search Base, Browse Button, Search Filter, and Return Attributes in one row
    HorizontalLayout csvSearchFieldsLayout = new HorizontalLayout();
    csvSearchFieldsLayout.setWidthFull();
    csvSearchFieldsLayout.setDefaultVerticalComponentAlignment(Alignment.END);
    csvSearchFieldsLayout.add(csvSearchBaseField, csvSearchBaseBrowseButton, csvSearchFilterField, csvReturnAttributesField);
    csvSearchFieldsLayout.setFlexGrow(1, csvSearchBaseField);
    csvSearchFieldsLayout.setFlexGrow(1, csvSearchFilterField);
    csvSearchFieldsLayout.setFlexGrow(1, csvReturnAttributesField);

    // CSV output options checkboxes
    csvIncludeHeaderCheckbox = new Checkbox("Include Header");
    csvIncludeHeaderCheckbox.setValue(true);
    csvIncludeHeaderCheckbox.setVisible(true);

    csvIncludeDnCheckbox = new Checkbox("Include DN");
    csvIncludeDnCheckbox.setValue(true);
    csvIncludeDnCheckbox.setVisible(true);

    csvSurroundValuesCheckbox = new Checkbox("Surround Values in Quotes");
    csvSurroundValuesCheckbox.setValue(true);
    csvSurroundValuesCheckbox.setVisible(true);

    HorizontalLayout csvOutputOptionsLayout = new HorizontalLayout();
    csvOutputOptionsLayout.setSpacing(true);
    csvOutputOptionsLayout.add(csvIncludeHeaderCheckbox, csvIncludeDnCheckbox, csvSurroundValuesCheckbox);

    // Format and export button layout
    HorizontalLayout formatAndExportLayout = new HorizontalLayout();
    formatAndExportLayout.setDefaultVerticalComponentAlignment(Alignment.END);
    formatAndExportLayout.add(csvOutputFormatCombo, csvExportButton);

    csvModeLayout.add(
        new H4("Input CSV Export"),
        new Span("Upload a CSV file to use as input for batch LDAP searches"),
        csvUpload,
        excludeHeaderCheckbox,
        quotedValuesCheckbox,
        csvPreviewContainer,
        csvSearchFieldsLayout,
        csvOutputOptionsLayout,
        formatAndExportLayout);
  }

  private void initializeDnSelectionDialog() {
    // DN selection dialog is now created on-demand using DnBrowserDialog
  }

  private void showDnSelectionDialog(boolean forCsvMode) {
    if (serverConfig == null && (groupServers == null || groupServers.isEmpty())) {
      NotificationHelper.showError("Please select a server first");
      return;
    }

    // Create and configure DnBrowserDialog
    DnBrowserDialog dialog = new DnBrowserDialog(ldapService, truststoreService);

    // Set the server configs
    if (serverConfig != null) {
      dialog.withServerConfig(serverConfig);
    } else if (groupServers != null && !groupServers.isEmpty()) {
      dialog.withServerConfigs(new java.util.ArrayList<>(groupServers));
    }

    // Set callback to update correct field based on mode
    dialog.onDnSelected(dn -> {
      if (forCsvMode) {
        csvSearchBaseField.setValue(dn);
      } else {
        searchBaseField.setValue(dn);
      }
    });

    dialog.open();
  }

  private void updateCsvCheckboxVisibility(boolean csvMode) {
    String format = csvMode ? csvOutputFormatCombo.getValue() : outputFormatCombo.getValue();
    boolean isCsv = "CSV".equals(format);

    if (csvMode) {
      csvIncludeHeaderCheckbox.setVisible(isCsv);
      csvIncludeDnCheckbox.setVisible(isCsv);
      csvSurroundValuesCheckbox.setVisible(isCsv);
    } else {
      searchIncludeHeaderCheckbox.setVisible(isCsv);
      searchIncludeDnCheckbox.setVisible(isCsv);
      searchSurroundValuesCheckbox.setVisible(isCsv);
    }
  }

  private void setupLayout() {
    setSizeFull();
    setPadding(true);
    setSpacing(true);
    addClassName("export-container");

    // Title with icon
    HorizontalLayout titleLayout = new HorizontalLayout();
    titleLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    titleLayout.setSpacing(true);

    Icon exportIcon = new Icon(VaadinIcon.DOWNLOAD);
    exportIcon.setSize("20px");
    exportIcon.getStyle().set("color", "#4a90e2");

    H3 title = new H3("Export");
    title.addClassNames(LumoUtility.Margin.NONE);
    title.getStyle().set("color", "#333");

    titleLayout.add(exportIcon, title);

    // Mode selector with styling
    itemSelectionMode.addClassName("export-mode-selector");

    // Add section styling to mode containers
    modeContainer.addClassName("export-section");

    add(titleLayout, itemSelectionMode, modeContainer, progressContainer);

    // Download link (initially hidden)
    downloadLink = new Anchor();
    downloadLink.setVisible(false);
    downloadLink.addClassName("export-download");
    add(downloadLink);
  }

  private void switchMode(String mode) {
    modeContainer.removeAll();
    if ("Search".equals(mode)) {
      modeContainer.add(searchModeLayout);
    } else if ("Input CSV".equals(mode)) {
      modeContainer.add(csvModeLayout);
    }
  }

  private void processCsvFile() throws Exception {
    String content;

    // If this is the first time processing, read from the input stream
    if (rawCsvContent == null) {
      content = new String(csvBuffer.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      rawCsvContent = content;
    } else {
      // Use stored content for reprocessing
      content = rawCsvContent;
    }

    final String[] lines = content.split("\n");

    csvData.clear();
    csvColumnOrder.clear();
    csvPreviewGrid.removeAllColumns();

    if (lines.length == 0) {
      NotificationHelper.showError("CSV file is empty");
      return;
    }

    // Skip header row if checkbox is checked
    int startIndex = excludeHeaderCheckbox.getValue() ? 1 : 0;
    boolean removeQuotes = quotedValuesCheckbox.getValue();

    // Parse CSV data
    boolean isFirstRow = true;
    for (int i = startIndex; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty()) {
        continue;
      }

      List<String> values = parseCsvLine(line, removeQuotes);
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

    // Set up preview grid columns
    for (String columnName : csvColumnOrder) {
      csvPreviewGrid.addColumn(row -> row.get(columnName))
          .setHeader(columnName)
          .setFlexGrow(1)
          .setResizable(true)
          .setSortable(true)
          .setComparator((row1, row2) -> {
            String value1 = row1.get(columnName);
            String value2 = row2.get(columnName);
            if (value1 == null) {
              value1 = "";
            }
            if (value2 == null) {
              value2 = "";
            }
            return value1.compareToIgnoreCase(value2);
          });
    }

    csvPreviewGrid.setItems(csvData);
    csvPreviewContainer.setVisible(true);
    csvExportButton.setEnabled(true);

    String excludeText = excludeHeaderCheckbox.getValue() ? " (header row excluded)" : "";
    String quoteText = quotedValuesCheckbox.getValue() ? " (quotes removed)" : "";
    String successMsg = "CSV file processed successfully. " + csvData.size() + " rows loaded"
        + excludeText + quoteText + ".";
    NotificationHelper.showSuccess(successMsg);
  }

  private List<String> parseCsvLine(String line, boolean removeQuotes) {
    List<String> values = new ArrayList<>();
    boolean inQuotes = false;
    StringBuilder currentValue = new StringBuilder();

    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);

      if (ch == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          // Handle escaped quotes ("")
          currentValue.append('"');
          i++;
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
    Pattern pattern = Pattern.compile("\\{(C\\d+)\\}");
    Matcher matcher = pattern.matcher(template);

    while (matcher.find()) {
      String variable = matcher.group(1);
      String value = variables.get(variable);
      if (value != null) {
        result = result.replace("{" + variable + "}", value);
      }
    }

    return result;
  }

  private void performCsvExport() {
    isSearchMode = false;

    Set<LdapServerConfig> effectiveServers = getEffectiveServers();
    if (effectiveServers.isEmpty()) {
      NotificationHelper.showError("Please select an LDAP server first");
      return;
    }

    if (csvData.isEmpty()) {
      NotificationHelper.showError("Please upload a CSV file first");
      return;
    }

    String searchBase = csvSearchBaseField.getValue();
    String searchFilterTemplate = csvSearchFilterField.getValue();
    String returnAttrs = csvReturnAttributesField.getValue();
    String format = csvOutputFormatCombo.getValue();

    if (searchBase == null || searchBase.trim().isEmpty()) {
      NotificationHelper.showError("Search Base is required");
      return;
    }

    if (searchFilterTemplate == null || searchFilterTemplate.trim().isEmpty()) {
      NotificationHelper.showError("Search Filter is required");
      return;
    }

    String serverNames = effectiveServers.stream()
        .map(LdapServerConfig::getName)
        .collect(Collectors.joining(", "));

    loggingService.logInfo("EXPORT", String.format(
        "Starting CSV export - Servers: %s, CSV rows: %d, Format: %s",
        serverNames, csvData.size(), format));

    showProgress();

    try {
      List<LdapEntry> allEntries = new ArrayList<>();

      // Parse return attributes
      String[] attrs = null;
      if (returnAttrs != null && !returnAttrs.trim().isEmpty()) {
        attrs = returnAttrs.split(",");
        for (int i = 0; i < attrs.length; i++) {
          attrs[i] = attrs[i].trim();
        }
      }

      // Process each row in CSV data across all servers
      for (Map<String, String> row : csvData) {
        String searchFilter = substituteVariables(searchFilterTemplate, row);

        for (LdapServerConfig server : effectiveServers) {
          try {
            List<LdapEntry> entries = ldapService.search(
                server,
                searchBase.trim(),
                searchFilter,
                SearchScope.SUB,
                attrs
            );
            allEntries.addAll(entries);

          } catch (LDAPException | GeneralSecurityException e) {
            String errMsg = "CSV export failed for server: " + server.getName();
            loggingService.logError("EXPORT", errMsg, e.getMessage());
            NotificationHelper.showError("Search failed for server " + server.getName() + ": " + e.getMessage());
            // Continue with other servers
          }
        }
      }

      // Generate export data
      String exportData = generateExportData(allEntries, format, getReturnAttributesList(returnAttrs));
      String fileName = generateFileName(format);

      createDownloadLink(exportData, fileName, format);
      hideProgress();

      loggingService.logInfo("EXPORT", String.format(
          "Export completed - %d entries exported from %d searches across %d server(s) to %s",
          allEntries.size(), csvData.size(), effectiveServers.size(), fileName));

      NotificationHelper.showSuccess(String.format(
          "Export completed successfully. %d entries exported from %d searches across %d server(s).",
          allEntries.size(), csvData.size(), effectiveServers.size()));

    } catch (Exception e) {
      hideProgress();
      loggingService.logError("EXPORT", "CSV export failed", e.getMessage());
      NotificationHelper.showError("Export failed: " + e.getMessage());
    }
  }

  private void performSearchExport() {
    isSearchMode = true;

    Set<LdapServerConfig> effectiveServers = getEffectiveServers();
    if (effectiveServers.isEmpty()) {
      NotificationHelper.showError("Please select an LDAP server first");
      return;
    }

    String searchBase = searchBaseField.getValue();
    String searchFilter = searchFilterField.getValue();
    String returnAttrs = returnAttributesField.getValue();
    String format = outputFormatCombo.getValue();

    if (searchBase == null || searchBase.trim().isEmpty()) {
      NotificationHelper.showError("Search Base is required");
      return;
    }

    if (searchFilter == null || searchFilter.trim().isEmpty()) {
      NotificationHelper.showError("Search Filter is required");
      return;
    }

    String serverNames = effectiveServers.stream()
        .map(LdapServerConfig::getName)
        .collect(Collectors.joining(", "));

    loggingService.logInfo("EXPORT", String.format(
        "Starting search export - Servers: %s, Base: %s, Filter: %s, Format: %s",
        serverNames, searchBase, searchFilter, format));

    showProgress();

    try {
      List<LdapEntry> allEntries = new ArrayList<>();
      int totalEntries = 0;

      // Parse return attributes
      String[] attrs = null;
      if (returnAttrs != null && !returnAttrs.trim().isEmpty()) {
        attrs = returnAttrs.split(",");
        for (int i = 0; i < attrs.length; i++) {
          attrs[i] = attrs[i].trim();
        }
      }

      // Search across all servers
      for (LdapServerConfig server : effectiveServers) {
        try {
          List<LdapEntry> entries = ldapService.search(
              server,
              searchBase.trim(),
              searchFilter.trim(),
              SearchScope.SUB,
              attrs
          );
          allEntries.addAll(entries);
          totalEntries += entries.size();

        } catch (LDAPException | GeneralSecurityException e) {
          String errMsg = "Search export failed for server: " + server.getName();
          loggingService.logError("EXPORT", errMsg, e.getMessage());
          NotificationHelper.showError("Search failed for server " + server.getName() + ": " + e.getMessage());
          // Continue with other servers
        }
      }

      // Generate export data
      String exportData = generateExportData(allEntries, format, getReturnAttributesList(returnAttrs));
      String fileName = generateFileName(format);

      createDownloadLink(exportData, fileName, format);
      hideProgress();

      loggingService.logInfo("EXPORT", String.format(
          "Export completed - %d entries exported from %d server(s) to %s",
          totalEntries, effectiveServers.size(), fileName));

      NotificationHelper.showSuccess(String.format(
          "Export completed successfully. %d entries exported from %d server(s).",
          totalEntries, effectiveServers.size()));

    } catch (Exception e) {
      hideProgress();
      loggingService.logError("EXPORT", "Search export failed", e.getMessage());
      NotificationHelper.showError("Export failed: " + e.getMessage());
    }
  }

  private List<String> getReturnAttributesList(String returnAttrs) {
    if (returnAttrs == null || returnAttrs.trim().isEmpty()) {
      return new ArrayList<>();
    }

    List<String> attrList = new ArrayList<>();
    for (String attr : returnAttrs.split(",")) {
      String trimmed = attr.trim();
      if (!trimmed.isEmpty()) {
        attrList.add(trimmed);
      }
    }
    return attrList;
  }

  private String generateExportData(List<LdapEntry> entries, String format, List<String> requestedAttrs) {
    return switch (format.toUpperCase()) {
      case "CSV" -> generateCsvData(entries, requestedAttrs);
      case "JSON" -> generateJsonData(entries, requestedAttrs);
      case "LDIF" -> generateLdifData(entries, requestedAttrs);
      case "DN LIST" -> generateDnListData(entries);
      default -> generateCsvData(entries, requestedAttrs);
    };
  }

  private String generateCsvData(List<LdapEntry> entries, List<String> requestedAttrs) {
    StringBuilder sb = new StringBuilder();

    if (entries.isEmpty()) {
      return sb.toString();
    }

    // Get checkbox values based on current mode
    boolean includeHeader = isSearchMode ? searchIncludeHeaderCheckbox.getValue() : csvIncludeHeaderCheckbox.getValue();
    boolean includeDn = isSearchMode ? searchIncludeDnCheckbox.getValue() : csvIncludeDnCheckbox.getValue();
    boolean surroundValues = isSearchMode ? searchSurroundValuesCheckbox.getValue() : csvSurroundValuesCheckbox.getValue();

    // Determine attributes to export
    Set<String> attributesToExport = new LinkedHashSet<>();
    if (includeDn) {
      attributesToExport.add("dn");
    }

    if (requestedAttrs.isEmpty()) {
      // Collect all unique attributes
      for (LdapEntry entry : entries) {
        attributesToExport.addAll(entry.getAttributes().keySet());
      }
    } else {
      attributesToExport.addAll(requestedAttrs);
    }

    // Write header if requested
    if (includeHeader) {
      sb.append(String.join(",", attributesToExport)).append("\n");
    }

    // Write data
    for (LdapEntry entry : entries) {
      List<String> values = new ArrayList<>();
      for (String attrName : attributesToExport) {
        if ("dn".equals(attrName)) {
          String dnValue = entry.getDn();
          if (surroundValues) {
            values.add("\"" + escapeQuotes(dnValue) + "\"");
          } else {
            values.add(dnValue);
          }
        } else {
          List<String> attrValues = entry.getAttributes().get(attrName);
          if (attrValues != null && !attrValues.isEmpty()) {
            String value = String.join("; ", attrValues);
            if (surroundValues) {
              values.add("\"" + escapeQuotes(value) + "\"");
            } else {
              values.add(value);
            }
          } else {
            if (surroundValues) {
              values.add("\"\"");
            } else {
              values.add("");
            }
          }
        }
      }
      sb.append(String.join(",", values)).append("\n");
    }

    return sb.toString();
  }

  private String generateJsonData(List<LdapEntry> entries, List<String> requestedAttrs) {
    StringBuilder sb = new StringBuilder();
    sb.append("[\n");

    for (int i = 0; i < entries.size(); i++) {
      LdapEntry entry = entries.get(i);
      sb.append("  {\n");
      sb.append("    \"dn\": \"").append(escapeJson(entry.getDn())).append("\"");

      if (requestedAttrs.isEmpty()) {
        // Export all attributes
        for (var attr : entry.getAttributes().entrySet()) {
          sb.append(",\n    \"").append(escapeJson(attr.getKey())).append("\": ");
          if (attr.getValue().size() == 1) {
            sb.append("\"").append(escapeJson(attr.getValue().get(0))).append("\"");
          } else {
            sb.append("[");
            for (int j = 0; j < attr.getValue().size(); j++) {
              if (j > 0) sb.append(", ");
              sb.append("\"").append(escapeJson(attr.getValue().get(j))).append("\"");
            }
            sb.append("]");
          }
        }
      } else {
        // Export only requested attributes
        for (String attrName : requestedAttrs) {
          List<String> values = entry.getAttributes().get(attrName);
          if (values != null && !values.isEmpty()) {
            sb.append(",\n    \"").append(escapeJson(attrName)).append("\": ");
            if (values.size() == 1) {
              sb.append("\"").append(escapeJson(values.get(0))).append("\"");
            } else {
              sb.append("[");
              for (int j = 0; j < values.size(); j++) {
                if (j > 0) sb.append(", ");
                sb.append("\"").append(escapeJson(values.get(j))).append("\"");
              }
              sb.append("]");
            }
          }
        }
      }

      sb.append("\n  }");
      if (i < entries.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }

    sb.append("]\n");
    return sb.toString();
  }

  private String generateLdifData(List<LdapEntry> entries, List<String> requestedAttrs) {
    StringBuilder sb = new StringBuilder();

    for (LdapEntry entry : entries) {
      sb.append("dn: ").append(entry.getDn()).append("\n");

      if (requestedAttrs.isEmpty()) {
        // Export all attributes
        for (var attr : entry.getAttributes().entrySet()) {
          for (String value : attr.getValue()) {
            sb.append(attr.getKey()).append(": ").append(value).append("\n");
          }
        }
      } else {
        // Export only requested attributes
        for (String attrName : requestedAttrs) {
          List<String> values = entry.getAttributes().get(attrName);
          if (values != null) {
            for (String value : values) {
              sb.append(attrName).append(": ").append(value).append("\n");
            }
          }
        }
      }

      sb.append("\n");
    }

    return sb.toString();
  }

  private String generateDnListData(List<LdapEntry> entries) {
    StringBuilder sb = new StringBuilder();
    for (LdapEntry entry : entries) {
      sb.append(entry.getDn()).append("\n");
    }
    return sb.toString();
  }

  private String escapeQuotes(String value) {
    return value.replace("\"", "\"\"");
  }

  private String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private String generateFileName(String format) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    String timestamp = LocalDateTime.now().format(formatter);
    String extension = switch (format.toUpperCase()) {
      case "JSON" -> "json";
      case "LDIF" -> "ldif";
      case "DN LIST" -> "txt";
      default -> "csv";
    };
    return "ldap_export_" + timestamp + "." + extension;
  }

  private void createDownloadLink(String data, String fileName, String format) {
    String mimeType = switch (format.toUpperCase()) {
      case "JSON" -> "application/json";
      case "LDIF" -> "text/plain";
      case "DN LIST" -> "text/plain";
      default -> "text/csv";
    };

    StreamResource resource = new StreamResource(fileName,
        () -> new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
    resource.setContentType(mimeType);

    downloadLink.setHref(resource);
    downloadLink.getElement().setAttribute("download", true);
    downloadLink.setText("Download " + fileName);
    downloadLink.setVisible(true);
  }

  private void showProgress() {
    progressBar.setIndeterminate(true);
    progressContainer.setVisible(true);
    exportButton.setEnabled(false);
    csvExportButton.setEnabled(false);
    downloadLink.setVisible(false);
  }

  private void hideProgress() {
    progressContainer.setVisible(false);
    exportButton.setEnabled(true);
    csvExportButton.setEnabled(!csvData.isEmpty());
  }

  public void setServerConfig(LdapServerConfig serverConfig) {
    this.serverConfig = serverConfig;
    this.groupServers = null;
  }

  /**
   * Set multiple servers for group operations.
   *
   * @param groupServers set of server configurations to use for group operations
   */
  public void setGroupServers(Set<LdapServerConfig> groupServers) {
    this.groupServers = groupServers;
    this.serverConfig = null;
  }

  /**
   * Get the effective servers to operate on (either single server or group servers).
   *
   * @return set of server configurations to use for operations
   */
  private Set<LdapServerConfig> getEffectiveServers() {
    if (groupServers != null && !groupServers.isEmpty()) {
      return groupServers;
    } else if (serverConfig != null) {
      return Set.of(serverConfig);
    } else {
      return Collections.emptySet();
    }
  }

  /**
   * Clear the form and reset the export tab state.
   */
  public void clear() {
    searchBaseField.clear();
    searchFilterField.clear();
    returnAttributesField.clear();
    csvSearchBaseField.clear();
    csvSearchFilterField.clear();
    csvReturnAttributesField.clear();
    csvData.clear();
    csvColumnOrder.clear();
    rawCsvContent = null;
    excludeHeaderCheckbox.setValue(false);
    quotedValuesCheckbox.setValue(true);
    csvPreviewContainer.setVisible(false);
    csvExportButton.setEnabled(false);
    downloadLink.setVisible(false);
    hideProgress();
  }
}
