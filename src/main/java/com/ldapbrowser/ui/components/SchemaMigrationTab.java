package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.service.SchemaComparisonService;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.unboundid.ldap.sdk.schema.AttributeTypeDefinition;
import com.unboundid.ldap.sdk.schema.ObjectClassDefinition;
import com.unboundid.ldap.sdk.schema.Schema;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schema migration tab component.
 * Allows migrating schema from a source server to target servers.
 */
public class SchemaMigrationTab extends VerticalLayout {

  private static final Logger logger = LoggerFactory.getLogger(SchemaMigrationTab.class);

  private final LdapService ldapService;
  private final LoggingService loggingService;
  private final SchemaComparisonService schemaComparisonService;

  private ComboBox<LdapServerConfig> sourceServerComboBox;
  private Button refreshButton;
  private TabSheet targetServerTabSheet;
  private VerticalLayout contentLayout;
  private Span statusLabel;

  private Set<LdapServerConfig> selectedServers = new HashSet<>();
  private LdapServerConfig sourceServer;
  private Map<String, Schema> schemaCache = new HashMap<>();
  private Map<String, TargetServerPanel> targetPanels = new HashMap<>();

  /**
   * Creates the schema migration tab.
   *
   * @param ldapService the service used to retrieve LDAP schema information
   * @param loggingService the service used for logging schema migration information
   * @param schemaComparisonService the service used for schema comparison operations
   */
  public SchemaMigrationTab(LdapService ldapService, LoggingService loggingService,
      SchemaComparisonService schemaComparisonService) {
    this.ldapService = ldapService;
    this.loggingService = loggingService;
    this.schemaComparisonService = schemaComparisonService;

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    initializeComponents();
    setupLayout();
  }

  /**
   * Initialize UI components.
   */
  private void initializeComponents() {
    // Source server dropdown
    sourceServerComboBox = new ComboBox<>("Schema Source");
    sourceServerComboBox.setPlaceholder("Select source server...");
    sourceServerComboBox.setItemLabelGenerator(LdapServerConfig::getName);
    sourceServerComboBox.setWidth("300px");
    sourceServerComboBox.addValueChangeListener(e -> onSourceServerChanged(e.getValue()));

    // Refresh button
    refreshButton = new Button("Refresh", new Icon(VaadinIcon.REFRESH));
    refreshButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
    refreshButton.addClickListener(e -> refreshSchemas());
    refreshButton.setEnabled(false);

    // Status label
    statusLabel = new Span("Select a schema source server to begin");
    statusLabel.getStyle()
        .set("color", "#666")
        .set("font-size", "0.9em");

    // Target server tab sheet
    targetServerTabSheet = new TabSheet();
    targetServerTabSheet.setSizeFull();
  }

  /**
   * Setup layout structure.
   */
  private void setupLayout() {
    // Header
    Icon icon = new Icon(VaadinIcon.EXCHANGE);
    icon.setColor("#1976d2");
    icon.setSize("20px");
    H3 title = new H3("Schema Migration");
    title.getStyle().set("margin", "0");
    HorizontalLayout header = new HorizontalLayout(icon, title);
    header.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    // Controls layout
    HorizontalLayout controlsLayout = new HorizontalLayout();
    controlsLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    controlsLayout.setSpacing(true);
    controlsLayout.add(sourceServerComboBox, refreshButton, statusLabel);
    controlsLayout.setAlignSelf(Alignment.END, refreshButton);
    controlsLayout.setAlignSelf(Alignment.END, statusLabel);

    // Content layout
    contentLayout = new VerticalLayout();
    contentLayout.setSizeFull();
    contentLayout.setPadding(false);
    contentLayout.setSpacing(false);

    add(header, controlsLayout, contentLayout);
    setFlexGrow(1, contentLayout);
  }

  /**
   * Sets the selected servers (environments) for migration.
   *
   * @param servers set of selected server configurations
   */
  public void setEnvironments(Set<LdapServerConfig> servers) {
    this.selectedServers = servers;
    updateSourceServerComboBox();
    clearTargetPanels();
    statusLabel.setText("Select a schema source server to begin");
  }

  /**
   * Updates the source server combo box with available servers.
   */
  private void updateSourceServerComboBox() {
    if (selectedServers == null || selectedServers.isEmpty()) {
      sourceServerComboBox.setItems(new ArrayList<>());
      sourceServerComboBox.setEnabled(false);
      return;
    }

    List<LdapServerConfig> serverList = new ArrayList<>(selectedServers);
    serverList.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
    sourceServerComboBox.setItems(serverList);
    sourceServerComboBox.setEnabled(true);
  }

