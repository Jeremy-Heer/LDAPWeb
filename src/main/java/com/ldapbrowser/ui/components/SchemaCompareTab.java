package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.LoggingService;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.ldapbrowser.util.SchemaCompareUtil;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.schema.AttributeSyntaxDefinition;
import com.unboundid.ldap.sdk.schema.AttributeTypeDefinition;
import com.unboundid.ldap.sdk.schema.MatchingRuleDefinition;
import com.unboundid.ldap.sdk.schema.MatchingRuleUseDefinition;
import com.unboundid.ldap.sdk.schema.ObjectClassDefinition;
import com.unboundid.ldap.sdk.schema.Schema;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Schema compare tab component.
 * Allows comparing LDAP schemas across multiple servers.
 */
public class SchemaCompareTab extends VerticalLayout {

  private final LdapService ldapService;
  private final LoggingService loggingService;
  private final TabSheet tabSheet = new TabSheet();
  private final Button refreshButton = new Button("Refresh", new Icon(VaadinIcon.REFRESH));
  private final Span statusLabel = new Span();
  private final TextField searchField = new TextField();
  private final Checkbox ignoreExtensionsCheckbox = new Checkbox("Ignore Extensions", true);

  private Set<LdapServerConfig> environments = new HashSet<>();
  private List<LdapServerConfig> sortedServers = new ArrayList<>();

  // Grids per schema component
  private Grid<RowModel> ocGrid;
  private Grid<RowModel> atGrid;
  private Grid<RowModel> mrGrid;
  private Grid<RowModel> mruGrid;
  private Grid<RowModel> synGrid;

  // Data providers for filtering
  private ListDataProvider<RowModel> ocDataProvider;
  private ListDataProvider<RowModel> atDataProvider;
  private ListDataProvider<RowModel> mrDataProvider;
  private ListDataProvider<RowModel> mruDataProvider;
  private ListDataProvider<RowModel> synDataProvider;

  // Split layout for details view
  private SplitLayout splitLayout;
  private VerticalLayout detailsPanel;

  /**
   * Creates the schema compare tab.
   *
   * @param ldapService the service used to retrieve LDAP schema information
   * @param loggingService the service used for logging schema comparison debug information
   */
  public SchemaCompareTab(LdapService ldapService, LoggingService loggingService) {
    this.ldapService = ldapService;
    this.loggingService = loggingService;

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    // Title
    Icon icon = new Icon(VaadinIcon.BRIEFCASE);
    icon.setColor("#1976d2");
    icon.setSize("20px");
    H3 title = new H3("Schema Comparison");
    title.getStyle().set("margin", "0");
    HorizontalLayout header = new HorizontalLayout(icon, title);
    header.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    // Search field
    searchField.setPlaceholder("Search schema elements...");
    searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
    searchField.setValueChangeMode(ValueChangeMode.LAZY);
    searchField.addValueChangeListener(e -> applyFilter(e.getValue()));
    searchField.setWidth("300px");

    refreshButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
    refreshButton.addClickListener(e -> loadAndRender());

    HorizontalLayout controls = new HorizontalLayout(
        searchField, ignoreExtensionsCheckbox, refreshButton, statusLabel
    );
    controls.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    controls.setWidthFull();
    controls.setJustifyContentMode(JustifyContentMode.BETWEEN);

    // Tabs
    tabSheet.setSizeFull();
    ocGrid = createGrid();
    atGrid = createGrid();
    mrGrid = createGrid();
    mruGrid = createGrid();
    synGrid = createGrid();

    // Initialize data providers
    ocDataProvider = new ListDataProvider<>(new ArrayList<>());
    atDataProvider = new ListDataProvider<>(new ArrayList<>());
    mrDataProvider = new ListDataProvider<>(new ArrayList<>());
    mruDataProvider = new ListDataProvider<>(new ArrayList<>());
    synDataProvider = new ListDataProvider<>(new ArrayList<>());

    ocGrid.setDataProvider(ocDataProvider);
    atGrid.setDataProvider(atDataProvider);
    mrGrid.setDataProvider(mrDataProvider);
    mruGrid.setDataProvider(mruDataProvider);
    synGrid.setDataProvider(synDataProvider);

    tabSheet.add(new Tab("Object Classes"), ocGrid);
    tabSheet.add(new Tab("Attribute Types"), atGrid);
    tabSheet.add(new Tab("Matching Rules"), mrGrid);
    tabSheet.add(new Tab("Matching Rule Use"), mruGrid);
    tabSheet.add(new Tab("Syntaxes"), synGrid);

    // Setup split layout for details
    splitLayout = new SplitLayout();
    splitLayout.setSizeFull();
    splitLayout.setOrientation(SplitLayout.Orientation.VERTICAL);
    splitLayout.setSplitterPosition(100);
    splitLayout.addToPrimary(tabSheet);

    detailsPanel = new VerticalLayout();
    detailsPanel.setSizeFull();
    detailsPanel.setPadding(true);
    detailsPanel.setSpacing(true);
    detailsPanel.setVisible(false);
    splitLayout.addToSecondary(detailsPanel);

    add(header, controls, splitLayout);
  }

