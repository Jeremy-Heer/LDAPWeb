package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.model.SchemaElement;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.ldapbrowser.ui.utils.SchemaDetailDialogHelper;
import com.unboundid.ldap.sdk.schema.AttributeSyntaxDefinition;
import com.unboundid.ldap.sdk.schema.AttributeTypeDefinition;
import com.unboundid.ldap.sdk.schema.MatchingRuleDefinition;
import com.unboundid.ldap.sdk.schema.MatchingRuleUseDefinition;
import com.unboundid.ldap.sdk.schema.ObjectClassDefinition;
import com.unboundid.ldap.sdk.schema.Schema;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schema management tab component.
 * Displays and manages LDAP schema elements across multiple servers.
 */
public class SchemaManageTab extends VerticalLayout {

  private static final Logger logger = LoggerFactory.getLogger(SchemaManageTab.class);

  private final LdapService ldapService;
  private final ConfigurationService configService;

  private Tabs schemaTabs;
  private TextField searchField;
  private Button refreshButton;
  private Button addObjectClassButton;
  private Button addAttributeTypeButton;
  private VerticalLayout gridContainer;
  private VerticalLayout detailsPanel;

  private Grid<SchemaElement<ObjectClassDefinition>> objectClassGrid;
  private Grid<SchemaElement<AttributeTypeDefinition>> attributeTypeGrid;
  private Grid<SchemaElement<MatchingRuleDefinition>> matchingRuleGrid;
  private Grid<SchemaElement<MatchingRuleUseDefinition>> matchingRuleUseGrid;
  private Grid<SchemaElement<AttributeSyntaxDefinition>> syntaxGrid;

  private String currentView = "objectClasses";
  private String currentFilter = "";

  /**
   * Creates the schema manage tab.
   *
   * @param ldapService LDAP service
   * @param configService configuration service
   */
  public SchemaManageTab(LdapService ldapService, ConfigurationService configService) {
    this.ldapService = ldapService;
    this.configService = configService;

    initializeComponents();
    setupLayout();
    
    // Set initial button visibility based on default tab (objectClasses)
    updateAddButtons();
  }

  private void initializeComponents() {
    // Search controls
    searchField = new TextField();
    searchField.setPlaceholder("Search schema elements...");
    searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
    searchField.setValueChangeMode(ValueChangeMode.LAZY);
    searchField.addValueChangeListener(e -> {
      currentFilter = e.getValue();
      filterCurrentView();
    });
    searchField.setWidth("300px");

    refreshButton = new Button("Refresh", new Icon(VaadinIcon.REFRESH));
    refreshButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
    refreshButton.addClickListener(e -> refreshSchemaCache());

    // Add schema element buttons
    addObjectClassButton = new Button("Add Object Class", new Icon(VaadinIcon.PLUS));
    addObjectClassButton.addThemeVariants(ButtonVariant.LUMO_SMALL,
        ButtonVariant.LUMO_PRIMARY);
    addObjectClassButton.addClickListener(e -> openAddObjectClassDialog());
    addObjectClassButton.setEnabled(false);

    addAttributeTypeButton = new Button("Add Attribute Type", new Icon(VaadinIcon.PLUS));
    addAttributeTypeButton.addThemeVariants(ButtonVariant.LUMO_SMALL,
        ButtonVariant.LUMO_PRIMARY);
    addAttributeTypeButton.addClickListener(e -> openAddAttributeTypeDialog());
    addAttributeTypeButton.setEnabled(false);

    // Schema tabs
    schemaTabs = new Tabs();
    Tab objectClassTab = new Tab("Object Classes");
    Tab attributeTypeTab = new Tab("Attribute Types");
    Tab matchingRuleTab = new Tab("Matching Rules");
    Tab matchingRuleUseTab = new Tab("Matching Rule Use");
    Tab syntaxTab = new Tab("Syntaxes");

    schemaTabs.add(objectClassTab, attributeTypeTab, matchingRuleTab,
        matchingRuleUseTab, syntaxTab);
    schemaTabs.addSelectedChangeListener(e -> {
      Tab selectedTab = e.getSelectedTab();
      if (selectedTab == objectClassTab) {
        currentView = "objectClasses";
        showObjectClasses();
        updateAddButtons();
      } else if (selectedTab == attributeTypeTab) {
        currentView = "attributeTypes";
        showAttributeTypes();
        updateAddButtons();
      } else if (selectedTab == matchingRuleTab) {
        currentView = "matchingRules";
        showMatchingRules();
        updateAddButtons();
      } else if (selectedTab == matchingRuleUseTab) {
        currentView = "matchingRuleUse";
        showMatchingRuleUse();
        updateAddButtons();
      } else if (selectedTab == syntaxTab) {
        currentView = "syntaxes";
        showSyntaxes();
        updateAddButtons();
      }
    });

    // Initialize grids
    initializeObjectClassGrid();
    initializeAttributeTypeGrid();
    initializeMatchingRuleGrid();
    initializeMatchingRuleUseGrid();
    initializeSyntaxGrid();

    gridContainer = new VerticalLayout();
    gridContainer.setSizeFull();
    gridContainer.setPadding(false);
    gridContainer.setSpacing(false);

    detailsPanel = new VerticalLayout();
    detailsPanel.setSizeFull();
    detailsPanel.setPadding(true);
    detailsPanel.setSpacing(true);
  }

  private void initializeObjectClassGrid() {
    objectClassGrid = new Grid<>();
    objectClassGrid.setSizeFull();
    objectClassGrid.addClassName("schema-grid");

    objectClassGrid.addColumn(SchemaElement::getServerName)
        .setHeader("Server")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    objectClassGrid.addColumn(se -> {
      ObjectClassDefinition oc = se.getElement();
      if (oc.getNames() != null && oc.getNames().length > 0) {
        return String.join(", ", Arrays.asList(oc.getNames()));
      }
      return oc.getOID();
    })
        .setHeader("Name")
        .setFlexGrow(2)
        .setResizable(true)
        .setSortable(true);

    objectClassGrid.addColumn(se -> se.getElement().getOID())
        .setHeader("OID")
        .setFlexGrow(2)
        .setResizable(true)
        .setSortable(true);

    objectClassGrid.addColumn(se -> se.getElement().getDescription() != null
            ? se.getElement().getDescription() : "")
        .setHeader("Description")
        .setFlexGrow(3)
        .setResizable(true)
        .setSortable(true);

    objectClassGrid.addColumn(se -> se.getElement().getObjectClassType() != null
            ? se.getElement().getObjectClassType().getName() : "")
        .setHeader("Type")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    objectClassGrid.addColumn(se -> se.getElement().isObsolete() ? "Yes" : "No")
        .setHeader("Obsolete")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    // Schema File column from X-Schema-file extension (when available)
    objectClassGrid.addColumn(se -> getSchemaFileFromExtensions(se.getElement().getExtensions()))
        .setHeader("Schema File")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    objectClassGrid.asSingleSelect().addValueChangeListener(e -> {
      if (e.getValue() != null) {
        showObjectClassDetails(e.getValue());
      }
    });
  }

  private void initializeAttributeTypeGrid() {
    attributeTypeGrid = new Grid<>();
    attributeTypeGrid.setSizeFull();
    attributeTypeGrid.addClassName("schema-grid");

    attributeTypeGrid.addColumn(SchemaElement::getServerName)
        .setHeader("Server")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    attributeTypeGrid.addColumn(se -> {
      AttributeTypeDefinition at = se.getElement();
      if (at.getNames() != null && at.getNames().length > 0) {
        return String.join(", ", Arrays.asList(at.getNames()));
      }
      return at.getOID();
    })
        .setHeader("Name")
        .setFlexGrow(2)
        .setResizable(true)
        .setSortable(true);

    attributeTypeGrid.addColumn(se -> se.getElement().getOID())
        .setHeader("OID")
        .setFlexGrow(2)
        .setResizable(true)
        .setSortable(true);

    attributeTypeGrid.addColumn(se -> se.getElement().getDescription() != null
            ? se.getElement().getDescription() : "")
        .setHeader("Description")
        .setFlexGrow(3)
        .setResizable(true)
        .setSortable(true);

    attributeTypeGrid.addColumn(se -> se.getElement().getSyntaxOID() != null
            ? se.getElement().getSyntaxOID() : "")
        .setHeader("Syntax OID")
        .setFlexGrow(2)
        .setResizable(true)
        .setSortable(true);

    attributeTypeGrid.addColumn(se -> se.getElement().isObsolete() ? "Yes" : "No")
        .setHeader("Obsolete")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    // Schema File column from X-Schema-file extension (when available)
    attributeTypeGrid.addColumn(se -> getSchemaFileFromExtensions(se.getElement().getExtensions()))
        .setHeader("Schema File")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    attributeTypeGrid.asSingleSelect().addValueChangeListener(e -> {
      if (e.getValue() != null) {
        showAttributeTypeDetails(e.getValue());
      }
    });
  }

  private void initializeMatchingRuleGrid() {
    matchingRuleGrid = new Grid<>();
    matchingRuleGrid.setSizeFull();
    matchingRuleGrid.addClassName("schema-grid");

    matchingRuleGrid.addColumn(SchemaElement::getServerName)
        .setHeader("Server")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    matchingRuleGrid.addColumn(se -> se.getElement().getNameOrOID())
        .setHeader("Name")
        .setFlexGrow(2)
        .setResizable(true)
        .setSortable(true);

    matchingRuleGrid.addColumn(se -> se.getElement().getOID())
        .setHeader("OID")
        .setFlexGrow(2)
        .setResizable(true)
        .setSortable(true);

    matchingRuleGrid.addColumn(se -> se.getElement().getDescription() != null
            ? se.getElement().getDescription() : "")
        .setHeader("Description")
        .setFlexGrow(3)
        .setResizable(true)
        .setSortable(true);

    matchingRuleGrid.addColumn(se -> se.getElement().getSyntaxOID() != null
            ? se.getElement().getSyntaxOID() : "")
        .setHeader("Syntax OID")
        .setFlexGrow(2)
        .setResizable(true)
        .setSortable(true);

    matchingRuleGrid.addColumn(se -> se.getElement().isObsolete() ? "Yes" : "No")
        .setHeader("Obsolete")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    // Schema File column from X-Schema-file extension (when available)
    matchingRuleGrid.addColumn(se -> getSchemaFileFromExtensions(se.getElement().getExtensions()))
        .setHeader("Schema File")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    matchingRuleGrid.asSingleSelect().addValueChangeListener(e -> {
      if (e.getValue() != null) {
        showMatchingRuleDetails(e.getValue());
      }
    });
  }

  private void initializeMatchingRuleUseGrid() {
    matchingRuleUseGrid = new Grid<>();
    matchingRuleUseGrid.setSizeFull();
    matchingRuleUseGrid.addClassName("schema-grid");

    matchingRuleUseGrid.addColumn(SchemaElement::getServerName)
        .setHeader("Server")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    matchingRuleUseGrid.addColumn(se -> se.getElement().getOID())
        .setHeader("OID")
        .setFlexGrow(2)
        .setResizable(true)
        .setSortable(true);

    matchingRuleUseGrid.addColumn(se -> se.getElement().getDescription() != null
            ? se.getElement().getDescription() : "")
        .setHeader("Description")
        .setFlexGrow(3)
        .setResizable(true)
        .setSortable(true);

    matchingRuleUseGrid.addColumn(se -> se.getElement().isObsolete() ? "Yes" : "No")
        .setHeader("Obsolete")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    // Schema File column from X-Schema-file extension (when available)
    matchingRuleUseGrid.addColumn(se -> getSchemaFileFromExtensions(se.getElement().getExtensions()))
        .setHeader("Schema File")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    matchingRuleUseGrid.asSingleSelect().addValueChangeListener(e -> {
      if (e.getValue() != null) {
        showMatchingRuleUseDetails(e.getValue());
      }
    });
  }

  private void initializeSyntaxGrid() {
    syntaxGrid = new Grid<>();
    syntaxGrid.setSizeFull();
    syntaxGrid.addClassName("schema-grid");

    syntaxGrid.addColumn(SchemaElement::getServerName)
        .setHeader("Server")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    syntaxGrid.addColumn(se -> se.getElement().getOID())
        .setHeader("OID")
        .setFlexGrow(2)
        .setResizable(true)
        .setSortable(true);

    syntaxGrid.addColumn(se -> se.getElement().getDescription() != null
            ? se.getElement().getDescription() : "")
        .setHeader("Description")
        .setFlexGrow(4)
        .setResizable(true)
        .setSortable(true);

    // Schema File column from X-Schema-file extension (when available)
    syntaxGrid.addColumn(se -> getSchemaFileFromExtensions(se.getElement().getExtensions()))
        .setHeader("Schema File")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true);