  /**
   * Handles source server selection change.
   *
   * @param server the selected source server
   */
  private void onSourceServerChanged(LdapServerConfig server) {
    this.sourceServer = server;
    clearTargetPanels();

    if (server == null) {
      statusLabel.setText("Select a schema source server to begin");
      refreshButton.setEnabled(false);
      return;
    }

    refreshButton.setEnabled(true);
    statusLabel.setText("Loading schema from " + server.getName() + "...");
    loadSchemaAndBuildTargetPanels();
  }

  /**
   * Loads source schema and builds target server panels.
   */
  private void loadSchemaAndBuildTargetPanels() {
    try {
      // Load source schema
      Schema sourceSchema = loadSchema(sourceServer);
      if (sourceSchema == null) {
        NotificationHelper.showError("Failed to load schema from " + sourceServer.getName());
        statusLabel.setText("Error loading schema");
        return;
      }

      // Build target panels
      contentLayout.removeAll();
      targetServerTabSheet = new TabSheet();
      targetServerTabSheet.setSizeFull();

      List<LdapServerConfig> targetServers = selectedServers.stream()
          .filter(s -> !s.getName().equals(sourceServer.getName()))
          .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
          .collect(Collectors.toList());

      if (targetServers.isEmpty()) {
        statusLabel.setText("No target servers available (need at least 2 servers selected)");
        return;
      }

      for (LdapServerConfig targetServer : targetServers) {
        Schema targetSchema = loadSchema(targetServer);
        if (targetSchema != null) {
          TargetServerPanel panel = new TargetServerPanel(
              sourceServer, targetServer, sourceSchema, targetSchema);
          targetPanels.put(targetServer.getName(), panel);
          targetServerTabSheet.add(targetServer.getName(), panel);
        }
      }

      contentLayout.add(targetServerTabSheet);
      setFlexGrow(1, targetServerTabSheet);

      statusLabel.setText("Comparing " + sourceServer.getName() + " with "
          + targetServers.size() + " target server(s)");

    } catch (Exception e) {
      logger.error("Error loading schemas for migration", e);
      NotificationHelper.showError("Error loading schemas: " + e.getMessage());
      statusLabel.setText("Error loading schemas");
    }
  }

  /**
   * Refreshes schemas by clearing cache and reloading.
   */
  private void refreshSchemas() {
    if (sourceServer == null) {
      NotificationHelper.showWarning("No source server selected");
      return;
    }

    statusLabel.setText("Refreshing schemas...");
    try {
      // Clear local cache
      schemaCache.clear();

      // Load schemas using service (automatically clears cache)
      Map<String, Schema> loadedSchemas = schemaComparisonService.loadSchemas(
          new ArrayList<>(selectedServers));
      
      // Update local cache
      for (Map.Entry<String, Schema> entry : loadedSchemas.entrySet()) {
        if (entry.getValue() != null) {
          schemaCache.put(entry.getKey(), entry.getValue());
        }
      }

      // Reload the migration view
      loadSchemaAndBuildTargetPanels();
      NotificationHelper.showSuccess("Schemas refreshed successfully");
    } catch (Exception e) {
      logger.error("Failed to refresh schemas", e);
      NotificationHelper.showError("Failed to refresh schemas: " + e.getMessage());
      statusLabel.setText("Error refreshing schemas");
    }
  }

  /**
   * Loads schema for a server (with caching).
   *
   * @param server the server configuration
   * @return the schema, or null if failed
   */
  private Schema loadSchema(LdapServerConfig server) {
    String serverName = server.getName();

    if (schemaCache.containsKey(serverName)) {
      return schemaCache.get(serverName);
    }

    try {
      Schema schema = ldapService.getSchema(serverName, true);
      schemaCache.put(serverName, schema);
      return schema;
    } catch (Exception e) {
      logger.error("Failed to load schema from " + serverName, e);
      return null;
    }
  }

  /**
   * Clears all target panels.
   */
  private void clearTargetPanels() {
    targetPanels.clear();
    contentLayout.removeAll();
    schemaCache.clear();
  }

  /**
   * Panel for a single target server showing schema differences.
   */
  private class TargetServerPanel extends VerticalLayout {

    private final LdapServerConfig sourceServer;
    private final LdapServerConfig targetServer;
    private final Schema sourceSchema;
    private final Schema targetSchema;

    private VerticalLayout attributeListLayout;
    private VerticalLayout objectClassListLayout;
    private TextArea ldifTextArea;

    private Map<String, Checkbox> attributeCheckboxes = new LinkedHashMap<>();
    private Map<String, Checkbox> objectClassCheckboxes = new LinkedHashMap<>();
    private Set<String> selectedAttributes = new TreeSet<>();
    private Set<String> selectedObjectClasses = new TreeSet<>();