  /**
   * Sets the server environments to compare.
   *
   * @param envs the server environments
   */
  public void setEnvironments(Set<LdapServerConfig> envs) {
    this.environments = envs == null ? new HashSet<>() : envs;
    this.sortedServers = new ArrayList<>(this.environments);
    // Sort by display name
    this.sortedServers.sort(Comparator.comparing(cfg -> displayName(cfg)));
    loadAndRender();
  }

  private Grid<RowModel> createGrid() {
    Grid<RowModel> grid = new Grid<>(RowModel.class, false);
    grid.setSizeFull();
    grid.setColumnReorderingAllowed(true);

    // Add selection listener to show details
    grid.asSingleSelect().addValueChangeListener(e -> {
      if (e.getValue() != null) {
        showSchemaElementDetails(e.getValue());
      } else {
        hideDetails();
      }
    });

    return grid;
  }

  private void loadAndRender() {
    if (sortedServers.isEmpty()) {
      statusLabel.setText("No servers selected.");
      setGridsEmpty();
      return;
    }

    statusLabel.setText("Loading schemas from " + sortedServers.size() + " servers...");
    loggingService.logDebug("SCHEMA", "Starting schema comparison for " 
        + sortedServers.size() + " servers");

    Map<String, Schema> schemas = new LinkedHashMap<>();
    int errors = 0;

    // Determine if all servers support extended schema info
    final String extendedSchemaInfoOid = "1.3.6.1.4.1.30221.2.5.12";
    boolean allSupportExtended = true;
    
    for (LdapServerConfig cfg : sortedServers) {
      try {
        if (!ldapService.isConnected(cfg.getName())) {
          ldapService.connect(cfg);
        }
        boolean supported = ldapService.isControlSupported(cfg.getName(), extendedSchemaInfoOid);
        if (!supported) {
          allSupportExtended = false;
        }
      } catch (LDAPException e) {
        allSupportExtended = false;
      }
    }

    loggingService.logDebug("SCHEMA", "Extended schema info control " 
        + (allSupportExtended ? "enabled" : "disabled") + " for all servers");

    // Fetch schemas
    for (LdapServerConfig cfg : sortedServers) {
      String serverName = displayName(cfg);
      try {
        Schema schema = ldapService.getSchema(cfg.getName(), allSupportExtended);
        schemas.put(serverName, schema);
        loggingService.logDebug("SCHEMA", "Successfully loaded schema from " + serverName);
      } catch (LDAPException e) {
        errors++;
        schemas.put(serverName, null);
        loggingService.logError("SCHEMA", "Failed to load schema from " + serverName, 
            e.getMessage());
      }
    }

    // Build data for each component
    loggingService.logDebug("SCHEMA", "Processing schema elements for comparison");
    renderObjectClasses(schemas);
    renderAttributeTypes(schemas);
    renderMatchingRules(schemas);
    renderMatchingRuleUse(schemas);
    renderSyntaxes(schemas);

    if (errors > 0) {
      statusLabel.setText("Loaded with " + errors + " error(s). Extended schema info control " 
          + (allSupportExtended ? "used" : "disabled") + ".");
      NotificationHelper.showError("Some servers failed to load schema.");
      loggingService.logError("SCHEMA", "Schema comparison completed with " + errors + " errors");
    } else {
      statusLabel.setText("Schema loaded. Extended schema info control " 
          + (allSupportExtended ? "used" : "disabled") + ".");
      loggingService.logInfo("SCHEMA", "Schema comparison completed successfully for " 
          + sortedServers.size() + " servers");
    }
  }