    syntaxGrid.asSingleSelect().addValueChangeListener(e -> {
      if (e.getValue() != null) {
        showSyntaxDetails(e.getValue());
      }
    });
  }

  private void setupLayout() {
    setSizeFull();
    setPadding(false);
    setSpacing(false);

    // Create left panel with schema browser controls and search results
    VerticalLayout leftPanel = new VerticalLayout();
    leftPanel.setSizeFull();
    leftPanel.setPadding(false);
    leftPanel.setSpacing(false);

    // Left panel header
    HorizontalLayout schemaHeader = new HorizontalLayout();
    schemaHeader.setWidthFull();
    schemaHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    schemaHeader.setPadding(true);
    schemaHeader.getStyle().set("margin-bottom", "0px");

    Icon schemaIcon = new Icon(VaadinIcon.COGS);
    schemaIcon.setSize("16px");
    schemaIcon.getStyle().set("color", "#4a90e2");

    H3 title = new H3("Schema Browser");
    title.addClassNames(LumoUtility.Margin.NONE);
    title.getStyle()
        .set("font-size", "0.9em")
        .set("font-weight", "600")
        .set("color", "#333");

    schemaHeader.add(schemaIcon, title, searchField, addObjectClassButton,
        addAttributeTypeButton, refreshButton);
    schemaHeader.setFlexGrow(1, title);

    // Schema tabs container
    VerticalLayout schemaTabsContainer = new VerticalLayout();
    schemaTabsContainer.setWidthFull();
    schemaTabsContainer.setPadding(false);
    schemaTabsContainer.setSpacing(false);
    schemaTabsContainer.add(schemaTabs);

    leftPanel.add(schemaHeader, schemaTabsContainer, gridContainer);
    leftPanel.setFlexGrow(1, gridContainer);

    // Right panel for details
    VerticalLayout rightPanel = new VerticalLayout();
    rightPanel.setSizeFull();
    rightPanel.setPadding(false);
    rightPanel.setSpacing(false);

    HorizontalLayout detailsHeader = new HorizontalLayout();
    detailsHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    detailsHeader.setWidthFull();
    detailsHeader.setPadding(true);

    H3 detailsTitle = new H3("Details");
    detailsTitle.addClassNames(LumoUtility.Margin.NONE);
    detailsTitle.getStyle()
        .set("font-size", "0.9em")
        .set("font-weight", "600")
        .set("color", "#333");

    detailsHeader.add(detailsTitle);
    rightPanel.add(detailsHeader, detailsPanel);
    rightPanel.setFlexGrow(1, detailsPanel);

    // Main horizontal split with resizable divider
    SplitLayout mainLayout = new SplitLayout();
    mainLayout.setSizeFull();
    mainLayout.addToPrimary(leftPanel);
    mainLayout.addToSecondary(rightPanel);
    mainLayout.setSplitterPosition(70); // 70% for grid, 30% for details

    add(mainLayout);
    setFlexGrow(1, mainLayout);
  }

  /**
   * Refreshes the schema cache from the LDAP server and reloads the UI.
   */
  private void refreshSchemaCache() {
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers == null || selectedServers.isEmpty()) {
      NotificationHelper.showError("No servers selected");
      return;
    }

    try {
      // Refresh schema cache for each selected server
      for (String serverName : selectedServers) {
        configService.getConfiguration(serverName).ifPresent(config -> {
          try {
            ldapService.refreshSchema(config);
            logger.info("Refreshed schema cache for server: {}", serverName);
          } catch (Exception ex) {
            logger.warn("Failed to refresh schema cache for {}: {}", serverName, ex.getMessage());
            NotificationHelper.showError("Failed to refresh schema for " + serverName + ": " + ex.getMessage());
          }
        });
      }
      
      // Now reload the UI with the refreshed cache
      loadSchemas();
    } catch (Exception e) {
      NotificationHelper.showError("Failed to refresh schema cache: " + e.getMessage());
    }
  }

  /**
   * Loads schemas from all selected servers.
   */
  public void loadSchemas() {
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers == null || selectedServers.isEmpty()) {
      NotificationHelper.showError("No servers selected");
      return;
    }

    try {
      filterCurrentView();
      updateAddButtons();
      NotificationHelper.showSuccess("Schema loaded successfully");
    } catch (Exception e) {
      NotificationHelper.showError("Failed to load schema: " + e.getMessage());
    }
  }

  private void showObjectClasses() {
    clearGridContainer();
    gridContainer.add(objectClassGrid);

    List<SchemaElement<ObjectClassDefinition>> allElements = new ArrayList<>();
    Set<String> selectedServers = MainLayout.getSelectedServers();

    for (String serverName : selectedServers) {
      try {
        LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
        if (config == null) {
          continue;
        }

        Schema schema = ldapService.getSchema(config);
        if (schema != null) {
          Collection<ObjectClassDefinition> objectClasses = schema.getObjectClasses();
          for (ObjectClassDefinition oc : objectClasses) {
            allElements.add(new SchemaElement<>(oc, serverName, config));
          }
        }
      } catch (Exception e) {
        NotificationHelper.showError("Failed to load schema from " + serverName + ": " + e.getMessage());
      }
    }

    List<SchemaElement<ObjectClassDefinition>> filtered = filterObjectClasses(allElements);
    objectClassGrid.setItems(filtered);
  }

  private void showAttributeTypes() {
    clearGridContainer();
    gridContainer.add(attributeTypeGrid);

    List<SchemaElement<AttributeTypeDefinition>> allElements = new ArrayList<>();
    Set<String> selectedServers = MainLayout.getSelectedServers();

    for (String serverName : selectedServers) {
      try {
        LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
        if (config == null) {
          continue;
        }

        Schema schema = ldapService.getSchema(config);
        if (schema != null) {
          Collection<AttributeTypeDefinition> attributeTypes = schema.getAttributeTypes();
          for (AttributeTypeDefinition at : attributeTypes) {
            allElements.add(new SchemaElement<>(at, serverName, config));
          }
        }
      } catch (Exception e) {
        NotificationHelper.showError("Failed to load schema from " + serverName + ": " + e.getMessage());
      }
    }

    List<SchemaElement<AttributeTypeDefinition>> filtered = filterAttributeTypes(allElements);
    attributeTypeGrid.setItems(filtered);
  }

  private void showMatchingRules() {
    clearGridContainer();
    gridContainer.add(matchingRuleGrid);

    List<SchemaElement<MatchingRuleDefinition>> allElements = new ArrayList<>();
    Set<String> selectedServers = MainLayout.getSelectedServers();

    for (String serverName : selectedServers) {
      try {
        LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
        if (config == null) {
          continue;
        }

        Schema schema = ldapService.getSchema(config);
        if (schema != null) {
          Collection<MatchingRuleDefinition> matchingRules = schema.getMatchingRules();
          for (MatchingRuleDefinition mr : matchingRules) {
            allElements.add(new SchemaElement<>(mr, serverName));
          }
        }
      } catch (Exception e) {
        NotificationHelper.showError("Failed to load schema from " + serverName + ": " + e.getMessage());
      }
    }

    List<SchemaElement<MatchingRuleDefinition>> filtered = filterMatchingRules(allElements);
    matchingRuleGrid.setItems(filtered);
  }

  private void showMatchingRuleUse() {
    clearGridContainer();
    gridContainer.add(matchingRuleUseGrid);

    List<SchemaElement<MatchingRuleUseDefinition>> allElements = new ArrayList<>();
    Set<String> selectedServers = MainLayout.getSelectedServers();

    for (String serverName : selectedServers) {
      try {
        LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
        if (config == null) {
          continue;
        }

        Schema schema = ldapService.getSchema(config);
        if (schema != null) {
          Collection<MatchingRuleUseDefinition> matchingRuleUses = schema.getMatchingRuleUses();
          for (MatchingRuleUseDefinition mru : matchingRuleUses) {
            allElements.add(new SchemaElement<>(mru, serverName));
          }
        }
      } catch (Exception e) {
        NotificationHelper.showError("Failed to load schema from " + serverName + ": " + e.getMessage());
      }
    }

    List<SchemaElement<MatchingRuleUseDefinition>> filtered = filterMatchingRuleUse(allElements);
    matchingRuleUseGrid.setItems(filtered);
  }

  private void showSyntaxes() {
    clearGridContainer();
    gridContainer.add(syntaxGrid);

    List<SchemaElement<AttributeSyntaxDefinition>> allElements = new ArrayList<>();
    Set<String> selectedServers = MainLayout.getSelectedServers();

    for (String serverName : selectedServers) {
      try {
        LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
        if (config == null) {
          continue;
        }

        Schema schema = ldapService.getSchema(config);
        if (schema != null) {
          Collection<AttributeSyntaxDefinition> syntaxes = schema.getAttributeSyntaxes();
          for (AttributeSyntaxDefinition syn : syntaxes) {
            allElements.add(new SchemaElement<>(syn, serverName));
          }
        }
      } catch (Exception e) {
        NotificationHelper.showError("Failed to load schema from " + serverName + ": " + e.getMessage());
      }
    }

    List<SchemaElement<AttributeSyntaxDefinition>> filtered = filterSyntaxes(allElements);
    syntaxGrid.setItems(filtered);
  }

  private void clearGridContainer() {
    gridContainer.removeAll();
  }

  private void filterCurrentView() {
    switch (currentView) {
      case "objectClasses":
        showObjectClasses();
        break;
      case "attributeTypes":
        showAttributeTypes();
        break;
      case "matchingRules":
        showMatchingRules();
        break;
      case "matchingRuleUse":
        showMatchingRuleUse();
        break;
      case "syntaxes":
        showSyntaxes();
        break;
      default:
        break;
    }
  }

  private List<SchemaElement<ObjectClassDefinition>> filterObjectClasses(
      List<SchemaElement<ObjectClassDefinition>> elements) {
    if (currentFilter == null || currentFilter.trim().isEmpty()) {
      return elements;
    }

    String filter = currentFilter.toLowerCase().trim();
    return elements.stream()
        .filter(se -> {
          ObjectClassDefinition oc = se.getElement();
          return (oc.getNameOrOID() != null
              && oc.getNameOrOID().toLowerCase().contains(filter))
              || (oc.getOID() != null && oc.getOID().toLowerCase().contains(filter))
              || (oc.getDescription() != null
              && oc.getDescription().toLowerCase().contains(filter))
              || se.getServerName().toLowerCase().contains(filter);
        })
        .collect(Collectors.toList());
  }

  private List<SchemaElement<AttributeTypeDefinition>> filterAttributeTypes(
      List<SchemaElement<AttributeTypeDefinition>> elements) {
    if (currentFilter == null || currentFilter.trim().isEmpty()) {
      return elements;
    }

    String filter = currentFilter.toLowerCase().trim();
    return elements.stream()
        .filter(se -> {
          AttributeTypeDefinition at = se.getElement();
          return (at.getNameOrOID() != null
              && at.getNameOrOID().toLowerCase().contains(filter))
              || (at.getOID() != null && at.getOID().toLowerCase().contains(filter))
              || (at.getDescription() != null
              && at.getDescription().toLowerCase().contains(filter))
              || se.getServerName().toLowerCase().contains(filter);
        })
        .collect(Collectors.toList());
  }

  private List<SchemaElement<MatchingRuleDefinition>> filterMatchingRules(
      List<SchemaElement<MatchingRuleDefinition>> elements) {
    if (currentFilter == null || currentFilter.trim().isEmpty()) {
      return elements;
    }

    String filter = currentFilter.toLowerCase().trim();
    return elements.stream()
        .filter(se -> {
          MatchingRuleDefinition mr = se.getElement();
          return (mr.getNameOrOID() != null
              && mr.getNameOrOID().toLowerCase().contains(filter))
              || (mr.getOID() != null && mr.getOID().toLowerCase().contains(filter))
              || (mr.getDescription() != null
              && mr.getDescription().toLowerCase().contains(filter))
              || se.getServerName().toLowerCase().contains(filter);
        })
        .collect(Collectors.toList());
  }

  private List<SchemaElement<MatchingRuleUseDefinition>> filterMatchingRuleUse(
      List<SchemaElement<MatchingRuleUseDefinition>> elements) {
    if (currentFilter == null || currentFilter.trim().isEmpty()) {
      return elements;
    }

    String filter = currentFilter.toLowerCase().trim();
    return elements.stream()
        .filter(se -> {
          MatchingRuleUseDefinition mru = se.getElement();
          return (mru.getOID() != null && mru.getOID().toLowerCase().contains(filter))
              || (mru.getDescription() != null
              && mru.getDescription().toLowerCase().contains(filter))
              || se.getServerName().toLowerCase().contains(filter);
        })
        .collect(Collectors.toList());
  }

  private List<SchemaElement<AttributeSyntaxDefinition>> filterSyntaxes(
      List<SchemaElement<AttributeSyntaxDefinition>> elements) {
    if (currentFilter == null || currentFilter.trim().isEmpty()) {
      return elements;
    }

    String filter = currentFilter.toLowerCase().trim();
    return elements.stream()
        .filter(se -> {
          AttributeSyntaxDefinition syn = se.getElement();
          return (syn.getOID() != null && syn.getOID().toLowerCase().contains(filter))
              || (syn.getDescription() != null
              && syn.getDescription().toLowerCase().contains(filter))
              || se.getServerName().toLowerCase().contains(filter);
        })
        .collect(Collectors.toList());
  }

  private void showObjectClassDetails(SchemaElement<ObjectClassDefinition> se) {
    detailsPanel.removeAll();
    ObjectClassDefinition oc = se.getElement();

    VerticalLayout details = new VerticalLayout();
    details.setSizeFull();
    details.setSpacing(true);
    details.setPadding(true);

    // Header with Edit button
    HorizontalLayout headerLayout = new HorizontalLayout();
    headerLayout.setWidthFull();
    headerLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    H3 header = new H3("Object Class: " + oc.getNameOrOID());
    header.getStyle().set("margin-bottom", "0px");

    Button editButton = new Button("Edit", new Icon(VaadinIcon.EDIT));
    editButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
    editButton.addClickListener(e -> openEditObjectClassDialog(se));

    headerLayout.add(header, editButton);
    headerLayout.setFlexGrow(1, header);
    headerLayout.getStyle().set("margin-bottom", "16px");

    details.add(headerLayout);

    SchemaDetailDialogHelper.addDetailRow(details, "Server", se.getServerName());
    SchemaDetailDialogHelper.addDetailRow(details, "OID", oc.getOID());
    SchemaDetailDialogHelper.addDetailRow(details, "Names",
        oc.getNames() != null ? String.join(", ", Arrays.asList(oc.getNames())) : "");
    SchemaDetailDialogHelper.addDetailRow(details, "Description", oc.getDescription());

    // Schema file (extension)
    String ocSchemaFile = getSchemaFileFromExtensions(oc.getExtensions());
    SchemaDetailDialogHelper.addDetailRow(details, "Schema File", ocSchemaFile);

    SchemaDetailDialogHelper.addDetailRow(details, "Type",
        oc.getObjectClassType() != null ? oc.getObjectClassType().getName() : "");
    SchemaDetailDialogHelper.addDetailRow(details, "Obsolete", oc.isObsolete() ? "Yes" : "No");

    if (oc.getSuperiorClasses() != null && oc.getSuperiorClasses().length > 0) {
      SchemaDetailDialogHelper.addDetailRow(details, "Superior Classes",
          String.join(", ", oc.getSuperiorClasses()));
    }

    // Get schema for clickable attribute links
    Schema schema = getSchemaForElement(se);

    // Required Attributes - clickable if schema available
    if (oc.getRequiredAttributes() != null && oc.getRequiredAttributes().length > 0) {
      addClickableAttributeListToDetails(details, "Required Attributes",
          oc.getRequiredAttributes(), schema, se.getServerName());
    }

    // Optional Attributes - clickable if schema available
    if (oc.getOptionalAttributes() != null && oc.getOptionalAttributes().length > 0) {
      addClickableAttributeListToDetails(details, "Optional Attributes",
          oc.getOptionalAttributes(), schema, se.getServerName());
    }

    // Extensions (additional properties)
    if (oc.getExtensions() != null && !oc.getExtensions().isEmpty()) {
      StringBuilder extensions = new StringBuilder();
      for (Map.Entry<String, String[]> entry : oc.getExtensions().entrySet()) {
        if (extensions.length() > 0) {
          extensions.append(", ");
        }
        extensions.append(entry.getKey()).append("=").append(String.join(",", entry.getValue()));
      }
      SchemaDetailDialogHelper.addDetailRow(details, "Extensions", extensions.toString());
    }

    // Add raw schema definition at the bottom
    SchemaDetailDialogHelper.addRawDefinition(details, oc);

    detailsPanel.add(details);
  }

  private void showAttributeTypeDetails(SchemaElement<AttributeTypeDefinition> se) {
    detailsPanel.removeAll();
    AttributeTypeDefinition at = se.getElement();

    VerticalLayout details = new VerticalLayout();
    details.setSizeFull();
    details.setSpacing(true);
    details.setPadding(true);

    // Header with Edit button
    HorizontalLayout headerLayout = new HorizontalLayout();
    headerLayout.setWidthFull();
    headerLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    H3 header = new H3("Attribute Type: " + at.getNameOrOID());
    header.getStyle().set("margin-bottom", "0px");

    Button editButton = new Button("Edit", new Icon(VaadinIcon.EDIT));
    editButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
    editButton.addClickListener(e -> openEditAttributeTypeDialog(se));

    headerLayout.add(header, editButton);
    headerLayout.setFlexGrow(1, header);
    headerLayout.getStyle().set("margin-bottom", "16px");

    details.add(headerLayout);

    SchemaDetailDialogHelper.addDetailRow(details, "Server", se.getServerName());
    SchemaDetailDialogHelper.addDetailRow(details, "OID", at.getOID());
    SchemaDetailDialogHelper.addDetailRow(details, "Names",
        at.getNames() != null ? String.join(", ", Arrays.asList(at.getNames())) : "");
    SchemaDetailDialogHelper.addDetailRow(details, "Description", at.getDescription());

    // Schema file (extension)
    String atSchemaFile = getSchemaFileFromExtensions(at.getExtensions());
    SchemaDetailDialogHelper.addDetailRow(details, "Schema File", atSchemaFile);

    SchemaDetailDialogHelper.addDetailRow(details, "Syntax OID", at.getSyntaxOID());
    SchemaDetailDialogHelper.addDetailRow(details, "Obsolete", at.isObsolete() ? "Yes" : "No");
    SchemaDetailDialogHelper.addDetailRow(details, "Single Value", at.isSingleValued() ? "Yes" : "No");
    SchemaDetailDialogHelper.addDetailRow(details, "Collective", at.isCollective() ? "Yes" : "No");
    SchemaDetailDialogHelper.addDetailRow(details, "No User Modification",
        at.isNoUserModification() ? "Yes" : "No");

    // Usage
    if (at.getUsage() != null) {
      SchemaDetailDialogHelper.addDetailRow(details, "Usage", at.getUsage().getName());
    }

    if (at.getSuperiorType() != null) {
      SchemaDetailDialogHelper.addDetailRow(details, "Superior Type", at.getSuperiorType());
    }

    if (at.getEqualityMatchingRule() != null) {
      SchemaDetailDialogHelper.addDetailRow(details, "Equality Matching Rule", at.getEqualityMatchingRule());
    }

    // Ordering matching rule
    if (at.getOrderingMatchingRule() != null) {
      SchemaDetailDialogHelper.addDetailRow(details, "Ordering Matching Rule", at.getOrderingMatchingRule());
    }

    // Substring matching rule
    if (at.getSubstringMatchingRule() != null) {
      SchemaDetailDialogHelper.addDetailRow(details, "Substring Matching Rule", at.getSubstringMatchingRule());
    }

    // Extensions (additional properties)
    if (at.getExtensions() != null && !at.getExtensions().isEmpty()) {
      StringBuilder extensions = new StringBuilder();
      for (Map.Entry<String, String[]> entry : at.getExtensions().entrySet()) {
        if (extensions.length() > 0) {
          extensions.append(", ");
        }
        extensions.append(entry.getKey()).append("=").append(String.join(",", entry.getValue()));
      }
      SchemaDetailDialogHelper.addDetailRow(details, "Extensions", extensions.toString());
    }

    // Add "Used as May" and "Used as Must" sections
    addAttributeUsageSection(details, at, se);

    // Add raw schema definition at the bottom
    SchemaDetailDialogHelper.addRawDefinition(details, at);

    detailsPanel.add(details);
  }

  private void showMatchingRuleDetails(SchemaElement<MatchingRuleDefinition> se) {
    detailsPanel.removeAll();
    MatchingRuleDefinition mr = se.getElement();

    VerticalLayout details = new VerticalLayout();
    details.setSizeFull();
    details.setSpacing(true);
    details.setPadding(true);

    H3 header = new H3("Matching Rule: " + mr.getNameOrOID());
    header.getStyle().set("margin-bottom", "16px");
    details.add(header);

    SchemaDetailDialogHelper.addDetailRow(details, "Server", se.getServerName());
    SchemaDetailDialogHelper.addDetailRow(details, "OID", mr.getOID());
    SchemaDetailDialogHelper.addDetailRow(details, "Names",
        mr.getNames() != null ? String.join(", ", Arrays.asList(mr.getNames())) : "");
    SchemaDetailDialogHelper.addDetailRow(details, "Description", mr.getDescription());
    SchemaDetailDialogHelper.addDetailRow(details, "Syntax OID", mr.getSyntaxOID());
    SchemaDetailDialogHelper.addDetailRow(details, "Obsolete", mr.isObsolete() ? "Yes" : "No");

    // Add raw schema definition at the bottom
    SchemaDetailDialogHelper.addRawDefinition(details, mr);

    detailsPanel.add(details);
  }

  private void showMatchingRuleUseDetails(SchemaElement<MatchingRuleUseDefinition> se) {
    detailsPanel.removeAll();
    MatchingRuleUseDefinition mru = se.getElement();

    VerticalLayout details = new VerticalLayout();
    details.setSizeFull();
    details.setSpacing(true);
    details.setPadding(true);

    H3 header = new H3("Matching Rule Use: " + mru.getOID());
    header.getStyle().set("margin-bottom", "16px");
    details.add(header);

    SchemaDetailDialogHelper.addDetailRow(details, "Server", se.getServerName());
    SchemaDetailDialogHelper.addDetailRow(details, "OID", mru.getOID());
    SchemaDetailDialogHelper.addDetailRow(details, "Names",
        mru.getNames() != null ? String.join(", ", Arrays.asList(mru.getNames())) : "");
    SchemaDetailDialogHelper.addDetailRow(details, "Description", mru.getDescription());
    SchemaDetailDialogHelper.addDetailRow(details, "Obsolete", mru.isObsolete() ? "Yes" : "No");

    if (mru.getApplicableAttributeTypes() != null
        && mru.getApplicableAttributeTypes().length > 0) {
      SchemaDetailDialogHelper.addDetailRow(details, "Applicable Attribute Types",
          String.join(", ", mru.getApplicableAttributeTypes()));
    }

    // Extensions
    if (mru.getExtensions() != null && !mru.getExtensions().isEmpty()) {
      StringBuilder extensionsText = new StringBuilder();
      for (Map.Entry<String, String[]> entry : mru.getExtensions().entrySet()) {
        extensionsText.append(entry.getKey()).append(": ")
            .append(String.join(", ", entry.getValue())).append("\n");
      }
      SchemaDetailDialogHelper.addDetailRow(details, "Extensions", extensionsText.toString());
    }

    // Add raw schema definition at the bottom
    SchemaDetailDialogHelper.addRawDefinition(details, mru);

    detailsPanel.add(details);
  }

  private void showSyntaxDetails(SchemaElement<AttributeSyntaxDefinition> se) {
    detailsPanel.removeAll();
    AttributeSyntaxDefinition syn = se.getElement();

    VerticalLayout details = new VerticalLayout();
    details.setSizeFull();
    details.setSpacing(true);
    details.setPadding(true);

    H3 header = new H3("Syntax: " + syn.getOID());
    header.getStyle().set("margin-bottom", "16px");
    details.add(header);

    SchemaDetailDialogHelper.addDetailRow(details, "Server", se.getServerName());
    SchemaDetailDialogHelper.addDetailRow(details, "OID", syn.getOID());
    SchemaDetailDialogHelper.addDetailRow(details, "Description", syn.getDescription());

    // Extensions (if any)
    if (syn.getExtensions() != null && !syn.getExtensions().isEmpty()) {
      StringBuilder extensionsText = new StringBuilder();
      for (Map.Entry<String, String[]> entry : syn.getExtensions().entrySet()) {
        extensionsText.append(entry.getKey()).append(": ")
            .append(String.join(", ", entry.getValue())).append("\n");
      }
      SchemaDetailDialogHelper.addDetailRow(details, "Extensions", extensionsText.toString());
    }

    // Add raw schema definition at the bottom
    SchemaDetailDialogHelper.addRawDefinition(details, syn);

    detailsPanel.add(details);
  }

  /**
   * Opens edit dialog for Object Class.
   *
   * @param se schema element
   */
  private void openEditObjectClassDialog(SchemaElement<ObjectClassDefinition> se) {
    ObjectClassDefinition oc = se.getElement();
    LdapServerConfig config = se.getServerConfig();

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Edit Object Class: " + oc.getNameOrOID());
    dialog.setWidth("600px");

    VerticalLayout form = new VerticalLayout();
    form.setPadding(false);
    form.setSpacing(true);

    // Check if server supports schema modification
    boolean canModifyTemp = false;
    try {
      canModifyTemp = ldapService.supportsSchemaModification(config);
    } catch (Exception e) {
      logger.warn("Error checking schema modification support", e);
    }
    final boolean canModify = canModifyTemp;

    if (!canModify) {
      Span warningMessage = new Span(
          "This server does not support schema modification. "
          + "Showing read-only view."
      );
      warningMessage.getStyle()
          .set("color", "var(--lumo-error-text-color)")
          .set("font-size", "var(--lumo-font-size-s)")
          .set("margin-bottom", "16px");
      form.add(warningMessage);
    }

    TextField oidField = new TextField("OID");
    oidField.setValue(oc.getOID() != null ? oc.getOID() : "");
    oidField.setWidthFull();
    oidField.setReadOnly(true); // OID cannot be changed

    TextField namesField = new TextField("Names (comma-separated)");
    namesField.setValue(oc.getNames() != null
        ? String.join(", ", Arrays.asList(oc.getNames())) : "");
    namesField.setWidthFull();
    namesField.setReadOnly(!canModify);

    TextArea descField = new TextArea("Description");
    descField.setValue(oc.getDescription() != null ? oc.getDescription() : "");
    descField.setWidthFull();
    descField.setReadOnly(!canModify);

    ComboBox<String> typeField = new ComboBox<>("Type");
    typeField.setItems("STRUCTURAL", "AUXILIARY", "ABSTRACT");
    typeField.setValue(oc.getObjectClassType() != null
        ? oc.getObjectClassType().getName() : "STRUCTURAL");
    typeField.setWidthFull();
    typeField.setReadOnly(!canModify);

    // Get available schema elements for selectors
    List<String> availableObjectClasses = getAvailableObjectClassNames();
    List<String> availableAttributes = getAvailableAttributeTypeNames();

    // Superior classes with multi-select
    MultiSelectComboBox<String> superiorField = createSchemaMultiSelect(
        "Superior Classes",
        "Choose from existing object classes...",
        availableObjectClasses);
    if (oc.getSuperiorClasses() != null && oc.getSuperiorClasses().length > 0) {
      superiorField.setValue(new HashSet<>(Arrays.asList(oc.getSuperiorClasses())));
    }
    superiorField.setWidthFull();
    superiorField.setReadOnly(!canModify);

    // Required attributes with multi-select
    MultiSelectComboBox<String> requiredField = createSchemaMultiSelect(
        "Required Attributes (MUST)",
        "Choose from existing attributes...",
        availableAttributes);
    if (oc.getRequiredAttributes() != null && oc.getRequiredAttributes().length > 0) {
      requiredField.setValue(new HashSet<>(Arrays.asList(oc.getRequiredAttributes())));
    }
    requiredField.setWidthFull();
    requiredField.setReadOnly(!canModify);

    // Optional attributes with multi-select
    MultiSelectComboBox<String> optionalField = createSchemaMultiSelect(
        "Optional Attributes (MAY)",
        "Choose from existing attributes...",
        availableAttributes);
    if (oc.getOptionalAttributes() != null && oc.getOptionalAttributes().length > 0) {
      optionalField.setValue(new HashSet<>(Arrays.asList(oc.getOptionalAttributes())));
    }
    optionalField.setWidthFull();
    optionalField.setReadOnly(!canModify);

    TextField schemaFileField = new TextField("Schema File");
    schemaFileField.setValue(getSchemaFileFromExtensions(oc.getExtensions()) != null
        ? getSchemaFileFromExtensions(oc.getExtensions()) : "");
    schemaFileField.setWidthFull();
    schemaFileField.setReadOnly(true);

    // Raw schema definition field with two-way binding and paste button
    VerticalLayout rawSchemaContainer = new VerticalLayout();
    rawSchemaContainer.setPadding(false);
    rawSchemaContainer.setSpacing(false);
    
    HorizontalLayout rawSchemaHeader = new HorizontalLayout();
    rawSchemaHeader.setWidthFull();
    rawSchemaHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    rawSchemaHeader.getStyle().set("margin-bottom", "4px");
    
    Span rawSchemaLabel = new Span("Raw Schema Definition");
    rawSchemaLabel.getStyle()
        .set("font-weight", "500")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");
    
    Button pasteButton = new Button(new Icon(VaadinIcon.PASTE));
    pasteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    pasteButton.setTooltipText("Paste from clipboard");
    pasteButton.setEnabled(canModify);
    
    rawSchemaHeader.add(rawSchemaLabel, pasteButton);
    rawSchemaHeader.setFlexGrow(1, rawSchemaLabel);
    
    TextArea rawSchemaField = new TextArea();
    rawSchemaField.setWidthFull();
    rawSchemaField.setHeight("200px");
    rawSchemaField.getStyle().set("font-family", "monospace");
    rawSchemaField.setReadOnly(!canModify);
    
    // Add paste functionality
    pasteButton.addClickListener(e -> {
      rawSchemaField.getElement().executeJs(
          "navigator.clipboard.readText().then(text => {" +
          "  this.value = text;" +
          "  this.dispatchEvent(new Event('change', { bubbles: true }));" +
          "});",
          rawSchemaField.getElement()
      );
    });
    
    rawSchemaContainer.add(rawSchemaHeader, rawSchemaField);
    
    // Initialize raw schema field with current definition
    Set<String> initialSuperiorSet = superiorField.getValue();
    String initialSuperiorStr = initialSuperiorSet != null && !initialSuperiorSet.isEmpty()
        ? String.join(", ", initialSuperiorSet) : "";
    Set<String> initialRequiredSet = requiredField.getValue();
    String initialRequiredStr = initialRequiredSet != null && !initialRequiredSet.isEmpty()
        ? String.join(", ", initialRequiredSet) : "";
    Set<String> initialOptionalSet = optionalField.getValue();
    String initialOptionalStr = initialOptionalSet != null && !initialOptionalSet.isEmpty()
        ? String.join(", ", initialOptionalSet) : "";
    
    rawSchemaField.setValue(buildObjectClassDefinition(
        oidField.getValue(),
        namesField.getValue(),
        descField.getValue(),
        typeField.getValue(),
        initialSuperiorStr,
        initialRequiredStr,
        initialOptionalStr,
        filterWritableExtensions(oc.getExtensions())
    ));

    // Track if we're updating from raw schema to prevent circular updates
    final boolean[] updatingFromRaw = {false};

    // Lambda to update raw schema when UI fields change
    Runnable updateRawSchemaFromUI = () -> {
      if (!rawSchemaField.isReadOnly() && !updatingFromRaw[0]) {
        Set<String> superior = superiorField.getValue();
        String superiorValues = superior != null && !superior.isEmpty()
            ? String.join(", ", superior) : "";
        Set<String> required = requiredField.getValue();
        String requiredValues = required != null && !required.isEmpty()
            ? String.join(", ", required) : "";
        Set<String> optional = optionalField.getValue();
        String optionalValues = optional != null && !optional.isEmpty()
            ? String.join(", ", optional) : "";
        
        String newRaw = buildObjectClassDefinition(
            oidField.getValue(),
            namesField.getValue(),
            descField.getValue(),
            typeField.getValue(),
            superiorValues,
            requiredValues,
            optionalValues,
            filterWritableExtensions(oc.getExtensions())
        );
        
        // Only update if different to prevent infinite loop
        if (!rawSchemaField.getValue().equals(newRaw)) {
          rawSchemaField.setValue(newRaw);
        }
      }
    };

    namesField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    descField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    typeField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    superiorField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    requiredField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    optionalField.addValueChangeListener(e -> updateRawSchemaFromUI.run());

    // Add listener to parse raw schema and update UI fields
    rawSchemaField.addValueChangeListener(event -> {
      if (canModify && event.isFromClient()) {
        try {
          updatingFromRaw[0] = true;
          String rawValue = event.getValue();
          if (rawValue != null && !rawValue.trim().isEmpty()) {
            parseAndUpdateObjectClassFields(rawValue, namesField, descField,
                typeField, superiorField, requiredField, optionalField);
          }
        } catch (Exception ex) {
          logger.debug("Error parsing raw schema: {}", ex.getMessage());
          // Don't show error - allow invalid intermediate states while typing
        } finally {
          updatingFromRaw[0] = false;
        }
      }
    });

    form.add(oidField, namesField, descField, typeField, superiorField,
        requiredField, optionalField, schemaFileField, rawSchemaContainer);

    Button cancelButton = new Button("Cancel", e -> dialog.close());
    
    if (canModify) {
      Button saveButton = new Button("Save", e -> {
        try {
          // Build new definition string
          String oldDefinition = getRawDefinitionString(oc);
          
          // Convert Set values to comma-separated strings
          Set<String> superiorSet = superiorField.getValue();
          String superiorStr = superiorSet != null && !superiorSet.isEmpty()
              ? String.join(", ", superiorSet) : "";
          
          Set<String> requiredSet = requiredField.getValue();
          String requiredStr = requiredSet != null && !requiredSet.isEmpty()
              ? String.join(", ", requiredSet) : "";
          
          Set<String> optionalSet = optionalField.getValue();
          String optionalStr = optionalSet != null && !optionalSet.isEmpty()
              ? String.join(", ", optionalSet) : "";
          
          String newDefinition = buildObjectClassDefinition(
              oidField.getValue(),
              namesField.getValue(),
              descField.getValue(),
              typeField.getValue(),
              superiorStr,
              requiredStr,
              optionalStr,
              filterWritableExtensions(oc.getExtensions())
          );
          
          // Only modify if definition changed
          if (!normalizeDefinition(oldDefinition).equals(normalizeDefinition(newDefinition))) {
            ldapService.modifyObjectClassInSchema(config, oldDefinition, newDefinition);
            
            // Refresh the schema cache to pick up the modification
            try {
              ldapService.refreshSchema(config);
            } catch (Exception rex) {
              logger.warn("Failed to refresh schema cache after modification for {}: {}", 
                  config.getName(), rex.getMessage());
            }
            
            NotificationHelper.showSuccess("Object class updated successfully");
            dialog.close();
            // Reload schema
            refreshSchema();
          } else {
            NotificationHelper.showInfo("No changes detected");
            dialog.close();
          }
        } catch (Exception ex) {
          logger.error("Error saving object class", ex);
          NotificationHelper.showError("Error: " + ex.getMessage());
        }
      });
      saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
      dialog.getFooter().add(cancelButton, saveButton);
    } else {
      cancelButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
      dialog.getFooter().add(cancelButton);
    }

    dialog.add(form);
    dialog.open();
  }

  /**
   * Opens edit dialog for Attribute Type.
   *
   * @param se schema element
   */
  private void openEditAttributeTypeDialog(SchemaElement<AttributeTypeDefinition> se) {
    AttributeTypeDefinition at = se.getElement();
    LdapServerConfig config = se.getServerConfig();

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Edit Attribute Type: " + at.getNameOrOID());
    dialog.setWidth("600px");

    VerticalLayout form = new VerticalLayout();
    form.setPadding(false);
    form.setSpacing(true);

    // Check if server supports schema modification
    boolean canModifyTemp = false;
    try {
      canModifyTemp = ldapService.supportsSchemaModification(config);
    } catch (Exception e) {
      logger.warn("Error checking schema modification support", e);
    }
    final boolean canModify = canModifyTemp;

    if (!canModify) {
      Span warningMessage = new Span(
          "This server does not support schema modification. "
          + "Showing read-only view."
      );
      warningMessage.getStyle()
          .set("color", "var(--lumo-error-text-color)")
          .set("font-size", "var(--lumo-font-size-s)")
          .set("margin-bottom", "16px");
      form.add(warningMessage);
    }

    TextField oidField = new TextField("OID");
    oidField.setValue(at.getOID() != null ? at.getOID() : "");
    oidField.setWidthFull();
    oidField.setReadOnly(true); // OID cannot be changed

    TextField namesField = new TextField("Names (comma-separated)");
    namesField.setValue(at.getNames() != null
        ? String.join(", ", Arrays.asList(at.getNames())) : "");
    namesField.setWidthFull();
    namesField.setReadOnly(!canModify);

    TextArea descField = new TextArea("Description");
    descField.setValue(at.getDescription() != null ? at.getDescription() : "");
    descField.setWidthFull();
    descField.setReadOnly(!canModify);

    TextField syntaxField = new TextField("Syntax OID");
    syntaxField.setValue(at.getSyntaxOID() != null ? at.getSyntaxOID() : "");
    syntaxField.setWidthFull();
    syntaxField.setReadOnly(!canModify);

    TextField superiorField = new TextField("Superior Type");
    superiorField.setValue(at.getSuperiorType() != null ? at.getSuperiorType() : "");
    superiorField.setWidthFull();
    superiorField.setReadOnly(!canModify);

    TextField equalityField = new TextField("Equality Matching Rule");
    equalityField.setValue(at.getEqualityMatchingRule() != null
        ? at.getEqualityMatchingRule() : "");
    equalityField.setWidthFull();
    equalityField.setReadOnly(!canModify);

    TextField orderingField = new TextField("Ordering Matching Rule");
    orderingField.setValue(at.getOrderingMatchingRule() != null
        ? at.getOrderingMatchingRule() : "");
    orderingField.setWidthFull();
    orderingField.setReadOnly(!canModify);

    TextField substringField = new TextField("Substring Matching Rule");
    substringField.setValue(at.getSubstringMatchingRule() != null
        ? at.getSubstringMatchingRule() : "");
    substringField.setWidthFull();
    substringField.setReadOnly(!canModify);

    TextField usageField = new TextField("Usage");
    usageField.setValue(at.getUsage() != null ? at.getUsage().getName() : "");
    usageField.setWidthFull();
    usageField.setReadOnly(!canModify);

    TextField schemaFileField = new TextField("Schema File");
    schemaFileField.setValue(getSchemaFileFromExtensions(at.getExtensions()) != null
        ? getSchemaFileFromExtensions(at.getExtensions()) : "");
    schemaFileField.setWidthFull();
    schemaFileField.setReadOnly(true);

    // Raw schema definition field with two-way binding and paste button
    VerticalLayout rawSchemaContainer = new VerticalLayout();
    rawSchemaContainer.setPadding(false);
    rawSchemaContainer.setSpacing(false);
    
    HorizontalLayout rawSchemaHeader = new HorizontalLayout();
    rawSchemaHeader.setWidthFull();
    rawSchemaHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    rawSchemaHeader.getStyle().set("margin-bottom", "4px");
    
    Span rawSchemaLabel = new Span("Raw Schema Definition");
    rawSchemaLabel.getStyle()
        .set("font-weight", "500")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");
    
    Button pasteButton = new Button(new Icon(VaadinIcon.PASTE));
    pasteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    pasteButton.setTooltipText("Paste from clipboard");
    pasteButton.setEnabled(canModify);
    
    rawSchemaHeader.add(rawSchemaLabel, pasteButton);
    rawSchemaHeader.setFlexGrow(1, rawSchemaLabel);
    
    TextArea rawSchemaField = new TextArea();
    rawSchemaField.setWidthFull();
    rawSchemaField.setHeight("200px");
    rawSchemaField.getStyle().set("font-family", "monospace");
    rawSchemaField.setReadOnly(!canModify);
    
    // Add paste functionality
    pasteButton.addClickListener(e -> {
      rawSchemaField.getElement().executeJs(
          "navigator.clipboard.readText().then(text => {" +
          "  this.value = text;" +
          "  this.dispatchEvent(new Event('change', { bubbles: true }));" +
          "});",
          rawSchemaField.getElement()
      );
    });
    
    rawSchemaContainer.add(rawSchemaHeader, rawSchemaField);
    
    // Initialize raw schema field with current definition
    rawSchemaField.setValue(buildAttributeTypeDefinition(
        oidField.getValue(),
        namesField.getValue(),
        descField.getValue(),
        syntaxField.getValue(),
        superiorField.getValue(),
        equalityField.getValue(),
        orderingField.getValue(),
        substringField.getValue(),
        usageField.getValue(),
        filterWritableExtensions(at.getExtensions())
    ));

    // Track if we're updating from raw schema to prevent circular updates
    final boolean[] updatingFromRaw = {false};

    // Lambda to update raw schema when UI fields change
    Runnable updateRawSchemaFromUI = () -> {
      if (!rawSchemaField.isReadOnly() && !updatingFromRaw[0]) {
        String newRaw = buildAttributeTypeDefinition(
            oidField.getValue(),
            namesField.getValue(),
            descField.getValue(),
            syntaxField.getValue(),
            superiorField.getValue(),
            equalityField.getValue(),
            orderingField.getValue(),
            substringField.getValue(),
            usageField.getValue(),
            filterWritableExtensions(at.getExtensions())
        );
        
        // Only update if different to prevent infinite loop
        if (!rawSchemaField.getValue().equals(newRaw)) {
          rawSchemaField.setValue(newRaw);
        }
      }
    };

    namesField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    descField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    syntaxField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    superiorField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    equalityField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    orderingField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    substringField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    usageField.addValueChangeListener(e -> updateRawSchemaFromUI.run());

    // Add listener to parse raw schema and update UI fields
    rawSchemaField.addValueChangeListener(event -> {
      if (canModify && event.isFromClient()) {
        try {
          updatingFromRaw[0] = true;
          String rawValue = event.getValue();
          if (rawValue != null && !rawValue.trim().isEmpty()) {
            parseAndUpdateAttributeTypeFields(rawValue, namesField, descField,
                syntaxField, superiorField, equalityField, orderingField,
                substringField, usageField);
          }
        } catch (Exception ex) {
          logger.debug("Error parsing raw schema: {}", ex.getMessage());
          // Don't show error - allow invalid intermediate states while typing
        } finally {
          updatingFromRaw[0] = false;
        }
      }
    });

    form.add(oidField, namesField, descField, syntaxField, superiorField,
        equalityField, orderingField, substringField, usageField, schemaFileField,
        rawSchemaContainer);

    Button cancelButton = new Button("Cancel", e -> dialog.close());
    
    if (canModify) {
      Button saveButton = new Button("Save", e -> {
        try {
          // Build new definition string
          String oldDefinition = getRawDefinitionString(at);
          String newDefinition = buildAttributeTypeDefinition(
              oidField.getValue(),
              namesField.getValue(),
              descField.getValue(),
              syntaxField.getValue(),
              superiorField.getValue(),
              equalityField.getValue(),
              orderingField.getValue(),
              substringField.getValue(),
              usageField.getValue(),
              filterWritableExtensions(at.getExtensions())
          );
          
          // Only modify if definition changed
          if (!normalizeDefinition(oldDefinition).equals(normalizeDefinition(newDefinition))) {
            ldapService.modifyAttributeTypeInSchema(config, oldDefinition, newDefinition);
            
            // Refresh the schema cache to pick up the modification
            try {
              ldapService.refreshSchema(config);
            } catch (Exception rex) {
              logger.warn("Failed to refresh schema cache after modification for {}: {}", 
                  config.getName(), rex.getMessage());
            }
            
            NotificationHelper.showSuccess("Attribute type updated successfully");
            dialog.close();
            // Reload schema
            refreshSchema();
          } else {
            NotificationHelper.showInfo("No changes detected");
            dialog.close();
          }
        } catch (Exception ex) {
          logger.error("Error saving attribute type", ex);
          NotificationHelper.showError("Error: " + ex.getMessage());
        }
      });
      saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
      dialog.getFooter().add(cancelButton, saveButton);
    } else {
      cancelButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
      dialog.getFooter().add(cancelButton);
    }

    dialog.add(form);
    dialog.open();
  }

  /**
   * Helper to read the X-Schema-file extension (supports common casings).
   *
   * @param extensions extension map
   * @return schema file name or null
   */
  private String getSchemaFileFromExtensions(Map<String, String[]> extensions) {
    if (extensions == null || extensions.isEmpty()) {
      return null;
    }

    String[] keys = new String[] {
        "X-SCHEMA-FILE", "X-Schema-File", "X-Schema-file", "x-schema-file"
    };

    for (String k : keys) {
      String[] vals = extensions.get(k);
      if (vals != null && vals.length > 0) {
        return String.join(", ", vals);
      }
    }

    return null;
  }



  /**
   * Gets the raw definition string from a schema element.
   * Used when modifying schema elements to get the original definition.
   *
   * @param schemaElement schema element
   * @return raw definition string
   */
  private String getRawDefinitionString(Object schemaElement) {
    // Try common methods for getting the raw definition
    String[] candidateMethods = new String[] {
        "toString", "getDefinitionString", "getDefinitionOrString",
        "getOriginalString"
    };

    for (String methodName : candidateMethods) {
      try {
        java.lang.reflect.Method m = schemaElement.getClass().getMethod(methodName);
        Object res = m.invoke(schemaElement);
        if (res instanceof String) {
          return (String) res;
        }
      } catch (NoSuchMethodException ns) {
        // ignore, try next
      } catch (Exception ignored) {
        // ignore any invocation issues and try next
      }
    }

    // Fallback to toString() which should at least provide a usable representation
    try {
      return schemaElement.toString();
    } catch (Exception e) {
      return "";
    }
  }

  /**
   * Filters out read-only extensions that should not be included when
   * modifying or adding schema elements.
   *
   * @param extensions original extension map
   * @return filtered extension map without read-only extensions
   */
  private Map<String, String[]> filterWritableExtensions(Map<String, String[]> extensions) {
    if (extensions == null || extensions.isEmpty()) {
      return extensions;
    }
    
    Map<String, String[]> filtered = new HashMap<>();
    for (Map.Entry<String, String[]> entry : extensions.entrySet()) {
      String key = entry.getKey();
      // Skip read-only extensions that are set by the LDAP server
      if ("X-READ-ONLY".equals(key)) {
        continue;
      }
      filtered.put(key, entry.getValue());
    }
    return filtered;
  }



  /**
   * Builds an object class definition string from the provided fields.
   *
   * @param oid object class OID
   * @param names comma-separated names
   * @param description description
   * @param type object class type
   * @param superiors comma-separated superior classes
   * @param required comma-separated required attributes
   * @param optional comma-separated optional attributes
   * @param extensions extension map
   * @return RFC-compliant object class definition
   */
  private String buildObjectClassDefinition(String oid, String names, String description,
      String type, String superiors, String required, String optional,
      Map<String, String[]> extensions) {
    StringBuilder sb = new StringBuilder();
    sb.append("( ").append(oid != null ? oid.trim() : "");

    // Add names
    if (names != null && !names.trim().isEmpty()) {
      String[] nameArray = names.split(",");
      if (nameArray.length == 1) {
        sb.append(" NAME '").append(nameArray[0].trim()).append("'");
      } else {
        sb.append(" NAME ( ");
        for (int i = 0; i < nameArray.length; i++) {
          if (i > 0) {
            sb.append(" ");
          }
          sb.append("'").append(nameArray[i].trim()).append("'");
        }
        sb.append(" )");
      }
    }

    // Add description
    if (description != null && !description.trim().isEmpty()) {
      sb.append(" DESC '").append(description.trim()).append("'");
    }

    // Add superior classes
    if (superiors != null && !superiors.trim().isEmpty()) {
      String[] supArray = superiors.split(",");
      if (supArray.length == 1) {
        sb.append(" SUP ").append(supArray[0].trim());
      } else {
        sb.append(" SUP ( ");
        for (int i = 0; i < supArray.length; i++) {
          if (i > 0) {
            sb.append(" $ ");
          }
          sb.append(supArray[i].trim());
        }
        sb.append(" )");
      }
    }

    // Add object class type (default to STRUCTURAL if not specified)
    if (type != null && !type.trim().isEmpty()) {
      String typeUpper = type.trim().toUpperCase();
      if ("ABSTRACT".equals(typeUpper) || "AUXILIARY".equals(typeUpper)) {
        sb.append(" ").append(typeUpper);
      }
      // STRUCTURAL is default, no need to specify
    }

    // Add required attributes
    if (required != null && !required.trim().isEmpty()) {
      String[] reqArray = required.split(",");
      sb.append(" MUST ");
      if (reqArray.length == 1) {
        sb.append(reqArray[0].trim());
      } else {
        sb.append("( ");
        for (int i = 0; i < reqArray.length; i++) {
          if (i > 0) {
            sb.append(" $ ");
          }
          sb.append(reqArray[i].trim());
        }
        sb.append(" )");
      }
    }

    // Add optional attributes
    if (optional != null && !optional.trim().isEmpty()) {
      String[] optArray = optional.split(",");
      sb.append(" MAY ");
      if (optArray.length == 1) {
        sb.append(optArray[0].trim());
      } else {
        sb.append("( ");
        for (int i = 0; i < optArray.length; i++) {
          if (i > 0) {
            sb.append(" $ ");
          }
          sb.append(optArray[i].trim());
        }
        sb.append(" )");
      }
    }

    // Add extensions if any
    if (extensions != null && !extensions.isEmpty()) {
      for (Map.Entry<String, String[]> entry : extensions.entrySet()) {
        String key = entry.getKey();
        String[] values = entry.getValue();
        if (values != null && values.length > 0) {
          sb.append(" ").append(key);
          if (values.length == 1) {
            sb.append(" '").append(values[0]).append("'");
          } else {
            sb.append(" ( ");
            for (int i = 0; i < values.length; i++) {
              if (i > 0) {
                sb.append(" ");
              }
              sb.append("'").append(values[i]).append("'");
            }
            sb.append(" )");
          }
        }
      }
    }

    sb.append(" )");
    return sb.toString();
  }

  /**
   * Normalizes a definition string for comparison by removing extra whitespace.
   *
   * @param definition definition string
   * @return normalized definition
   */
  private String normalizeDefinition(String definition) {
    if (definition == null) {
      return "";
    }
    return definition.trim().replaceAll("\\s+", " ");
  }

  /**
   * Builds an attribute type definition string from the provided fields.
   *
   * @param oid attribute type OID
   * @param names comma-separated names
   * @param description description
   * @param syntax syntax OID
   * @param superior superior type
   * @param equality equality matching rule
   * @param ordering ordering matching rule
   * @param substring substring matching rule
   * @param usage usage
   * @param extensions extension map
   * @return RFC-compliant attribute type definition
   */
  private String buildAttributeTypeDefinition(String oid, String names, String description,
      String syntax, String superior, String equality, String ordering, String substring,
      String usage, Map<String, String[]> extensions) {
    StringBuilder sb = new StringBuilder();
    sb.append("( ").append(oid != null ? oid.trim() : "");

    // Add names
    if (names != null && !names.trim().isEmpty()) {
      String[] nameArray = names.split(",");
      if (nameArray.length == 1) {
        sb.append(" NAME '").append(nameArray[0].trim()).append("'");
      } else {
        sb.append(" NAME ( ");
        for (int i = 0; i < nameArray.length; i++) {
          if (i > 0) {
            sb.append(" ");
          }
          sb.append("'").append(nameArray[i].trim()).append("'");
        }
        sb.append(" )");
      }
    }

    // Add description
    if (description != null && !description.trim().isEmpty()) {
      sb.append(" DESC '").append(description.trim()).append("'");
    }

    // Add superior type
    if (superior != null && !superior.trim().isEmpty()) {
      sb.append(" SUP ").append(superior.trim());
    }

    // Add equality matching rule
    if (equality != null && !equality.trim().isEmpty()) {
      sb.append(" EQUALITY ").append(equality.trim());
    }

    // Add ordering matching rule
    if (ordering != null && !ordering.trim().isEmpty()) {
      sb.append(" ORDERING ").append(ordering.trim());
    }

    // Add substring matching rule
    if (substring != null && !substring.trim().isEmpty()) {
      sb.append(" SUBSTR ").append(substring.trim());
    }

    // Add syntax
    if (syntax != null && !syntax.trim().isEmpty()) {
      sb.append(" SYNTAX ").append(syntax.trim());
    }

    // Add usage if not default (userApplications)
    if (usage != null && !usage.trim().isEmpty()) {
      String usageUpper = usage.trim().toUpperCase();
      if (!"USERAPPLICATIONS".equals(usageUpper)) {
        sb.append(" USAGE ").append(usageUpper);
      }
    }

    // Add extensions if any
    if (extensions != null && !extensions.isEmpty()) {
      for (Map.Entry<String, String[]> entry : extensions.entrySet()) {
        String key = entry.getKey();
        String[] values = entry.getValue();
        if (values != null && values.length > 0) {
          sb.append(" ").append(key);
          if (values.length == 1) {
            sb.append(" '").append(values[0]).append("'");
          } else {
            sb.append(" ( ");
            for (int i = 0; i < values.length; i++) {
              if (i > 0) {
                sb.append(" ");
              }
              sb.append("'").append(values[i]).append("'");
            }
            sb.append(" )");
          }
        }
      }
    }

    sb.append(" )");
    return sb.toString();
  }

  /**
   * Refreshes the schema display.
   */
  private void refreshSchema() {
    switch (currentView) {
      case "objectClasses":
        showObjectClasses();
        break;
      case "attributeTypes":
        showAttributeTypes();
        break;
      case "matchingRules":
        showMatchingRules();
        break;
      case "matchingRuleUses":
        showMatchingRuleUse();
        break;
      case "syntaxes":
        showSyntaxes();
        break;
      default:
        // Do nothing
    }
  }

  /**
   * Update visibility of add buttons based on current view and server capabilities.
   */
  private void updateAddButtons() {
    Set<String> selectedServers = MainLayout.getSelectedServers();
    boolean hasServers = selectedServers != null && !selectedServers.isEmpty();
    boolean canAddSchema = hasServers && canModifySchema();

    if ("objectClasses".equals(currentView)) {
      addObjectClassButton.setVisible(true);
      addAttributeTypeButton.setVisible(false);
    } else if ("attributeTypes".equals(currentView)) {
      addObjectClassButton.setVisible(false);
      addAttributeTypeButton.setVisible(true);
    } else {
      addObjectClassButton.setVisible(false);
      addAttributeTypeButton.setVisible(false);
    }

    addObjectClassButton.setEnabled(canAddSchema);
    addAttributeTypeButton.setEnabled(canAddSchema);
  }

  /**
   * Check if the current servers support schema modifications.
   */
  private boolean canModifySchema() {
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers == null || selectedServers.isEmpty()) {
      return false;
    }

    for (String serverName : selectedServers) {
      try {
        LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
        if (config != null && ldapService.supportsSchemaModification(config)) {
          return true;
        }
      } catch (Exception e) {
        logger.debug("Error checking schema modification support: {}", e.getMessage());
      }
    }
    return false;
  }

  /**
   * Get available object class names from all selected servers.
   */
  private List<String> getAvailableObjectClassNames() {
    List<String> objectClassNames = new ArrayList<>();
    Set<String> selectedServers = MainLayout.getSelectedServers();

    if (selectedServers != null) {
      for (String serverName : selectedServers) {
        try {
          LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
          if (config != null) {
            Schema schema = ldapService.getSchema(config);
            if (schema != null) {
              Collection<ObjectClassDefinition> objectClasses = schema.getObjectClasses();
              for (ObjectClassDefinition oc : objectClasses) {
                String name = oc.getNameOrOID();
                if (name != null && !name.trim().isEmpty()) {
                  objectClassNames.add(name);
                }
              }
            }
          }
        } catch (Exception e) {
          logger.debug("Error getting object classes from {}: {}", serverName, e.getMessage());
        }
      }
    }

    return objectClassNames.stream().distinct()
        .sorted(String.CASE_INSENSITIVE_ORDER)
        .collect(Collectors.toList());
  }

  /**
   * Get available attribute type names from all selected servers.
   */
  private List<String> getAvailableAttributeTypeNames() {
    List<String> attributeNames = new ArrayList<>();
    Set<String> selectedServers = MainLayout.getSelectedServers();

    if (selectedServers != null) {
      for (String serverName : selectedServers) {
        try {
          LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
          if (config != null) {
            Schema schema = ldapService.getSchema(config);
            if (schema != null) {
              Collection<AttributeTypeDefinition> attributeTypes = schema.getAttributeTypes();
              for (AttributeTypeDefinition at : attributeTypes) {
                String name = at.getNameOrOID();
                if (name != null && !name.trim().isEmpty()) {
                  attributeNames.add(name);
                }
              }
            }
          }
        } catch (Exception e) {
          logger.debug("Error getting attribute types from {}: {}", serverName, e.getMessage());
        }
      }
    }

    return attributeNames.stream().distinct()
        .sorted(String.CASE_INSENSITIVE_ORDER)
        .collect(Collectors.toList());
  }

  /**
   * Get available syntax OIDs with descriptions from all selected servers.
   */
  private List<String> getAvailableSyntaxOIDsWithDescriptions() {
    List<String> syntaxOIDs = new ArrayList<>();
    Set<String> selectedServers = MainLayout.getSelectedServers();

    if (selectedServers != null) {
      for (String serverName : selectedServers) {
        try {
          LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
          if (config != null) {
            Schema schema = ldapService.getSchema(config);
            if (schema != null) {
              Collection<AttributeSyntaxDefinition> syntaxes = schema.getAttributeSyntaxes();
              for (AttributeSyntaxDefinition syn : syntaxes) {
                String oid = syn.getOID();
                if (oid != null && !oid.trim().isEmpty()) {
                  String description = syn.getDescription();
                  if (description != null && !description.trim().isEmpty()) {
                    syntaxOIDs.add(oid + " - " + description);
                  } else {
                    syntaxOIDs.add(oid);
                  }
                }
              }
            }
          }
        } catch (Exception e) {
          logger.debug("Error getting syntaxes from {}: {}", serverName, e.getMessage());
        }
      }
    }

    return syntaxOIDs.stream().distinct()
        .sorted(String.CASE_INSENSITIVE_ORDER)
        .collect(Collectors.toList());
  }

  /**
   * Extract the OID/name from a dropdown option that may include description.
   */
  private String extractOidOrName(String optionValue) {
    if (optionValue == null) {
      return null;
    }
    int dashIndex = optionValue.indexOf(" - ");
    if (dashIndex > 0) {
      return optionValue.substring(0, dashIndex).trim();
    }
    return optionValue.trim();
  }

  /**
   * Create a multi-select combobox component with existing items from schema.
   */
  private MultiSelectComboBox<String> createSchemaMultiSelect(String label,
      String placeholder, List<String> items) {
    MultiSelectComboBox<String> multiSelect = new MultiSelectComboBox<>(label);
    multiSelect.setPlaceholder(placeholder);
    multiSelect.setItems(items);
    multiSelect.setWidth("100%");
    return multiSelect;
  }

  /**
   * Create a combobox component with existing items from schema.
   */
  private ComboBox<String> createSchemaComboBox(String label, String placeholder,
      List<String> items) {
    ComboBox<String> comboBox = new ComboBox<>(label);
    comboBox.setPlaceholder(placeholder);
    comboBox.setItems(items);
    comboBox.setWidth("100%");
    comboBox.setAllowCustomValue(true);
    comboBox.addCustomValueSetListener(e -> {
      String customValue = e.getDetail();
      if (customValue != null && !customValue.trim().isEmpty()) {
        comboBox.setValue(customValue);
      }
    });
    return comboBox;
  }

  /**
   * Open dialog for adding a new object class.
   */
  private void openAddObjectClassDialog() {
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers == null || selectedServers.isEmpty()) {
      NotificationHelper.showError("No server selected");
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Add Object Class");
    dialog.setWidth("700px");
    dialog.setHeight("800px");

    FormLayout formLayout = new FormLayout();
    formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

    // Basic fields
    TextField nameField = new TextField("Name*");
    nameField.setRequired(true);

    TextField oidField = new TextField("OID*");
    oidField.setRequired(true);
    oidField.setHelperText("Object identifier (e.g., 1.2.3.4.5.6.7.8)");

    TextField descriptionField = new TextField("Description");

    ComboBox<String> typeComboBox = new ComboBox<>("Type");
    typeComboBox.setItems("STRUCTURAL", "AUXILIARY", "ABSTRACT");
    typeComboBox.setValue("STRUCTURAL");

    Checkbox obsoleteCheckbox = new Checkbox("Obsolete");

    // Get available schema elements for selectors
    List<String> availableObjectClasses = getAvailableObjectClassNames();
    List<String> availableAttributes = getAvailableAttributeTypeNames();

    // Superior classes
    MultiSelectComboBox<String> superiorClassesSelector = createSchemaMultiSelect(
        "Superior Classes",
        "Choose from existing object classes...",
        availableObjectClasses);
    superiorClassesSelector.setHelperText("Select from existing object classes or type new ones");

    // Required attributes
    MultiSelectComboBox<String> requiredAttributesSelector = createSchemaMultiSelect(
        "Required Attributes (MUST)",
        "Choose from existing attributes...",
        availableAttributes);
    requiredAttributesSelector.setHelperText(
        "Select from existing attribute types or type new ones");

    // Optional attributes
    MultiSelectComboBox<String> optionalAttributesSelector = createSchemaMultiSelect(
        "Optional Attributes (MAY)",
        "Choose from existing attributes...",
        availableAttributes);
    optionalAttributesSelector.setHelperText(
        "Select from existing attribute types or type new ones");

    // Schema File field
    TextField schemaFileField = new TextField("Schema File");
    schemaFileField.setValue("99-user.ldif");
    schemaFileField.setHelperText("Schema file name for X-SCHEMA-FILE extension");

    // Raw schema definition field with two-way binding and paste button
    VerticalLayout rawSchemaContainer = new VerticalLayout();
    rawSchemaContainer.setPadding(false);
    rawSchemaContainer.setSpacing(false);
    
    HorizontalLayout rawSchemaHeader = new HorizontalLayout();
    rawSchemaHeader.setWidthFull();
    rawSchemaHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    rawSchemaHeader.getStyle().set("margin-bottom", "4px");
    
    Span rawSchemaLabel = new Span("Raw Schema Definition");
    rawSchemaLabel.getStyle()
        .set("font-weight", "500")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");
    
    Button pasteButton = new Button(new Icon(VaadinIcon.PASTE));
    pasteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    pasteButton.setTooltipText("Paste from clipboard");
    
    rawSchemaHeader.add(rawSchemaLabel, pasteButton);
    rawSchemaHeader.setFlexGrow(1, rawSchemaLabel);
    
    TextArea rawSchemaField = new TextArea();
    rawSchemaField.setWidthFull();
    rawSchemaField.setHeight("200px");
    rawSchemaField.getStyle().set("font-family", "monospace");
    
    // Initialize with empty/minimal definition
    rawSchemaField.setValue("( )");
    
    // Add paste functionality
    pasteButton.addClickListener(e -> {
      rawSchemaField.getElement().executeJs(
          "navigator.clipboard.readText().then(text => {" +
          "  this.value = text;" +
          "  this.dispatchEvent(new Event('change', { bubbles: true }));" +
          "});",
          rawSchemaField.getElement()
      );
    });
    
    rawSchemaContainer.add(rawSchemaHeader, rawSchemaField);

    // Track if we're updating from raw schema to prevent circular updates
    final boolean[] updatingFromRaw = {false};

    // Lambda to update raw schema when UI fields change
    Runnable updateRawSchemaFromUI = () -> {
      if (!updatingFromRaw[0]) {
        Set<String> superior = superiorClassesSelector.getValue();
        String superiorValues = superior != null && !superior.isEmpty()
            ? String.join(", ", superior) : "";
        Set<String> required = requiredAttributesSelector.getValue();
        String requiredValues = required != null && !required.isEmpty()
            ? String.join(", ", required) : "";
        Set<String> optional = optionalAttributesSelector.getValue();
        String optionalValues = optional != null && !optional.isEmpty()
            ? String.join(", ", optional) : "";
        
        // Build extension map for schema file
        Map<String, String[]> extensions = new HashMap<>();
        if (schemaFileField.getValue() != null
            && !schemaFileField.getValue().trim().isEmpty()) {
          extensions.put("X-SCHEMA-FILE", new String[]{schemaFileField.getValue().trim()});
        }
        
        String newRaw = buildObjectClassDefinition(
            oidField.getValue(),
            nameField.getValue(),
            descriptionField.getValue(),
            typeComboBox.getValue(),
            superiorValues,
            requiredValues,
            optionalValues,
            extensions
        );
        
        // Only update if different to prevent infinite loop
        if (!rawSchemaField.getValue().equals(newRaw)) {
          rawSchemaField.setValue(newRaw);
        }
      }
    };

    nameField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    oidField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    descriptionField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    typeComboBox.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    obsoleteCheckbox.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    superiorClassesSelector.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    requiredAttributesSelector.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    optionalAttributesSelector.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    schemaFileField.addValueChangeListener(e -> updateRawSchemaFromUI.run());

    // Add listener to parse raw schema and update UI fields
    rawSchemaField.addValueChangeListener(event -> {
      if (event.isFromClient()) {
        try {
          updatingFromRaw[0] = true;
          String rawValue = event.getValue();
          if (rawValue != null && !rawValue.trim().isEmpty()) {
            parseAndUpdateObjectClassFieldsForAdd(rawValue, nameField, oidField,
                descriptionField, typeComboBox, superiorClassesSelector,
                requiredAttributesSelector, optionalAttributesSelector);
          }
        } catch (Exception ex) {
          logger.debug("Error parsing raw schema: {}", ex.getMessage());
          // Don't show error - allow invalid intermediate states while typing
        } finally {
          updatingFromRaw[0] = false;
        }
      }
    });

    formLayout.add(nameField, oidField, descriptionField, typeComboBox, obsoleteCheckbox,
        superiorClassesSelector, requiredAttributesSelector, optionalAttributesSelector,
        schemaFileField, rawSchemaContainer);

    // Buttons
    Button saveButton = new Button("Add Object Class", e -> {
      if (validateAndSaveObjectClass(dialog, selectedServers, nameField, oidField,
          descriptionField, typeComboBox, obsoleteCheckbox,
          superiorClassesSelector, requiredAttributesSelector, optionalAttributesSelector,
          schemaFileField)) {
        dialog.close();
      }
    });
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.add(formLayout);
    dialog.getFooter().add(cancelButton, saveButton);
    dialog.open();
  }

  /**
   * Open dialog for adding a new attribute type.
   */
  private void openAddAttributeTypeDialog() {
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers == null || selectedServers.isEmpty()) {
      NotificationHelper.showError("No server selected");
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Add Attribute Type");
    dialog.setWidth("700px");
    dialog.setHeight("750px");

    FormLayout formLayout = new FormLayout();
    formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

    // Basic fields
    TextField nameField = new TextField("Name*");
    nameField.setRequired(true);

    TextField oidField = new TextField("OID*");
    oidField.setRequired(true);
    oidField.setHelperText("Object identifier (e.g., 1.2.3.4.5.6.7.8)");

    TextField descriptionField = new TextField("Description");

    // Get available schema elements for selectors
    List<String> availableSyntaxes = getAvailableSyntaxOIDsWithDescriptions();
    List<String> availableAttributes = getAvailableAttributeTypeNames();

    // Syntax OID - enhanced with selector
    ComboBox<String> syntaxOidSelector = createSchemaComboBox(
        "Syntax OID*",
        "Choose from available syntaxes...",
        availableSyntaxes);
    syntaxOidSelector.setRequired(true);
    // Set default to DirectoryString with description if available
    String defaultSyntax = availableSyntaxes.stream()
        .filter(s -> s.startsWith("1.3.6.1.4.1.1466.115.121.1.15"))
        .findFirst()
        .orElse("1.3.6.1.4.1.1466.115.121.1.15");
    syntaxOidSelector.setValue(defaultSyntax);
    syntaxOidSelector.setHelperText("Select from existing syntaxes or enter custom OID");

    // Superior Type
    ComboBox<String> superiorTypeSelector = createSchemaComboBox(
        "Superior Type",
        "Choose from existing attribute types...",
        availableAttributes);
    superiorTypeSelector.setHelperText(
        "Select from existing attribute types or enter custom name");

    ComboBox<String> usageComboBox = new ComboBox<>("Usage");
    usageComboBox.setItems("USER_APPLICATIONS", "DIRECTORY_OPERATION",
        "DISTRIBUTED_OPERATION", "DSA_OPERATION");
    usageComboBox.setValue("USER_APPLICATIONS");

    // Checkboxes
    Checkbox singleValuedCheckbox = new Checkbox("Single Valued");
    Checkbox obsoleteCheckbox = new Checkbox("Obsolete");
    Checkbox collectiveCheckbox = new Checkbox("Collective");
    Checkbox noUserModificationCheckbox = new Checkbox("No User Modification");

    // Schema File field
    TextField schemaFileField = new TextField("Schema File");
    schemaFileField.setValue("99-user.ldif");
    schemaFileField.setHelperText("Schema file name for X-SCHEMA-FILE extension");

    // Raw schema definition field with two-way binding and paste button
    VerticalLayout rawSchemaContainer = new VerticalLayout();
    rawSchemaContainer.setPadding(false);
    rawSchemaContainer.setSpacing(false);
    
    HorizontalLayout rawSchemaHeader = new HorizontalLayout();
    rawSchemaHeader.setWidthFull();
    rawSchemaHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    rawSchemaHeader.getStyle().set("margin-bottom", "4px");
    
    Span rawSchemaLabel = new Span("Raw Schema Definition");
    rawSchemaLabel.getStyle()
        .set("font-weight", "500")
        .set("font-size", "var(--lumo-font-size-s)")
        .set("color", "var(--lumo-secondary-text-color)");
    
    Button pasteButton = new Button(new Icon(VaadinIcon.PASTE));
    pasteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    pasteButton.setTooltipText("Paste from clipboard");
    
    rawSchemaHeader.add(rawSchemaLabel, pasteButton);
    rawSchemaHeader.setFlexGrow(1, rawSchemaLabel);
    
    TextArea rawSchemaField = new TextArea();
    rawSchemaField.setWidthFull();
    rawSchemaField.setHeight("200px");
    rawSchemaField.getStyle().set("font-family", "monospace");
    
    // Initialize with empty/minimal definition
    rawSchemaField.setValue("( )");
    
    // Add paste functionality
    pasteButton.addClickListener(e -> {
      rawSchemaField.getElement().executeJs(
          "navigator.clipboard.readText().then(text => {" +
          "  this.value = text;" +
          "  this.dispatchEvent(new Event('change', { bubbles: true }));" +
          "});",
          rawSchemaField.getElement()
      );
    });
    
    rawSchemaContainer.add(rawSchemaHeader, rawSchemaField);

    // Track if we're updating from raw schema to prevent circular updates
    final boolean[] updatingFromRaw = {false};

    // Lambda to update raw schema when UI fields change
    Runnable updateRawSchemaFromUI = () -> {
      if (!updatingFromRaw[0]) {
        // Build extension map for schema file
        Map<String, String[]> extensions = new HashMap<>();
        if (schemaFileField.getValue() != null
            && !schemaFileField.getValue().trim().isEmpty()) {
          extensions.put("X-SCHEMA-FILE", new String[]{schemaFileField.getValue().trim()});
        }
        
        // Extract OID from syntax selector (may have description)
        String syntaxOid = extractOidOrName(syntaxOidSelector.getValue());
        String superiorType = extractOidOrName(superiorTypeSelector.getValue());
        
        String newRaw = buildAttributeTypeDefinition(
            oidField.getValue(),
            nameField.getValue(),
            descriptionField.getValue(),
            syntaxOid,
            superiorType,
            "", // equality
            "", // ordering
            "", // substring
            usageComboBox.getValue(),
            extensions
        );
        
        // Only update if different to prevent infinite loop
        if (!rawSchemaField.getValue().equals(newRaw)) {
          rawSchemaField.setValue(newRaw);
        }
      }
    };

    nameField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    oidField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    descriptionField.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    syntaxOidSelector.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    superiorTypeSelector.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    usageComboBox.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    singleValuedCheckbox.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    obsoleteCheckbox.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    collectiveCheckbox.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    noUserModificationCheckbox.addValueChangeListener(e -> updateRawSchemaFromUI.run());
    schemaFileField.addValueChangeListener(e -> updateRawSchemaFromUI.run());

    // Add listener to parse raw schema and update UI fields
    rawSchemaField.addValueChangeListener(event -> {
      if (event.isFromClient()) {
        try {
          updatingFromRaw[0] = true;
          String rawValue = event.getValue();
          if (rawValue != null && !rawValue.trim().isEmpty()) {
            parseAndUpdateAttributeTypeFieldsForAdd(rawValue, nameField, oidField,
                descriptionField, syntaxOidSelector, superiorTypeSelector,
                usageComboBox);
          }
        } catch (Exception ex) {
          logger.debug("Error parsing raw schema: {}", ex.getMessage());
          // Don't show error - allow invalid intermediate states while typing
        } finally {
          updatingFromRaw[0] = false;
        }
      }
    });

    formLayout.add(nameField, oidField, descriptionField, syntaxOidSelector,
        superiorTypeSelector, usageComboBox, singleValuedCheckbox, obsoleteCheckbox,
        collectiveCheckbox, noUserModificationCheckbox, schemaFileField, rawSchemaContainer);

    // Buttons
    Button saveButton = new Button("Add Attribute Type", e -> {
      if (validateAndSaveAttributeType(dialog, selectedServers, nameField, oidField,
          descriptionField, syntaxOidSelector, superiorTypeSelector, usageComboBox,
          singleValuedCheckbox, obsoleteCheckbox, collectiveCheckbox,
          noUserModificationCheckbox, schemaFileField)) {
        dialog.close();
      }
    });
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.add(formLayout);
    dialog.getFooter().add(cancelButton, saveButton);
    dialog.open();
  }

  /**
   * Validate and save new object class.
   */
  private boolean validateAndSaveObjectClass(Dialog dialog, Set<String> selectedServers,
      TextField nameField, TextField oidField, TextField descriptionField,
      ComboBox<String> typeComboBox, Checkbox obsoleteCheckbox,
      MultiSelectComboBox<String> superiorClassesSelector,
      MultiSelectComboBox<String> requiredAttributesSelector,
      MultiSelectComboBox<String> optionalAttributesSelector,
      TextField schemaFileField) {
    try {
      // Validate required fields
      if (nameField.getValue() == null || nameField.getValue().trim().isEmpty()) {
        NotificationHelper.showError("Name is required");
        nameField.focus();
        return false;
      }
      if (oidField.getValue() == null || oidField.getValue().trim().isEmpty()) {
        NotificationHelper.showError("OID is required");
        oidField.focus();
        return false;
      }

      // Check if OID or name already exists on any server
      for (String serverName : selectedServers) {
        LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
        if (config == null) {
          continue;
        }
        
        Schema schema = ldapService.getSchema(config);
        if (schema != null) {
          if (schema.getObjectClass(oidField.getValue()) != null) {
            NotificationHelper.showError("An object class with this OID already exists on " + serverName);
            oidField.focus();
            return false;
          }
          if (schema.getObjectClass(nameField.getValue()) != null) {
            NotificationHelper.showError("An object class with this name already exists on " + serverName);
            nameField.focus();
            return false;
          }
        }
      }

      // Build object class definition
      StringBuilder objectClassDef = new StringBuilder();
      objectClassDef.append("( ").append(oidField.getValue().trim());

      if (!nameField.getValue().trim().isEmpty()) {
        objectClassDef.append(" NAME '").append(nameField.getValue().trim()).append("'");
      }

      if (descriptionField.getValue() != null
          && !descriptionField.getValue().trim().isEmpty()) {
        objectClassDef.append(" DESC '").append(descriptionField.getValue().trim())
            .append("'");
      }

      if (obsoleteCheckbox.getValue()) {
        objectClassDef.append(" OBSOLETE");
      }

      // Superior classes
      Set<String> superiorClasses = superiorClassesSelector.getValue();
      if (superiorClasses != null && !superiorClasses.isEmpty()) {
        List<String> superiorList = new ArrayList<>(superiorClasses);
        if (superiorList.size() == 1) {
          objectClassDef.append(" SUP ").append(superiorList.get(0));
        } else if (superiorList.size() > 1) {
          objectClassDef.append(" SUP ( ");
          for (int i = 0; i < superiorList.size(); i++) {
            if (i > 0) {
              objectClassDef.append(" $ ");
            }
            objectClassDef.append(superiorList.get(i));
          }
          objectClassDef.append(" )");
        }
      }

      // Object class type
      if (typeComboBox.getValue() != null) {
        objectClassDef.append(" ").append(typeComboBox.getValue());
      }

      // Required attributes
      Set<String> requiredAttributes = requiredAttributesSelector.getValue();
      if (requiredAttributes != null && !requiredAttributes.isEmpty()) {
        List<String> mustList = new ArrayList<>(requiredAttributes);
        if (mustList.size() == 1) {
          objectClassDef.append(" MUST ").append(mustList.get(0));
        } else if (mustList.size() > 1) {
          objectClassDef.append(" MUST ( ");
          for (int i = 0; i < mustList.size(); i++) {
            if (i > 0) {
              objectClassDef.append(" $ ");
            }
            objectClassDef.append(mustList.get(i));
          }
          objectClassDef.append(" )");
        }
      }

      // Optional attributes
      Set<String> optionalAttributes = optionalAttributesSelector.getValue();
      if (optionalAttributes != null && !optionalAttributes.isEmpty()) {
        List<String> mayList = new ArrayList<>(optionalAttributes);
        if (mayList.size() == 1) {
          objectClassDef.append(" MAY ").append(mayList.get(0));
        } else if (mayList.size() > 1) {
          objectClassDef.append(" MAY ( ");
          for (int i = 0; i < mayList.size(); i++) {
            if (i > 0) {
              objectClassDef.append(" $ ");
            }
            objectClassDef.append(mayList.get(i));
          }
          objectClassDef.append(" )");
        }
      }

      // Add X-SCHEMA-FILE extension if schema file is provided
      if (schemaFileField.getValue() != null
          && !schemaFileField.getValue().trim().isEmpty()) {
        objectClassDef.append(" X-SCHEMA-FILE '")
            .append(schemaFileField.getValue().trim()).append("'");
      }

      objectClassDef.append(" )");

      // Add to all selected LDAP servers
      int successCount = 0;
      int failCount = 0;
      StringBuilder failedServers = new StringBuilder();
      
      for (String serverName : selectedServers) {
        LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
        if (config == null) {
          failCount++;
          if (failedServers.length() > 0) {
            failedServers.append(", ");
          }
          failedServers.append(serverName).append(" (config not found)");
          continue;
        }
        
        try {
          ldapService.addObjectClassToSchema(config, objectClassDef.toString());
          
          // Refresh the schema cache to pick up the new object class
          try {
            ldapService.refreshSchema(config);
          } catch (Exception rex) {
            logger.warn("Failed to refresh schema cache after adding object class for {}: {}", 
                serverName, rex.getMessage());
          }
          
          successCount++;
        } catch (Exception e) {
          failCount++;
          if (failedServers.length() > 0) {
            failedServers.append(", ");
          }
          failedServers.append(serverName).append(" (").append(e.getMessage()).append(")");
          logger.error("Failed to add object class to {}", serverName, e);
        }
      }

      // Reload schema display
      loadSchemas();
      
      // Show appropriate message
      if (successCount > 0 && failCount == 0) {
        NotificationHelper.showSuccess("Object class '" + nameField.getValue() + "' added successfully to " 
            + successCount + " server(s)");
      } else if (successCount > 0 && failCount > 0) {
        NotificationHelper.showError("Object class added to " + successCount + " server(s), but failed on: " 
            + failedServers.toString());
      } else {
        NotificationHelper.showError("Failed to add object class to all servers: " + failedServers.toString());
        return false;
      }
      
      return true;
    } catch (Exception e) {
      NotificationHelper.showError("Failed to add object class: " + e.getMessage());
      logger.error("Failed to add object class", e);
      return false;
    }
  }

  /**
   * Validate and save new attribute type.
   */
  private boolean validateAndSaveAttributeType(Dialog dialog, Set<String> selectedServers,
      TextField nameField, TextField oidField, TextField descriptionField,
      ComboBox<String> syntaxOidSelector, ComboBox<String> superiorTypeSelector,
      ComboBox<String> usageComboBox, Checkbox singleValuedCheckbox,
      Checkbox obsoleteCheckbox, Checkbox collectiveCheckbox,
      Checkbox noUserModificationCheckbox, TextField schemaFileField) {
    try {
      // Validate required fields
      if (nameField.getValue() == null || nameField.getValue().trim().isEmpty()) {
        NotificationHelper.showError("Name is required");
        nameField.focus();
        return false;
      }
      if (oidField.getValue() == null || oidField.getValue().trim().isEmpty()) {
        NotificationHelper.showError("OID is required");
        oidField.focus();
        return false;
      }

      // Get syntax OID from selector (extract OID from description if present)
      String syntaxOid = extractOidOrName(syntaxOidSelector.getValue());
      if (syntaxOid == null || syntaxOid.trim().isEmpty()) {
        NotificationHelper.showError("Syntax OID is required");
        syntaxOidSelector.focus();
        return false;
      }

      // Check if OID or name already exists on any server
      for (String serverName : selectedServers) {
        LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
        if (config == null) {
          continue;
        }
        
        Schema schema = ldapService.getSchema(config);
        if (schema != null) {
          if (schema.getAttributeType(oidField.getValue()) != null) {
            NotificationHelper.showError("An attribute type with this OID already exists on " + serverName);
            oidField.focus();
            return false;
          }
          if (schema.getAttributeType(nameField.getValue()) != null) {
            NotificationHelper.showError("An attribute type with this name already exists on " + serverName);
            nameField.focus();
            return false;
          }
        }
      }

      // Build attribute type definition
      StringBuilder attributeDef = new StringBuilder();
      attributeDef.append("( ").append(oidField.getValue().trim());

      if (!nameField.getValue().trim().isEmpty()) {
        attributeDef.append(" NAME '").append(nameField.getValue().trim()).append("'");
      }

      if (descriptionField.getValue() != null
          && !descriptionField.getValue().trim().isEmpty()) {
        attributeDef.append(" DESC '").append(descriptionField.getValue().trim())
            .append("'");
      }

      if (obsoleteCheckbox.getValue()) {
        attributeDef.append(" OBSOLETE");
      }

      // Superior type
      String superiorType = extractOidOrName(superiorTypeSelector.getValue());
      if (superiorType != null && !superiorType.trim().isEmpty()) {
        attributeDef.append(" SUP ").append(superiorType.trim());
      }

      attributeDef.append(" SYNTAX ").append(syntaxOid.trim());

      if (singleValuedCheckbox.getValue()) {
        attributeDef.append(" SINGLE-VALUE");
      }

      if (collectiveCheckbox.getValue()) {
        attributeDef.append(" COLLECTIVE");
      }

      if (noUserModificationCheckbox.getValue()) {
        attributeDef.append(" NO-USER-MODIFICATION");
      }

      if (usageComboBox.getValue() != null
          && !"USER_APPLICATIONS".equals(usageComboBox.getValue())) {
        attributeDef.append(" USAGE ")
            .append(usageComboBox.getValue().toLowerCase().replace("_", ""));
      }

      // Add X-SCHEMA-FILE extension if schema file is provided
      if (schemaFileField.getValue() != null
          && !schemaFileField.getValue().trim().isEmpty()) {
        attributeDef.append(" X-SCHEMA-FILE '")
            .append(schemaFileField.getValue().trim()).append("'");
      }

      attributeDef.append(" )");

      // Add to all selected LDAP servers
      int successCount = 0;
      int failCount = 0;
      StringBuilder failedServers = new StringBuilder();

      for (String serverName : selectedServers) {
        LdapServerConfig config = configService.getConfiguration(serverName).orElse(null);
        if (config == null) {
          failCount++;
          if (failedServers.length() > 0) {
            failedServers.append(", ");
          }
          failedServers.append(serverName).append(" (config not found)");
          continue;
        }

        try {
          ldapService.addAttributeTypeToSchema(config, attributeDef.toString());
          
          // Refresh the schema cache to pick up the new attribute type
          try {
            ldapService.refreshSchema(config);
          } catch (Exception rex) {
            logger.warn("Failed to refresh schema cache after adding attribute type for {}: {}", 
                serverName, rex.getMessage());
          }
          
          successCount++;
        } catch (Exception e) {
          failCount++;
          if (failedServers.length() > 0) {
            failedServers.append(", ");
          }
          failedServers.append(serverName).append(" (").append(e.getMessage()).append(")");
        }
      }

      // Reload schema display
      loadSchemas();

      // Show appropriate message based on results
      if (successCount > 0 && failCount == 0) {
        NotificationHelper.showSuccess("Attribute type '" + nameField.getValue()
            + "' added successfully to " + successCount + " server(s)");
        return true;
      } else if (successCount > 0 && failCount > 0) {
        NotificationHelper.showError("Attribute type '" + nameField.getValue() + "' added to " + successCount
            + " server(s), but failed on: " + failedServers);
        return true; // Partial success
      } else {
        NotificationHelper.showError("Failed to add attribute type to any server: " + failedServers);
        return false;
      }
    } catch (Exception e) {
      NotificationHelper.showError("Failed to add attribute type: " + e.getMessage());
      logger.error("Failed to add attribute type", e);
      return false;
    }
  }

  /**
   * Parse raw object class definition and update UI fields.
   *
   * @param rawDefinition raw LDAP schema definition
   * @param namesField names field
   * @param descField description field
   * @param typeField type field
   * @param superiorField superior classes field
   * @param requiredField required attributes field
   * @param optionalField optional attributes field
   */
  private void parseAndUpdateObjectClassFields(String rawDefinition,
      TextField namesField, TextArea descField, ComboBox<String> typeField,
      MultiSelectComboBox<String> superiorField,
      MultiSelectComboBox<String> requiredField,
      MultiSelectComboBox<String> optionalField) {
    try {
      // Parse using UnboundID SDK
      ObjectClassDefinition parsed = new ObjectClassDefinition(rawDefinition);
      
      // Update names
      String[] names = parsed.getNames();
      if (names != null && names.length > 0) {
        namesField.setValue(String.join(", ", names));
      } else {
        namesField.clear();
      }
      
      // Update description
      if (parsed.getDescription() != null) {
        descField.setValue(parsed.getDescription());
      } else {
        descField.clear();
      }
      
      // Update type
      if (parsed.getObjectClassType() != null) {
        typeField.setValue(parsed.getObjectClassType().getName());
      }
      
      // Update superior classes
      String[] superiors = parsed.getSuperiorClasses();
      if (superiors != null && superiors.length > 0) {
        superiorField.setValue(new HashSet<>(Arrays.asList(superiors)));
      } else {
        superiorField.clear();
      }
      
      // Update required attributes
      String[] required = parsed.getRequiredAttributes();
      if (required != null && required.length > 0) {
        requiredField.setValue(new HashSet<>(Arrays.asList(required)));
      } else {
        requiredField.clear();
      }
      
      // Update optional attributes
      String[] optional = parsed.getOptionalAttributes();
      if (optional != null && optional.length > 0) {
        optionalField.setValue(new HashSet<>(Arrays.asList(optional)));
      } else {
        optionalField.clear();
      }
    } catch (Exception e) {
      logger.debug("Failed to parse object class definition: {}", e.getMessage());
      // Don't update fields if parsing fails
    }
  }

  /**
   * Parse raw attribute type definition and update UI fields.
   *
   * @param rawDefinition raw LDAP schema definition
   * @param namesField names field
   * @param descField description field
   * @param syntaxField syntax OID field
   * @param superiorField superior type field
   * @param equalityField equality matching rule field
   * @param orderingField ordering matching rule field
   * @param substringField substring matching rule field
   * @param usageField usage field
   */
  private void parseAndUpdateAttributeTypeFields(String rawDefinition,
      TextField namesField, TextArea descField, TextField syntaxField,
      TextField superiorField, TextField equalityField, TextField orderingField,
      TextField substringField, TextField usageField) {
    try {
      // Parse using UnboundID SDK
      AttributeTypeDefinition parsed = new AttributeTypeDefinition(rawDefinition);
      
      // Update names
      String[] names = parsed.getNames();
      if (names != null && names.length > 0) {
        namesField.setValue(String.join(", ", names));
      } else {
        namesField.clear();
      }
      
      // Update description
      if (parsed.getDescription() != null) {
        descField.setValue(parsed.getDescription());
      } else {
        descField.clear();
      }
      
      // Update syntax OID
      if (parsed.getSyntaxOID() != null) {
        syntaxField.setValue(parsed.getSyntaxOID());
      } else {
        syntaxField.clear();
      }
      
      // Update superior type
      if (parsed.getSuperiorType() != null) {
        superiorField.setValue(parsed.getSuperiorType());
      } else {
        superiorField.clear();
      }
      
      // Update equality matching rule
      if (parsed.getEqualityMatchingRule() != null) {
        equalityField.setValue(parsed.getEqualityMatchingRule());
      } else {
        equalityField.clear();
      }
      
      // Update ordering matching rule
      if (parsed.getOrderingMatchingRule() != null) {
        orderingField.setValue(parsed.getOrderingMatchingRule());
      } else {
        orderingField.clear();
      }
      
      // Update substring matching rule
      if (parsed.getSubstringMatchingRule() != null) {
        substringField.setValue(parsed.getSubstringMatchingRule());
      } else {
        substringField.clear();
      }
      
      // Update usage
      if (parsed.getUsage() != null) {
        usageField.setValue(parsed.getUsage().getName());
      } else {
        usageField.clear();
      }
    } catch (Exception e) {
      logger.debug("Failed to parse attribute type definition: {}", e.getMessage());
      // Don't update fields if parsing fails
    }
  }

  /**
   * Parse raw object class definition and update UI fields for Add dialog.
   *
   * @param rawDefinition raw LDAP schema definition
   * @param nameField name field
   * @param oidField OID field
   * @param descField description field
   * @param typeField type field
   * @param superiorField superior classes field
   * @param requiredField required attributes field
   * @param optionalField optional attributes field
   */
  private void parseAndUpdateObjectClassFieldsForAdd(String rawDefinition,
      TextField nameField, TextField oidField, TextField descField,
      ComboBox<String> typeField,
      MultiSelectComboBox<String> superiorField,
      MultiSelectComboBox<String> requiredField,
      MultiSelectComboBox<String> optionalField) {
    try {
      // Parse using UnboundID SDK
      ObjectClassDefinition parsed = new ObjectClassDefinition(rawDefinition);
      
      // Update OID
      if (parsed.getOID() != null) {
        oidField.setValue(parsed.getOID());
      } else {
        oidField.clear();
      }
      
      // Update names
      String[] names = parsed.getNames();
      if (names != null && names.length > 0) {
        nameField.setValue(names[0]); // Use first name for the name field
      } else {
        nameField.clear();
      }
      
      // Update description
      if (parsed.getDescription() != null) {
        descField.setValue(parsed.getDescription());
      } else {
        descField.clear();
      }
      
      // Update type
      if (parsed.getObjectClassType() != null) {
        typeField.setValue(parsed.getObjectClassType().getName());
      }
      
      // Update superior classes
      String[] superiors = parsed.getSuperiorClasses();
      if (superiors != null && superiors.length > 0) {
        superiorField.setValue(new HashSet<>(Arrays.asList(superiors)));
      } else {
        superiorField.clear();
      }
      
      // Update required attributes
      String[] required = parsed.getRequiredAttributes();
      if (required != null && required.length > 0) {
        requiredField.setValue(new HashSet<>(Arrays.asList(required)));
      } else {
        requiredField.clear();
      }
      
      // Update optional attributes
      String[] optional = parsed.getOptionalAttributes();
      if (optional != null && optional.length > 0) {
        optionalField.setValue(new HashSet<>(Arrays.asList(optional)));
      } else {
        optionalField.clear();
      }
    } catch (Exception e) {
      logger.debug("Failed to parse object class definition: {}", e.getMessage());
      // Don't update fields if parsing fails
    }
  }

  /**
   * Parse raw attribute type definition and update UI fields for Add dialog.
   *
   * @param rawDefinition raw LDAP schema definition
   * @param nameField name field
   * @param oidField OID field
   * @param descField description field
   * @param syntaxField syntax OID field
   * @param superiorField superior type field
   * @param usageField usage field
   */
  private void parseAndUpdateAttributeTypeFieldsForAdd(String rawDefinition,
      TextField nameField, TextField oidField, TextField descField,
      ComboBox<String> syntaxField, ComboBox<String> superiorField,
      ComboBox<String> usageField) {
    try {
      // Parse using UnboundID SDK
      AttributeTypeDefinition parsed = new AttributeTypeDefinition(rawDefinition);
      
      // Update OID
      if (parsed.getOID() != null) {
        oidField.setValue(parsed.getOID());
      } else {
        oidField.clear();
      }
      
      // Update names
      String[] names = parsed.getNames();
      if (names != null && names.length > 0) {
        nameField.setValue(names[0]); // Use first name for the name field
      } else {
        nameField.clear();
      }
      
      // Update description
      if (parsed.getDescription() != null) {
        descField.setValue(parsed.getDescription());
      } else {
        descField.clear();
      }
      
      // Update syntax OID
      if (parsed.getSyntaxOID() != null) {
        syntaxField.setValue(parsed.getSyntaxOID());
      } else {
        syntaxField.clear();
      }
      
      // Update superior type
      if (parsed.getSuperiorType() != null) {
        superiorField.setValue(parsed.getSuperiorType());
      } else {
        superiorField.clear();
      }
      
      // Update usage
      if (parsed.getUsage() != null) {
        usageField.setValue(parsed.getUsage().getName());
      } else {
        usageField.clear();
      }
    } catch (Exception e) {
      logger.debug("Failed to parse attribute type definition: {}", e.getMessage());
      // Don't update fields if parsing fails
    }
  }

  /**
   * Add "Used as May" and "Used as Must" sections to attribute type details.
   *
   * @param details the layout to add sections to
   * @param at the attribute type definition
   * @param se the schema element
   */
  private void addAttributeUsageSection(VerticalLayout details, AttributeTypeDefinition at,
      SchemaElement<AttributeTypeDefinition> se) {
    try {
      LdapServerConfig config = se.getServerConfig();
      if (config == null) {
        return;
      }

      Schema schema = ldapService.getSchema(config);
      if (schema == null) {
        return;
      }

      // Find all attribute names for this attribute type
      Set<String> attrNames = new HashSet<>();
      if (at.getNames() != null && at.getNames().length > 0) {
        attrNames.addAll(Arrays.asList(at.getNames()));
      }
      attrNames.add(at.getOID());

      // Find object classes that use this attribute
      List<ObjectClassDefinition> usedAsMay = new ArrayList<>();
      List<ObjectClassDefinition> usedAsMust = new ArrayList<>();

      for (ObjectClassDefinition oc : schema.getObjectClasses()) {
        // Check MAY list
        if (oc.getOptionalAttributes() != null) {
          for (String mayAttr : oc.getOptionalAttributes()) {
            if (attrNames.contains(mayAttr)) {
              usedAsMay.add(oc);
              break;
            }
          }
        }

        // Check MUST list
        if (oc.getRequiredAttributes() != null) {
          for (String mustAttr : oc.getRequiredAttributes()) {
            if (attrNames.contains(mustAttr)) {
              usedAsMust.add(oc);
              break;
            }
          }
        }
      }

      // Add "Used as May" section
      if (!usedAsMay.isEmpty()) {
        addClickableObjectClassList(details, "Used as May", usedAsMay, se.getServerName(), config);
      }

      // Add "Used as Must" section
      if (!usedAsMust.isEmpty()) {
        addClickableObjectClassList(details, "Used as Must", usedAsMust, se.getServerName(), config);
      }
    } catch (Exception e) {
      logger.warn("Failed to load attribute usage information: {}", e.getMessage());
    }
  }

  /**
   * Add a clickable list of object classes to the details panel.
   *
   * @param parent the parent layout
   * @param label the section label
   * @param objectClasses the list of object classes
   * @param serverName the server name
   * @param config the server configuration
   */
  private void addClickableObjectClassList(VerticalLayout parent, String label,
      List<ObjectClassDefinition> objectClasses, String serverName, LdapServerConfig config) {
    HorizontalLayout row = new HorizontalLayout();
    row.setWidthFull();
    row.setDefaultVerticalComponentAlignment(Alignment.START);
    row.setSpacing(true);

    Span labelSpan = new Span(label + ":");
    labelSpan.getStyle().set("font-weight", "bold").set("min-width", "150px");

    HorizontalLayout linksLayout = new HorizontalLayout();
    linksLayout.setSpacing(false);
    linksLayout.getStyle().set("flex-wrap", "wrap");

    for (int i = 0; i < objectClasses.size(); i++) {
      ObjectClassDefinition oc = objectClasses.get(i);
      
      Button linkButton = new Button(oc.getNameOrOID());
      linkButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
      linkButton.getStyle().set("padding", "0").set("min-height", "auto");
      
      // Get schema for nested lookups
      try {
        Schema schema = ldapService.getSchema(config);
        linkButton.addClickListener(e -> 
            SchemaDetailDialogHelper.showObjectClassDialog(oc, serverName, schema));
      } catch (Exception ex) {
        linkButton.addClickListener(e -> 
            SchemaDetailDialogHelper.showObjectClassDialog(oc, serverName));
      }
      
      linksLayout.add(linkButton);
      
      if (i < objectClasses.size() - 1) {
        Span comma = new Span(", ");
        comma.getStyle().set("padding", "0 2px");
        linksLayout.add(comma);
      }
    }

    row.add(labelSpan, linksLayout);
    row.setFlexGrow(1, linksLayout);

    parent.add(row);
  }

  /**
   * Add a clickable list of attributes to the details panel.
   *
   * @param parent the parent layout
   * @param label the section label
   * @param attributeNames the attribute names array
   * @param schema the schema for looking up attribute definitions
   * @param serverName the server name
   */
  private void addClickableAttributeListToDetails(VerticalLayout parent, String label,
      String[] attributeNames, Schema schema, String serverName) {
    HorizontalLayout row = new HorizontalLayout();
    row.setWidthFull();
    row.setDefaultVerticalComponentAlignment(Alignment.START);
    row.setSpacing(true);

    Span labelSpan = new Span(label + ":");
    labelSpan.getStyle().set("font-weight", "bold").set("min-width", "150px");

    HorizontalLayout linksLayout = new HorizontalLayout();
    linksLayout.setSpacing(false);
    linksLayout.getStyle().set("flex-wrap", "wrap");

    for (int i = 0; i < attributeNames.length; i++) {
      String attrName = attributeNames[i];
      
      Button linkButton = new Button(attrName);
      linkButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
      linkButton.getStyle().set("padding", "0").set("min-height", "auto");
      
      if (schema != null) {
        AttributeTypeDefinition attrType = schema.getAttributeType(attrName);
        if (attrType != null) {
          linkButton.addClickListener(e -> 
              SchemaDetailDialogHelper.showAttributeTypeDialog(attrType, serverName, schema));
        } else {
          // If attribute type not found, disable the button
          linkButton.setEnabled(false);
        }
      } else {
        // No schema available, disable the button
        linkButton.setEnabled(false);
      }
      
      linksLayout.add(linkButton);
      
      if (i < attributeNames.length - 1) {
        Span comma = new Span(", ");
        comma.getStyle().set("padding", "0 2px");
        linksLayout.add(comma);
      }
    }

    row.add(labelSpan, linksLayout);
    row.setFlexGrow(1, linksLayout);

    parent.add(row);
  }

  /**
   * Get the Schema for a SchemaElement.
   *
   * @param se the schema element
   * @return the Schema or null if not available
   */
  private Schema getSchemaForElement(SchemaElement<?> se) {
    try {
      LdapServerConfig config = se.getServerConfig();
      if (config != null) {
        return ldapService.getSchema(config);
      }
    } catch (Exception e) {
      logger.warn("Failed to get schema for element: {}", e.getMessage());
    }
    return null;
  }
}