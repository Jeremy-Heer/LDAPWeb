package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.service.SchemaComparisonService;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.ldapbrowser.ui.utils.SchemaDetailDialogHelper;
import com.unboundid.ldap.sdk.schema.AttributeTypeDefinition;
import com.unboundid.ldap.sdk.schema.ObjectClassDefinition;
import com.unboundid.ldap.sdk.schema.Schema;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.server.streams.DownloadHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schema files tab component.
 * Groups schema elements by X-SCHEMA-FILE and exposes file-level content.
 */
public class SchemaFilesTab extends VerticalLayout {

  private final LoggingService loggingService;
  private final SchemaComparisonService schemaComparisonService;

  private final Button refreshButton = new Button("Refresh", new Icon(VaadinIcon.REFRESH));
  private final Span statusLabel = new Span();
  private final Grid<SchemaFileRow> fileGrid = new Grid<>(SchemaFileRow.class, false);
  private final SplitLayout splitLayout = new SplitLayout();
  private final VerticalLayout detailsPanel = new VerticalLayout();
  private final TextArea detailsTextArea = new TextArea();
  private final Button copyButton = new Button(new Icon(VaadinIcon.COPY));
  private final Anchor downloadLink = new Anchor();

  private Set<LdapServerConfig> environments = new HashSet<>();
  private List<LdapServerConfig> sortedServers = new ArrayList<>();
  private final Map<String, List<SchemaFileContent>> fileContents = new LinkedHashMap<>();