  private void setGridsEmpty() {
    ocDataProvider.getItems().clear();
    atDataProvider.getItems().clear();
    mrDataProvider.getItems().clear();
    mruDataProvider.getItems().clear();
    synDataProvider.getItems().clear();
    ocDataProvider.refreshAll();
    atDataProvider.refreshAll();
    mrDataProvider.refreshAll();
    mruDataProvider.refreshAll();
    synDataProvider.refreshAll();
  }

  private void renderObjectClasses(Map<String, Schema> schemas) {
    setupColumns(ocGrid, schemas.keySet());
    Map<String, Map<String, String>> rows = buildRowsFor(schemas, ComponentType.OBJECT_CLASS);
    List<RowModel> models = toModels(rows, schemas.keySet());
    ocDataProvider.getItems().clear();
    ocDataProvider.getItems().addAll(models);
    ocDataProvider.refreshAll();
  }

  private void renderAttributeTypes(Map<String, Schema> schemas) {
    setupColumns(atGrid, schemas.keySet());
    Map<String, Map<String, String>> rows = buildRowsFor(schemas, ComponentType.ATTRIBUTE_TYPE);
    List<RowModel> models = toModels(rows, schemas.keySet());
    atDataProvider.getItems().clear();
    atDataProvider.getItems().addAll(models);
    atDataProvider.refreshAll();
  }

  private void renderMatchingRules(Map<String, Schema> schemas) {
    setupColumns(mrGrid, schemas.keySet());
    Map<String, Map<String, String>> rows = buildRowsFor(schemas, ComponentType.MATCHING_RULE);
    List<RowModel> models = toModels(rows, schemas.keySet());
    mrDataProvider.getItems().clear();
    mrDataProvider.getItems().addAll(models);
    mrDataProvider.refreshAll();
  }

  private void renderMatchingRuleUse(Map<String, Schema> schemas) {
    setupColumns(mruGrid, schemas.keySet());
    Map<String, Map<String, String>> rows = buildRowsFor(schemas, 
        ComponentType.MATCHING_RULE_USE);
    List<RowModel> models = toModels(rows, schemas.keySet());
    mruDataProvider.getItems().clear();
    mruDataProvider.getItems().addAll(models);
    mruDataProvider.refreshAll();
  }

  private void renderSyntaxes(Map<String, Schema> schemas) {
    setupColumns(synGrid, schemas.keySet());
    Map<String, Map<String, String>> rows = buildRowsFor(schemas, ComponentType.SYNTAX);
    List<RowModel> models = toModels(rows, schemas.keySet());
    synDataProvider.getItems().clear();
    synDataProvider.getItems().addAll(models);
    synDataProvider.refreshAll();
  }

  private void setupColumns(Grid<RowModel> grid, Collection<String> serverNames) {
    grid.removeAllColumns();
    
    grid.addColumn(RowModel::getName)
        .setHeader("Name")
        .setAutoWidth(true)
        .setResizable(true)
        .setFrozen(true)
        .setSortable(true)
        .setComparator(Comparator.comparing(RowModel::getName));

    for (String server : serverNames) {
      grid.addColumn(row -> row.getChecksum(server))
          .setHeader(server)
          .setAutoWidth(true)
          .setResizable(true)
          .setSortable(true)
          .setComparator(Comparator.comparing(row -> row.getChecksum(server)));
    }

    grid.addColumn(row -> row.isEqualAcross(serverNames) ? "Equal" : "Unequal")
        .setHeader("Status")
        .setAutoWidth(true)
        .setResizable(true)
        .setSortable(true)
        .setComparator(Comparator.comparing(row -> row.isEqualAcross(serverNames)));
  }

  private enum ComponentType {
    OBJECT_CLASS, ATTRIBUTE_TYPE, MATCHING_RULE, MATCHING_RULE_USE, SYNTAX
  }