    /**
     * Creates a target server panel.
     *
     * @param sourceServer source server config
     * @param targetServer target server config
     * @param sourceSchema source schema
     * @param targetSchema target schema
     */
    public TargetServerPanel(LdapServerConfig sourceServer, LdapServerConfig targetServer,
        Schema sourceSchema, Schema targetSchema) {
      this.sourceServer = sourceServer;
      this.targetServer = targetServer;
      this.sourceSchema = sourceSchema;
      this.targetSchema = targetSchema;

      setSizeFull();
      setPadding(true);
      setSpacing(true);

      buildPanel();
    }

    /**
     * Builds the panel UI.
     */
    private void buildPanel() {
      // Title
      H3 title = new H3("Migrate schema from " + sourceServer.getName()
          + " to " + targetServer.getName());
      title.getStyle().set("margin-top", "0");

      // Attribute differences section
      Span attributeLabel = new Span("Attribute Types");
      attributeLabel.getStyle()
          .set("font-weight", "600")
          .set("font-size", "1.1em");

      attributeListLayout = new VerticalLayout();
      attributeListLayout.setPadding(false);
      attributeListLayout.setSpacing(false);
      attributeListLayout.getStyle()
          .set("max-height", "200px")
          .set("overflow-y", "auto")
          .set("border", "1px solid #ddd")
          .set("border-radius", "4px")
          .set("padding", "8px");

      // Object class differences section
      Span objectClassLabel = new Span("Object Classes");
      objectClassLabel.getStyle()
          .set("font-weight", "600")
          .set("font-size", "1.1em");

      objectClassListLayout = new VerticalLayout();
      objectClassListLayout.setPadding(false);
      objectClassListLayout.setSpacing(false);
      objectClassListLayout.getStyle()
          .set("max-height", "200px")
          .set("overflow-y", "auto")
          .set("border", "1px solid #ddd")
          .set("border-radius", "4px")
          .set("padding", "8px");

      // LDIF text area
      Span ldifLabel = new Span("LDIF");
      ldifLabel.getStyle()
          .set("font-weight", "600")
          .set("font-size", "1.1em");

      HorizontalLayout ldifHeader = new HorizontalLayout();
      ldifHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);
      ldifHeader.setWidthFull();

      Button copyButton = new Button("Copy", new Icon(VaadinIcon.COPY));
      copyButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
      copyButton.addClickListener(e -> copyLdifToClipboard());

      ldifHeader.add(ldifLabel);
      ldifHeader.add(copyButton);
      ldifHeader.setFlexGrow(1, ldifLabel);

      ldifTextArea = new TextArea();
      ldifTextArea.setWidthFull();
      ldifTextArea.setHeight("300px");
      ldifTextArea.setReadOnly(true);
      ldifTextArea.getStyle()
          .set("font-family", "monospace")
          .set("font-size", "12px");

      add(title, attributeLabel, attributeListLayout,
          objectClassLabel, objectClassListLayout, ldifHeader, ldifTextArea);

      // Load differences
      loadDifferences();
    }

    /**
     * Loads and displays schema differences.
     */
    private void loadDifferences() {
      try {
        loggingService.logDebug("MIGRATION", "Comparing schemas for "
            + targetServer.getName());

        // Use service to find differences
        Set<String> missingAttributes = schemaComparisonService.findMissingAttributeTypes(
            sourceSchema, targetSchema);
        Set<String> missingObjectClasses = schemaComparisonService.findMissingObjectClasses(
            sourceSchema, targetSchema);

        // Populate attribute checkboxes
        attributeListLayout.removeAll();
        if (missingAttributes.isEmpty()) {
          attributeListLayout.add(new Span("No missing attribute types"));
        } else {
          for (String atName : missingAttributes) {
            Checkbox cb = new Checkbox(atName);
            cb.setValue(true);
            cb.addValueChangeListener(e -> onAttributeSelectionChanged(atName, e.getValue()));
            attributeCheckboxes.put(atName, cb);
            selectedAttributes.add(atName);
            attributeListLayout.add(cb);
          }
        }

        // Populate object class checkboxes - include both missing and different
        objectClassListLayout.removeAll();
        
        if (missingObjectClasses.isEmpty()) {
          objectClassListLayout.add(new Span("No missing object classes or differences"));
        } else {
          for (String ocName : missingObjectClasses) {
            String label = ocName;
            if (schemaComparisonService.existsWithDifferentAttributes(ocName, targetSchema)) {
              label = ocName + " (update attributes)";
            }
            Checkbox cb = new Checkbox(label);
            cb.setValue(true);
            cb.addValueChangeListener(e -> onObjectClassSelectionChanged(ocName, e.getValue()));
            objectClassCheckboxes.put(ocName, cb);
            selectedObjectClasses.add(ocName);
            objectClassListLayout.add(cb);
          }
        }

        // Generate initial LDIF
        updateLdif();

        loggingService.logDebug("MIGRATION", "Found " + missingAttributes.size()
            + " missing attributes and " + missingObjectClasses.size()
            + " object classes requiring updates");

      } catch (Exception e) {
        logger.error("Error loading schema differences", e);
        NotificationHelper.showError("Error loading differences: " + e.getMessage());
      }
    }