  /**
   * Creates the schema files tab.
   *
   * @param ldapService LDAP service
   * @param loggingService logging service
   * @param schemaComparisonService schema comparison service
   */
  public SchemaFilesTab(LoggingService loggingService,
      SchemaComparisonService schemaComparisonService) {
    this.loggingService = loggingService;
    this.schemaComparisonService = schemaComparisonService;

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    Icon icon = new Icon(VaadinIcon.FOLDER_OPEN);
    icon.setColor("#1976d2");
    icon.setSize("20px");
    H3 title = new H3("Schema Files");
    title.getStyle().set("margin", "0");
    HorizontalLayout header = new HorizontalLayout(icon, title);
    header.setDefaultVerticalComponentAlignment(
      com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);

    refreshButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
    refreshButton.addClickListener(e -> loadAndRender());

    statusLabel.getStyle().set("color", "#666").set("font-size", "0.9em");

    HorizontalLayout controls = new HorizontalLayout(refreshButton, statusLabel);
    controls.setDefaultVerticalComponentAlignment(
      com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
    controls.setWidthFull();

    configureGrid();
    configureDetailsPanel();

    splitLayout.setSizeFull();
    splitLayout.setOrientation(SplitLayout.Orientation.VERTICAL);
    splitLayout.setSplitterPosition(55);
    splitLayout.addToPrimary(fileGrid);
    splitLayout.addToSecondary(detailsPanel);

    add(header, controls, splitLayout);
  }

  /**
   * Sets the selected servers used for schema file discovery.
   *
   * @param envs selected server configurations
   */
  public void setEnvironments(Set<LdapServerConfig> envs) {
    this.environments = envs == null ? new HashSet<>() : envs;
    this.sortedServers = new ArrayList<>(this.environments);
    this.sortedServers.sort(Comparator.comparing(cfg -> cfg.getName().toLowerCase()));
    loadAndRender();
  }

  private void configureGrid() {
    fileGrid.setSizeFull();
    fileGrid.setColumnReorderingAllowed(true);
    fileGrid.addColumn(SchemaFileRow::getServerName)
        .setHeader("Server")
        .setFrozen(true)
        .setResizable(true)
        .setSortable(true)
        .setAutoWidth(true);
    fileGrid.addColumn(SchemaFileRow::getSchemaFile)
        .setHeader("X-SCHEMA-FILE")
        .setResizable(true)
        .setSortable(true)
        .setFlexGrow(1);
    fileGrid.asSingleSelect().addValueChangeListener(e -> showFileDetails(e.getValue()));
  }

  private void configureDetailsPanel() {
    detailsPanel.setSizeFull();
    detailsPanel.setPadding(true);
    detailsPanel.setSpacing(true);
    detailsPanel.setVisible(false);

    detailsTextArea.setWidthFull();
    detailsTextArea.setHeight("100%");
    detailsTextArea.setReadOnly(true);
    detailsTextArea.getStyle().set("font-family", "monospace");
    detailsTextArea.getElement().getStyle().set("white-space", "pre");

    copyButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    copyButton.setTooltipText("Copy to clipboard");
    copyButton.addClickListener(e -> copyDetailsToClipboard());

    downloadLink.getElement().setAttribute("download", true);
    downloadLink.setVisible(false);
    downloadLink.add(new Button("Download", new Icon(VaadinIcon.DOWNLOAD)));

    HorizontalLayout actions = new HorizontalLayout(copyButton, downloadLink);
    actions.setDefaultVerticalComponentAlignment(
      com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);

    detailsPanel.add(detailsTextArea, actions);
    detailsPanel.setFlexGrow(1, detailsTextArea);
  }

  private void loadAndRender() {
    detailsPanel.setVisible(false);
    if (sortedServers.isEmpty()) {
      statusLabel.setText("No servers selected.");
      fileGrid.setItems(List.of());
      fileContents.clear();
      return;
    }

    statusLabel.setText("Loading schema files from " + sortedServers.size() + " servers...");
    loggingService.logDebug("SCHEMA", "Loading schema files for " + sortedServers.size()
        + " servers");

    Map<String, Schema> schemas = schemaComparisonService.loadSchemas(sortedServers);
    fileContents.clear();

    List<SchemaFileRow> rows = new ArrayList<>();
    for (LdapServerConfig cfg : sortedServers) {
      Schema schema = schemas.get(cfg.getName());
      if (schema == null) {
        continue;
      }

      Map<String, List<SchemaFileContent>> perServer = collectSchemaFiles(schema);
      for (Map.Entry<String, List<SchemaFileContent>> entry : perServer.entrySet()) {
        String schemaFile = entry.getKey();
        rows.add(new SchemaFileRow(cfg.getName(), schemaFile, entry.getValue().size()));
        fileContents.put(key(cfg.getName(), schemaFile), entry.getValue());
      }
    }

    rows.sort(Comparator.comparing(SchemaFileRow::getServerName, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(SchemaFileRow::getSchemaFile, String.CASE_INSENSITIVE_ORDER));
    fileGrid.setItems(rows);
    statusLabel.setText(rows.isEmpty() ? "No schema files found." : "Schema files loaded.");
  }

  static Map<String, List<SchemaFileContent>> collectSchemaFiles(Schema schema) {
    Map<String, List<SchemaFileContent>> files = new LinkedHashMap<>();

    for (ObjectClassDefinition oc : schema.getObjectClasses()) {
      String schemaFile = SchemaDetailDialogHelper.getSchemaFileFromExtensions(oc.getExtensions());
      if (schemaFile != null && !schemaFile.isBlank()) {
        files.computeIfAbsent(schemaFile, k -> new ArrayList<>())
            .add(new SchemaFileContent("objectClass", oc.getNameOrOID(),
                SchemaDetailDialogHelper.getRawDefinitionString(oc)));
      }
    }

    for (AttributeTypeDefinition at : schema.getAttributeTypes()) {
      String schemaFile = SchemaDetailDialogHelper.getSchemaFileFromExtensions(at.getExtensions());
      if (schemaFile != null && !schemaFile.isBlank()) {
        files.computeIfAbsent(schemaFile, k -> new ArrayList<>())
            .add(new SchemaFileContent("attributeType", at.getNameOrOID(),
                SchemaDetailDialogHelper.getRawDefinitionString(at)));
      }
    }

    for (List<SchemaFileContent> contents : files.values()) {
      contents.sort(Comparator.comparing(SchemaFileContent::type)
          .thenComparing(SchemaFileContent::name, String.CASE_INSENSITIVE_ORDER));
    }

    return files;
  }

  private void showFileDetails(SchemaFileRow row) {
    if (row == null) {
      detailsPanel.setVisible(false);
      return;
    }

    List<SchemaFileContent> contents = fileContents.get(key(row.getServerName(), row.getSchemaFile()));
    if (contents == null || contents.isEmpty()) {
      detailsTextArea.setValue("");
      downloadLink.setVisible(false);
      detailsPanel.setVisible(false);
      return;
    }

    String output = buildSchemaFileContent(row.getServerName(), row.getSchemaFile(), contents);
    detailsTextArea.setValue(output);
    downloadLink.setHref(createDownloadHandler(output, row.getServerName(), row.getSchemaFile()));
    downloadLink.setVisible(true);
    detailsPanel.setVisible(true);
    splitLayout.setSplitterPosition(60);
  }

  private void copyDetailsToClipboard() {
    String text = detailsTextArea.getValue();
    if (text == null || text.isBlank()) {
      NotificationHelper.showWarning("No schema file content to copy");
      return;
    }

    detailsTextArea.getElement().executeJs(
        "navigator.clipboard.writeText($0).then(() => {}, () => {});", text);
    NotificationHelper.showSuccess("Schema file copied to clipboard");
  }

  private DownloadHandler createDownloadHandler(String content, String serverName,
      String schemaFile) {
    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
    String fileName = sanitizeFileName(serverName + "-" + schemaFile) + ".ldif";
    return event -> {
      event.setFileName(fileName);
      try (java.io.OutputStream out = event.getOutputStream()) {
        out.write(bytes);
      }
    };
  }

  private String sanitizeFileName(String value) {
    return value.replaceAll("[^a-zA-Z0-9._-]+", "_");
  }

  private String key(String serverName, String schemaFile) {
    return serverName + "::" + schemaFile;
  }

  static String buildSchemaFileContent(String serverName, String schemaFile,
      List<SchemaFileContent> contents) {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: cn=schema\n");
    ldif.append("objectClass: top\n");
    ldif.append("objectClass: ldapSubentry\n");
    ldif.append("objectClass: subschema\n");
    ldif.append("cn: schema\n");
    for (SchemaFileContent content : contents) {
      ldif.append(content.prefix())
          .append(normalizeSchemaDefinition(content.rawDefinition()))
          .append('\n');
    }

    return ldif.toString().trim();
  }

  private static String normalizeSchemaDefinition(String schemaDefinition) {
    if (schemaDefinition == null) {
      return "";
    }

    String normalized = SchemaDetailDialogHelper.stripServerExtensions(schemaDefinition)
        .trim()
        .replaceAll("\\s+", " ");
    if (normalized.startsWith("(") && normalized.endsWith(")")) {
      normalized = normalized.substring(1, normalized.length() - 1).trim();
      return "( " + normalized + " )";
    }

    return normalized;
  }

  /**
   * Represents a schema file row.
   */
  public static class SchemaFileRow {
    private final String serverName;
    private final String schemaFile;
    private final int elementCount;

    public SchemaFileRow(String serverName, String schemaFile, int elementCount) {
      this.serverName = serverName;
      this.schemaFile = schemaFile;
      this.elementCount = elementCount;
    }

    public String getServerName() {
      return serverName;
    }

    public String getSchemaFile() {
      return schemaFile;
    }

    public int getElementCount() {
      return elementCount;
    }
  }

  /**
   * Represents a schema element in a file.
   */
  static record SchemaFileContent(String type, String name, String rawDefinition) {

    private String prefix() {
      return switch (type) {
        case "attributeType" -> "attributeTypes: ";
        case "objectClass" -> "objectClasses: ";
        default -> type + ": ";
      };
    }
  }
}