  private Map<String, Map<String, String>> buildRowsFor(
      Map<String, Schema> schemas, ComponentType type) {
    Map<String, Map<String, String>> rows = new LinkedHashMap<>();
    Set<String> allNames = new TreeSet<>();
    String elementTypeName = type.name().toLowerCase().replace('_', ' ');

    // First pass: collect all element names
    for (Map.Entry<String, Schema> e : schemas.entrySet()) {
      Schema schema = e.getValue();
      if (schema == null) {
        continue;
      }

      int elementCount = 0;
      switch (type) {
        case OBJECT_CLASS -> {
          for (ObjectClassDefinition def : schema.getObjectClasses()) {
            allNames.add(def.getNameOrOID());
            elementCount++;
          }
        }
        case ATTRIBUTE_TYPE -> {
          for (AttributeTypeDefinition def : schema.getAttributeTypes()) {
            allNames.add(def.getNameOrOID());
            elementCount++;
          }
        }
        case MATCHING_RULE -> {
          for (MatchingRuleDefinition def : schema.getMatchingRules()) {
            allNames.add(def.getNameOrOID());
            elementCount++;
          }
        }
        case MATCHING_RULE_USE -> {
          for (MatchingRuleUseDefinition def : schema.getMatchingRuleUses()) {
            allNames.add(def.getNameOrOID());
            elementCount++;
          }
        }
        case SYNTAX -> {
          for (AttributeSyntaxDefinition def : schema.getAttributeSyntaxes()) {
            allNames.add(def.getOID());
            elementCount++;
          }
        }
      }

      loggingService.logSchemaComparisonStart(e.getKey(), elementTypeName, elementCount);
    }

    // Second pass: process each element and generate checksums
    for (String name : allNames) {
      Map<String, String> checksums = new LinkedHashMap<>();
      
      for (Map.Entry<String, Schema> e : schemas.entrySet()) {
        String server = e.getKey();
        Schema schema = e.getValue();
        
        if (schema == null) {
          checksums.put(server, "ERROR");
          loggingService.logSchemaError(server, elementTypeName, name, "Schema not available");
          continue;
        }

        try {
          boolean includeExtensions = !ignoreExtensionsCheckbox.getValue();
          String sum = switch (type) {
            case OBJECT_CLASS -> {
              ObjectClassDefinition d = schema.getObjectClass(name);
              if (d != null) {
                String originalValue = d.toString();
                String canonicalValue = SchemaCompareUtil.canonical(d, includeExtensions);
                String checksum = checksum(canonicalValue);
                loggingService.logSchemaElement(server, elementTypeName, name, 
                    originalValue, canonicalValue, checksum);
                yield checksum;
              } else {
                yield "MISSING";
              }
            }
            case ATTRIBUTE_TYPE -> {
              AttributeTypeDefinition d = schema.getAttributeType(name);
              if (d != null) {
                String originalValue = d.toString();
                String canonicalValue = SchemaCompareUtil.canonical(d, includeExtensions);
                String checksum = checksum(canonicalValue);
                loggingService.logSchemaElement(server, elementTypeName, name, 
                    originalValue, canonicalValue, checksum);
                yield checksum;
              } else {
                yield "MISSING";
              }
            }
            case MATCHING_RULE -> {
              MatchingRuleDefinition d = schema.getMatchingRule(name);
              if (d != null) {
                String originalValue = d.toString();
                String canonicalValue = SchemaCompareUtil.canonical(d, includeExtensions);
                String checksum = checksum(canonicalValue);
                loggingService.logSchemaElement(server, elementTypeName, name, 
                    originalValue, canonicalValue, checksum);
                yield checksum;
              } else {
                yield "MISSING";
              }
            }
            case MATCHING_RULE_USE -> {
              MatchingRuleUseDefinition d = schema.getMatchingRuleUse(name);
              if (d != null) {
                String originalValue = d.toString();
                String canonicalValue = SchemaCompareUtil.canonical(d, includeExtensions);
                String checksum = checksum(canonicalValue);
                loggingService.logSchemaElement(server, elementTypeName, name, 
                    originalValue, canonicalValue, checksum);
                yield checksum;
              } else {
                yield "MISSING";
              }
            }
            case SYNTAX -> {
              AttributeSyntaxDefinition d = schema.getAttributeSyntax(name);
              if (d != null) {
                String originalValue = d.toString();
                String canonicalValue = SchemaCompareUtil.canonical(d, includeExtensions);
                String checksum = checksum(canonicalValue);
                loggingService.logSchemaElement(server, elementTypeName, name, 
                    originalValue, canonicalValue, checksum);
                yield checksum;
              } else {
                yield "MISSING";
              }
            }
          };
          checksums.put(server, sum);
        } catch (Exception ex) {
          checksums.put(server, "ERROR");
          loggingService.logSchemaError(server, elementTypeName, name, ex.getMessage());
        }
      }
      
      rows.put(name, checksums);
    }

    // Log completion summary
    for (Map.Entry<String, Schema> e : schemas.entrySet()) {
      String serverName = e.getKey();
      Schema schema = e.getValue();
      if (schema != null) {
        int processedCount = (int) rows.values().stream()
            .filter(checksums -> !checksums.getOrDefault(serverName, "").equals("ERROR") 
                && !checksums.getOrDefault(serverName, "").equals("MISSING"))
            .count();
        int errorCount = (int) rows.values().stream()
            .filter(checksums -> checksums.getOrDefault(serverName, "").equals("ERROR"))
            .count();
        loggingService.logSchemaComparisonEnd(serverName, elementTypeName, 
            processedCount, errorCount);
      }
    }

    return rows;
  }