    /**
     * Handles attribute selection change.
     *
     * @param atName attribute name
     * @param selected whether selected
     */
    private void onAttributeSelectionChanged(String atName, boolean selected) {
      if (selected) {
        selectedAttributes.add(atName);
      } else {
        selectedAttributes.remove(atName);
      }
      updateLdif();
    }

    /**
     * Handles object class selection change.
     *
     * @param ocName object class name
     * @param selected whether selected
     */
    private void onObjectClassSelectionChanged(String ocName, boolean selected) {
      if (selected) {
        selectedObjectClasses.add(ocName);
      } else {
        selectedObjectClasses.remove(ocName);
      }
      updateLdif();
    }

    /**
     * Updates the LDIF text area based on current selections.
     */
    private void updateLdif() {
      StringBuilder ldif = new StringBuilder();

      ldif.append("# Schema migration LDIF for ").append(targetServer.getName())
          .append("\n");
      ldif.append("# Source: ").append(sourceServer.getName()).append("\n");
      ldif.append("# Generated: ").append(new java.util.Date()).append("\n");
      ldif.append("\n");

      // Add selected attribute types
      if (!selectedAttributes.isEmpty()) {
        ldif.append("# Attribute Types\n");
        for (String atName : selectedAttributes) {
          AttributeTypeDefinition at = sourceSchema.getAttributeType(atName);
          if (at != null) {
            ldif.append("dn: cn=schema\n");
            ldif.append("changetype: modify\n");
            ldif.append("add: attributeTypes\n");
            String cleanDef = schemaComparisonService.cleanSchemaDefinition(at.toString());
            ldif.append("attributeTypes: ").append(cleanDef).append("\n");
            ldif.append("\n");
          }
        }
      }

      // Add selected object classes (filter out those with unselected required attributes)
      if (!selectedObjectClasses.isEmpty()) {
        ldif.append("# Object Classes\n");
        for (String ocName : selectedObjectClasses) {
          ObjectClassDefinition sourceOc = sourceSchema.getObjectClass(ocName);
          ObjectClassDefinition targetOc = targetSchema.getObjectClass(ocName);
          
          if (sourceOc != null) {
            // Check if all required attributes are selected
            boolean canAdd = true;
            if (sourceOc.getRequiredAttributes() != null) {
              for (String reqAtName : sourceOc.getRequiredAttributes()) {
                String atName = reqAtName.toLowerCase();
                if (attributeCheckboxes.containsKey(atName)
                    && !selectedAttributes.contains(atName)) {
                  canAdd = false;
                  break;
                }
              }
            }

            if (canAdd) {
              // PingDirectory can update existing object classes with just ADD
              if (targetOc != null) {
                ldif.append("# Updating existing object class: ").append(ocName).append("\n");
              }
              ldif.append("dn: cn=schema\n");
              ldif.append("changetype: modify\n");
              ldif.append("add: objectClasses\n");
              String cleanDef = schemaComparisonService.cleanSchemaDefinition(sourceOc.toString());
              ldif.append("objectClasses: ").append(cleanDef).append("\n");
              ldif.append("\n");
            }
          }
        }
      }

      if (selectedAttributes.isEmpty() && selectedObjectClasses.isEmpty()) {
        ldif.append("# No schema elements selected\n");
      }

      ldifTextArea.setValue(ldif.toString());
    }

    /**
     * Copies LDIF to clipboard.
     */
    private void copyLdifToClipboard() {
      String ldif = ldifTextArea.getValue();
      if (ldif != null && !ldif.trim().isEmpty()) {
        // Use Vaadin's clipboard helper
        getUI().ifPresent(ui -> {
          ui.getPage().executeJs(
              "navigator.clipboard.writeText($0).then(() => {"
                  + "  console.log('LDIF copied to clipboard');"
                  + "}, (err) => {"
                  + "  console.error('Failed to copy LDIF:', err);"
                  + "});",
              ldif
          );
        });
        NotificationHelper.showSuccess("LDIF copied to clipboard");
      } else {
        NotificationHelper.showWarning("No LDIF to copy");
      }
    }
  }
}