  private List<RowModel> toModels(Map<String, Map<String, String>> rows, 
      Collection<String> servers) {
    List<RowModel> list = new ArrayList<>();
    for (Map.Entry<String, Map<String, String>> e : rows.entrySet()) {
      RowModel m = new RowModel(e.getKey(), e.getValue());
      list.add(m);
    }
    list.sort(Comparator.comparing(RowModel::getName));
    return list;
  }

  private String checksum(String text) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < hash.length; i++) {
        sb.append(String.format("%02x", hash[i]));
      }
      return sb.substring(0, 8);
    } catch (Exception e) {
      return "ERR";
    }
  }

  private void applyFilter(String filterText) {
    if (filterText == null || filterText.trim().isEmpty()) {
      ocDataProvider.clearFilters();
      atDataProvider.clearFilters();
      mrDataProvider.clearFilters();
      mruDataProvider.clearFilters();
      synDataProvider.clearFilters();
    } else {
      String lower = filterText.toLowerCase();
      ocDataProvider.setFilter(row -> row.getName().toLowerCase().contains(lower));
      atDataProvider.setFilter(row -> row.getName().toLowerCase().contains(lower));
      mrDataProvider.setFilter(row -> row.getName().toLowerCase().contains(lower));
      mruDataProvider.setFilter(row -> row.getName().toLowerCase().contains(lower));
      synDataProvider.setFilter(row -> row.getName().toLowerCase().contains(lower));
    }
  }

  private String displayName(LdapServerConfig cfg) {
    return cfg.getName();
  }

  private void showSchemaElementDetails(RowModel rowModel) {
    detailsPanel.removeAll();
    detailsPanel.setVisible(true);

    H3 detailTitle = new H3("Details: " + rowModel.getName());
    detailsPanel.add(detailTitle);

    // Determine which tab is active to get the correct schema type
    Tab selectedTab = tabSheet.getSelectedTab();
    ComponentType currentType = getTypeForTab(selectedTab);

    // Reload schemas for details
    Map<String, Schema> schemas = new LinkedHashMap<>();
    for (LdapServerConfig cfg : sortedServers) {
      try {
        Schema schema = ldapService.getSchema(cfg.getName(), false);
        schemas.put(displayName(cfg), schema);
      } catch (LDAPException e) {
        schemas.put(displayName(cfg), null);
      }
    }

    // Build comparison grid
    Grid<SchemaPropertyRow> comparisonGrid = new Grid<>(SchemaPropertyRow.class, false);
    comparisonGrid.setSizeFull();
    comparisonGrid.addColumn(SchemaPropertyRow::getProperty)
        .setHeader("Property")
        .setAutoWidth(true)
        .setResizable(true)
        .setFrozen(true);

    for (String serverName : sortedServers.stream().map(this::displayName).toList()) {
      comparisonGrid.addColumn(row -> row.getValue(serverName))
          .setHeader(serverName)
          .setAutoWidth(true)
          .setResizable(true)
          .setSortable(true);
    }

    List<SchemaPropertyRow> propertyRows = loadSchemaElementDetails(rowModel, schemas, 
        currentType);
    comparisonGrid.setItems(propertyRows);

    detailsPanel.add(comparisonGrid);
    splitLayout.setSplitterPosition(50);
  }

  private ComponentType getTypeForTab(Tab tab) {
    if (tab == null) {
      return ComponentType.OBJECT_CLASS;
    }
    String label = tab.getLabel();
    return switch (label) {
      case "Attribute Types" -> ComponentType.ATTRIBUTE_TYPE;
      case "Matching Rules" -> ComponentType.MATCHING_RULE;
      case "Matching Rule Use" -> ComponentType.MATCHING_RULE_USE;
      case "Syntaxes" -> ComponentType.SYNTAX;
      default -> ComponentType.OBJECT_CLASS;
    };
  }

  private void hideDetails() {
    detailsPanel.setVisible(false);
    splitLayout.setSplitterPosition(100);
  }

  private List<SchemaPropertyRow> loadSchemaElementDetails(RowModel rowModel, 
      Map<String, Schema> schemas, ComponentType type) {
    List<SchemaPropertyRow> propertyRows = new ArrayList<>();
    String elementName = rowModel.getName();

    switch (type) {
      case OBJECT_CLASS -> addObjectClassProperties(propertyRows, elementName, schemas);
      case ATTRIBUTE_TYPE -> addAttributeTypeProperties(propertyRows, elementName, schemas);
      case MATCHING_RULE -> addMatchingRuleProperties(propertyRows, elementName, schemas);
      case MATCHING_RULE_USE -> addMatchingRuleUseProperties(propertyRows, elementName, schemas);
      case SYNTAX -> addSyntaxProperties(propertyRows, elementName, schemas);
    }

    return propertyRows;
  }

  private void addObjectClassProperties(List<SchemaPropertyRow> propertyRows, 
      String elementName, Map<String, Schema> schemas) {
    addProperty(propertyRows, "OID", elementName, schemas, (schema, name) -> {
      ObjectClassDefinition def = schema.getObjectClass(name);
      return def != null ? def.getOID() : "N/A";
    });

    addProperty(propertyRows, "Names", elementName, schemas, (schema, name) -> {
      ObjectClassDefinition def = schema.getObjectClass(name);
      return def != null && def.getNames() != null 
          ? String.join(", ", Arrays.asList(def.getNames())) : "N/A";
    });

    addProperty(propertyRows, "Description", elementName, schemas, (schema, name) -> {
      ObjectClassDefinition def = schema.getObjectClass(name);
      return def != null ? (def.getDescription() != null ? def.getDescription() : "N/A") : "N/A";
    });

    addProperty(propertyRows, "Type", elementName, schemas, (schema, name) -> {
      ObjectClassDefinition def = schema.getObjectClass(name);
      return def != null && def.getObjectClassType() != null 
          ? def.getObjectClassType().getName() : "N/A";
    });

    addProperty(propertyRows, "Obsolete", elementName, schemas, (schema, name) -> {
      ObjectClassDefinition def = schema.getObjectClass(name);
      return def != null ? (def.isObsolete() ? "Yes" : "No") : "N/A";
    });

    addProperty(propertyRows, "Superior Classes", elementName, schemas, (schema, name) -> {
      ObjectClassDefinition def = schema.getObjectClass(name);
      return def != null && def.getSuperiorClasses() != null 
          ? String.join(", ", def.getSuperiorClasses()) : "N/A";
    });
  }

  private void addAttributeTypeProperties(List<SchemaPropertyRow> propertyRows, 
      String elementName, Map<String, Schema> schemas) {
    addProperty(propertyRows, "OID", elementName, schemas, (schema, name) -> {
      AttributeTypeDefinition def = schema.getAttributeType(name);
      return def != null ? def.getOID() : "N/A";
    });

    addProperty(propertyRows, "Names", elementName, schemas, (schema, name) -> {
      AttributeTypeDefinition def = schema.getAttributeType(name);
      return def != null && def.getNames() != null 
          ? String.join(", ", Arrays.asList(def.getNames())) : "N/A";
    });

    addProperty(propertyRows, "Description", elementName, schemas, (schema, name) -> {
      AttributeTypeDefinition def = schema.getAttributeType(name);
      return def != null ? (def.getDescription() != null ? def.getDescription() : "N/A") : "N/A";
    });

    addProperty(propertyRows, "Syntax", elementName, schemas, (schema, name) -> {
      AttributeTypeDefinition def = schema.getAttributeType(name);
      return def != null ? (def.getSyntaxOID() != null ? def.getSyntaxOID() : "N/A") : "N/A";
    });
  }

  private void addMatchingRuleProperties(List<SchemaPropertyRow> propertyRows, 
      String elementName, Map<String, Schema> schemas) {
    addProperty(propertyRows, "OID", elementName, schemas, (schema, name) -> {
      MatchingRuleDefinition def = schema.getMatchingRule(name);
      return def != null ? def.getOID() : "N/A";
    });

    addProperty(propertyRows, "Names", elementName, schemas, (schema, name) -> {
      MatchingRuleDefinition def = schema.getMatchingRule(name);
      return def != null && def.getNames() != null 
          ? String.join(", ", Arrays.asList(def.getNames())) : "N/A";
    });

    addProperty(propertyRows, "Description", elementName, schemas, (schema, name) -> {
      MatchingRuleDefinition def = schema.getMatchingRule(name);
      return def != null ? (def.getDescription() != null ? def.getDescription() : "N/A") : "N/A";
    });
  }

  private void addMatchingRuleUseProperties(List<SchemaPropertyRow> propertyRows, 
      String elementName, Map<String, Schema> schemas) {
    addProperty(propertyRows, "OID", elementName, schemas, (schema, name) -> {
      MatchingRuleUseDefinition def = schema.getMatchingRuleUse(name);
      return def != null ? def.getOID() : "N/A";
    });

    addProperty(propertyRows, "Names", elementName, schemas, (schema, name) -> {
      MatchingRuleUseDefinition def = schema.getMatchingRuleUse(name);
      return def != null && def.getNames() != null 
          ? String.join(", ", Arrays.asList(def.getNames())) : "N/A";
    });

    addProperty(propertyRows, "Description", elementName, schemas, (schema, name) -> {
      MatchingRuleUseDefinition def = schema.getMatchingRuleUse(name);
      return def != null ? (def.getDescription() != null ? def.getDescription() : "N/A") : "N/A";
    });
  }

  private void addSyntaxProperties(List<SchemaPropertyRow> propertyRows, String elementName, 
      Map<String, Schema> schemas) {
    addProperty(propertyRows, "OID", elementName, schemas, (schema, name) -> {
      AttributeSyntaxDefinition def = schema.getAttributeSyntax(name);
      return def != null ? def.getOID() : "N/A";
    });

    addProperty(propertyRows, "Description", elementName, schemas, (schema, name) -> {
      AttributeSyntaxDefinition def = schema.getAttributeSyntax(name);
      return def != null ? (def.getDescription() != null ? def.getDescription() : "N/A") : "N/A";
    });
  }

  private void addProperty(List<SchemaPropertyRow> propertyRows, String propertyName, 
      String elementName, Map<String, Schema> schemas, PropertyExtractor extractor) {
    Map<String, String> values = new LinkedHashMap<>();
    for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
      String serverName = entry.getKey();
      Schema schema = entry.getValue();
      String value;
      if (schema == null) {
        value = "ERROR";
      } else {
        try {
          value = extractor.extract(schema, elementName);
        } catch (Exception e) {
          value = "ERROR";
        }
      }
      values.put(serverName, value);
    }
    propertyRows.add(new SchemaPropertyRow(propertyName, values));
  }

  @FunctionalInterface
  private interface PropertyExtractor {
    String extract(Schema schema, String elementName);
  }

  /**
   * Represents a row in the schema comparison grid.
   */
  public static class SchemaPropertyRow {
    private final String property;
    private final Map<String, String> values;

    public SchemaPropertyRow(String property, Map<String, String> values) {
      this.property = property;
      this.values = values;
    }

    public String getProperty() {
      return property;
    }

    public String getValue(String serverName) {
      return values.getOrDefault(serverName, "N/A");
    }

    public Map<String, String> getValues() {
      return values;
    }
  }

  /**
   * Represents a row in the grid with dynamic per-server checksum map.
   */
  public static class RowModel {
    private final String name;
    private final Map<String, String> checksums;

    public RowModel(String name, Map<String, String> checksums) {
      this.name = name;
      this.checksums = checksums;
    }

    public String getName() {
      return name;
    }

    public String getChecksum(String serverName) {
      return checksums.getOrDefault(serverName, "");
    }

    public boolean isEqualAcross(Collection<String> serverNames) {
      String ref = null;
      for (String s : serverNames) {
        String v = checksums.get(s);
        if (v == null) {
          continue;
        }
        if (ref == null) {
          ref = v;
        } else if (!ref.equals(v)) {
          return false;
        }
      }
      return ref != null;
    }
  }
}
