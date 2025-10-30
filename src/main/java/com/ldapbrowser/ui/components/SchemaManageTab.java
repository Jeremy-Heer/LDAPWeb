package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.model.SchemaElement;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.MainLayout;
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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
    refreshButton.addClickListener(e -> loadSchemas());

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
    detailsPanel.setWidth("400px");
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

    objectClassGrid.addColumn(se -> se.getElement().getNameOrOID())
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

    attributeTypeGrid.addColumn(se -> se.getElement().getNameOrOID())
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
    rightPanel.setWidth("400px");
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

    // Main horizontal split
    HorizontalLayout mainLayout = new HorizontalLayout();
    mainLayout.setSizeFull();
    mainLayout.setPadding(false);
    mainLayout.setSpacing(false);
    mainLayout.add(leftPanel, rightPanel);
    mainLayout.setFlexGrow(1, leftPanel);

    add(mainLayout);
    setFlexGrow(1, mainLayout);
  }

  /**
   * Loads schemas from all selected servers.
   */
  public void loadSchemas() {
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers == null || selectedServers.isEmpty()) {
      showError("No servers selected");
      return;
    }

    try {
      filterCurrentView();
      updateAddButtons();
      showSuccess("Schema loaded successfully");
    } catch (Exception e) {
      showError("Failed to load schema: " + e.getMessage());
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
        showError("Failed to load schema from " + serverName + ": " + e.getMessage());
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
        showError("Failed to load schema from " + serverName + ": " + e.getMessage());
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
        showError("Failed to load schema from " + serverName + ": " + e.getMessage());
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
        showError("Failed to load schema from " + serverName + ": " + e.getMessage());
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
        showError("Failed to load schema from " + serverName + ": " + e.getMessage());
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

    addDetailRow(details, "Server", se.getServerName());
    addDetailRow(details, "OID", oc.getOID());
    addDetailRow(details, "Names",
        oc.getNames() != null ? String.join(", ", Arrays.asList(oc.getNames())) : "");
    addDetailRow(details, "Description", oc.getDescription());

    // Schema file (extension)
    String ocSchemaFile = getSchemaFileFromExtensions(oc.getExtensions());
    addDetailRow(details, "Schema File", ocSchemaFile);

    addDetailRow(details, "Type",
        oc.getObjectClassType() != null ? oc.getObjectClassType().getName() : "");
    addDetailRow(details, "Obsolete", oc.isObsolete() ? "Yes" : "No");

    if (oc.getSuperiorClasses() != null && oc.getSuperiorClasses().length > 0) {
      addDetailRow(details, "Superior Classes",
          String.join(", ", oc.getSuperiorClasses()));
    }

    if (oc.getRequiredAttributes() != null && oc.getRequiredAttributes().length > 0) {
      addDetailRow(details, "Required Attributes",
          String.join(", ", oc.getRequiredAttributes()));
    }

    if (oc.getOptionalAttributes() != null && oc.getOptionalAttributes().length > 0) {
      addDetailRow(details, "Optional Attributes",
          String.join(", ", oc.getOptionalAttributes()));
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
      addDetailRow(details, "Extensions", extensions.toString());
    }

    // Add raw schema definition at the bottom
    addRawDefinition(details, oc);

    detailsPanel.add(details);
  }

  private void showAttributeTypeDetails(SchemaElement<AttributeTypeDefinition> se) {
    detailsPanel.removeAll();
    AttributeTypeDefinition at = se.getElement();

    VerticalLayout details = new VerticalLayout();
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

    addDetailRow(details, "Server", se.getServerName());
    addDetailRow(details, "OID", at.getOID());
    addDetailRow(details, "Names",
        at.getNames() != null ? String.join(", ", Arrays.asList(at.getNames())) : "");
    addDetailRow(details, "Description", at.getDescription());

    // Schema file (extension)
    String atSchemaFile = getSchemaFileFromExtensions(at.getExtensions());
    addDetailRow(details, "Schema File", atSchemaFile);

    addDetailRow(details, "Syntax OID", at.getSyntaxOID());
    addDetailRow(details, "Obsolete", at.isObsolete() ? "Yes" : "No");
    addDetailRow(details, "Single Value", at.isSingleValued() ? "Yes" : "No");
    addDetailRow(details, "Collective", at.isCollective() ? "Yes" : "No");
    addDetailRow(details, "No User Modification",
        at.isNoUserModification() ? "Yes" : "No");

    // Usage
    if (at.getUsage() != null) {
      addDetailRow(details, "Usage", at.getUsage().getName());
    }

    if (at.getSuperiorType() != null) {
      addDetailRow(details, "Superior Type", at.getSuperiorType());
    }

    if (at.getEqualityMatchingRule() != null) {
      addDetailRow(details, "Equality Matching Rule", at.getEqualityMatchingRule());
    }

    // Ordering matching rule
    if (at.getOrderingMatchingRule() != null) {
      addDetailRow(details, "Ordering Matching Rule", at.getOrderingMatchingRule());
    }

    // Substring matching rule
    if (at.getSubstringMatchingRule() != null) {
      addDetailRow(details, "Substring Matching Rule", at.getSubstringMatchingRule());
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
      addDetailRow(details, "Extensions", extensions.toString());
    }

    // Add raw schema definition at the bottom
    addRawDefinition(details, at);

    detailsPanel.add(details);
  }

  private void showMatchingRuleDetails(SchemaElement<MatchingRuleDefinition> se) {
    detailsPanel.removeAll();
    MatchingRuleDefinition mr = se.getElement();

    VerticalLayout details = new VerticalLayout();
    details.setSpacing(true);
    details.setPadding(true);

    H3 header = new H3("Matching Rule: " + mr.getNameOrOID());
    header.getStyle().set("margin-bottom", "16px");
    details.add(header);

    addDetailRow(details, "Server", se.getServerName());
    addDetailRow(details, "OID", mr.getOID());
    addDetailRow(details, "Names",
        mr.getNames() != null ? String.join(", ", Arrays.asList(mr.getNames())) : "");
    addDetailRow(details, "Description", mr.getDescription());
    addDetailRow(details, "Syntax OID", mr.getSyntaxOID());
    addDetailRow(details, "Obsolete", mr.isObsolete() ? "Yes" : "No");

    // Add raw schema definition at the bottom
    addRawDefinition(details, mr);

    detailsPanel.add(details);
  }

  private void showMatchingRuleUseDetails(SchemaElement<MatchingRuleUseDefinition> se) {
    detailsPanel.removeAll();
    MatchingRuleUseDefinition mru = se.getElement();

    VerticalLayout details = new VerticalLayout();
    details.setSpacing(true);
    details.setPadding(true);

    H3 header = new H3("Matching Rule Use: " + mru.getOID());
    header.getStyle().set("margin-bottom", "16px");
    details.add(header);

    addDetailRow(details, "Server", se.getServerName());
    addDetailRow(details, "OID", mru.getOID());
    addDetailRow(details, "Names",
        mru.getNames() != null ? String.join(", ", Arrays.asList(mru.getNames())) : "");
    addDetailRow(details, "Description", mru.getDescription());
    addDetailRow(details, "Obsolete", mru.isObsolete() ? "Yes" : "No");

    if (mru.getApplicableAttributeTypes() != null
        && mru.getApplicableAttributeTypes().length > 0) {
      addDetailRow(details, "Applicable Attribute Types",
          String.join(", ", mru.getApplicableAttributeTypes()));
    }

    // Extensions
    if (mru.getExtensions() != null && !mru.getExtensions().isEmpty()) {
      StringBuilder extensionsText = new StringBuilder();
      for (Map.Entry<String, String[]> entry : mru.getExtensions().entrySet()) {
        extensionsText.append(entry.getKey()).append(": ")
            .append(String.join(", ", entry.getValue())).append("\n");
      }
      addDetailRow(details, "Extensions", extensionsText.toString());
    }

    // Add raw schema definition at the bottom
    addRawDefinition(details, mru);

    detailsPanel.add(details);
  }

  private void showSyntaxDetails(SchemaElement<AttributeSyntaxDefinition> se) {
    detailsPanel.removeAll();
    AttributeSyntaxDefinition syn = se.getElement();

    VerticalLayout details = new VerticalLayout();
    details.setSpacing(true);
    details.setPadding(true);

    H3 header = new H3("Syntax: " + syn.getOID());
    header.getStyle().set("margin-bottom", "16px");
    details.add(header);

    addDetailRow(details, "Server", se.getServerName());
    addDetailRow(details, "OID", syn.getOID());
    addDetailRow(details, "Description", syn.getDescription());

    // Extensions (if any)
    if (syn.getExtensions() != null && !syn.getExtensions().isEmpty()) {
      StringBuilder extensionsText = new StringBuilder();
      for (Map.Entry<String, String[]> entry : syn.getExtensions().entrySet()) {
        extensionsText.append(entry.getKey()).append(": ")
            .append(String.join(", ", entry.getValue())).append("\n");
      }
      addDetailRow(details, "Extensions", extensionsText.toString());
    }

    // Add raw schema definition at the bottom
    addRawDefinition(details, syn);

    detailsPanel.add(details);
  }

  private void addDetailRow(VerticalLayout parent, String label, String value) {
    if (value != null && !value.trim().isEmpty()) {
      HorizontalLayout row = new HorizontalLayout();
      row.setWidthFull();
      row.setDefaultVerticalComponentAlignment(Alignment.START);
      row.setSpacing(true);

      Span labelSpan = new Span(label + ":");
      labelSpan.getStyle().set("font-weight", "bold").set("min-width", "150px");

      Span valueSpan = new Span(value);
      valueSpan.getStyle().set("word-wrap", "break-word");

      row.add(labelSpan, valueSpan);
      row.setFlexGrow(1, valueSpan);

      parent.add(row);
    }
  }

  private void showSuccess(String message) {
    Notification notification = Notification.show(message, 3000,
        Notification.Position.TOP_END);
    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  private void showError(String message) {
    Notification notification = Notification.show(message, 5000,
        Notification.Position.TOP_END);
    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
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
    boolean canModify = false;
    try {
      canModify = ldapService.supportsSchemaModification(config);
    } catch (Exception e) {
      logger.warn("Error checking schema modification support", e);
    }

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

    TextField typeField = new TextField("Type");
    typeField.setValue(oc.getObjectClassType() != null
        ? oc.getObjectClassType().getName() : "");
    typeField.setWidthFull();
    typeField.setReadOnly(!canModify);

    TextField superiorField = new TextField("Superior Classes (comma-separated)");
    superiorField.setValue(oc.getSuperiorClasses() != null
        ? String.join(", ", oc.getSuperiorClasses()) : "");
    superiorField.setWidthFull();
    superiorField.setReadOnly(!canModify);

    TextField requiredField = new TextField("Required Attributes (comma-separated)");
    requiredField.setValue(oc.getRequiredAttributes() != null
        ? String.join(", ", oc.getRequiredAttributes()) : "");
    requiredField.setWidthFull();
    requiredField.setReadOnly(!canModify);

    TextField optionalField = new TextField("Optional Attributes (comma-separated)");
    optionalField.setValue(oc.getOptionalAttributes() != null
        ? String.join(", ", oc.getOptionalAttributes()) : "");
    optionalField.setWidthFull();
    optionalField.setReadOnly(!canModify);

    TextField schemaFileField = new TextField("Schema File");
    schemaFileField.setValue(getSchemaFileFromExtensions(oc.getExtensions()) != null
        ? getSchemaFileFromExtensions(oc.getExtensions()) : "");
    schemaFileField.setWidthFull();
    schemaFileField.setReadOnly(true);

    form.add(oidField, namesField, descField, typeField, superiorField,
        requiredField, optionalField, schemaFileField);

    Button cancelButton = new Button("Cancel", e -> dialog.close());
    
    if (canModify) {
      Button saveButton = new Button("Save", e -> {
        try {
          // Build new definition string
          String oldDefinition = getRawDefinitionString(oc);
          String newDefinition = buildObjectClassDefinition(
              oidField.getValue(),
              namesField.getValue(),
              descField.getValue(),
              typeField.getValue(),
              superiorField.getValue(),
              requiredField.getValue(),
              optionalField.getValue(),
              filterWritableExtensions(oc.getExtensions())
          );
          
          // Only modify if definition changed
          if (!normalizeDefinition(oldDefinition).equals(normalizeDefinition(newDefinition))) {
            ldapService.modifyObjectClassInSchema(config, oldDefinition, newDefinition);
            Notification.show("Object class updated successfully", 3000,
                Notification.Position.BOTTOM_START);
            dialog.close();
            // Reload schema
            refreshSchema();
          } else {
            Notification.show("No changes detected", 3000,
                Notification.Position.BOTTOM_START);
            dialog.close();
          }
        } catch (Exception ex) {
          logger.error("Error saving object class", ex);
          Notification.show("Error: " + ex.getMessage(), 5000,
              Notification.Position.BOTTOM_START);
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
    boolean canModify = false;
    try {
      canModify = ldapService.supportsSchemaModification(config);
    } catch (Exception e) {
      logger.warn("Error checking schema modification support", e);
    }

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

    form.add(oidField, namesField, descField, syntaxField, superiorField,
        equalityField, orderingField, substringField, usageField, schemaFileField);

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
            Notification.show("Attribute type updated successfully", 3000,
                Notification.Position.BOTTOM_START);
            dialog.close();
            // Reload schema
            refreshSchema();
          } else {
            Notification.show("No changes detected", 3000,
                Notification.Position.BOTTOM_START);
            dialog.close();
          }
        } catch (Exception ex) {
          logger.error("Error saving attribute type", ex);
          Notification.show("Error: " + ex.getMessage(), 5000,
              Notification.Position.BOTTOM_START);
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
   * Adds a read-only, monospace TextArea containing the raw schema definition
   * to the provided details layout.
   *
   * @param details details layout
   * @param schemaElement schema element
   */
  private void addRawDefinition(VerticalLayout details, Object schemaElement) {
    String raw = getRawDefinitionString(schemaElement);
    if (raw != null && !raw.trim().isEmpty()) {
      TextArea rawArea = new TextArea("Raw Definition");
      rawArea.setValue(raw);
      rawArea.setReadOnly(true);
      rawArea.setWidthFull();
      rawArea.setHeight("140px");
      // Use monospace and preserve whitespace
      rawArea.getStyle().set("font-family", "monospace");
      rawArea.getElement().getStyle().set("white-space", "pre");
      details.add(rawArea);
    }
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
      showError("No server selected");
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

    formLayout.add(nameField, oidField, descriptionField, typeComboBox, obsoleteCheckbox,
        superiorClassesSelector, requiredAttributesSelector, optionalAttributesSelector,
        schemaFileField);

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
      showError("No server selected");
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

    formLayout.add(nameField, oidField, descriptionField, syntaxOidSelector,
        superiorTypeSelector, usageComboBox, singleValuedCheckbox, obsoleteCheckbox,
        collectiveCheckbox, noUserModificationCheckbox, schemaFileField);

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
        showError("Name is required");
        nameField.focus();
        return false;
      }
      if (oidField.getValue() == null || oidField.getValue().trim().isEmpty()) {
        showError("OID is required");
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
            showError("An object class with this OID already exists on " + serverName);
            oidField.focus();
            return false;
          }
          if (schema.getObjectClass(nameField.getValue()) != null) {
            showError("An object class with this name already exists on " + serverName);
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

      // Reload schema
      loadSchemas();
      
      // Show appropriate message
      if (successCount > 0 && failCount == 0) {
        showSuccess("Object class '" + nameField.getValue() + "' added successfully to " 
            + successCount + " server(s)");
      } else if (successCount > 0 && failCount > 0) {
        showError("Object class added to " + successCount + " server(s), but failed on: " 
            + failedServers.toString());
      } else {
        showError("Failed to add object class to all servers: " + failedServers.toString());
        return false;
      }
      
      return true;
    } catch (Exception e) {
      showError("Failed to add object class: " + e.getMessage());
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
        showError("Name is required");
        nameField.focus();
        return false;
      }
      if (oidField.getValue() == null || oidField.getValue().trim().isEmpty()) {
        showError("OID is required");
        oidField.focus();
        return false;
      }

      // Get syntax OID from selector (extract OID from description if present)
      String syntaxOid = extractOidOrName(syntaxOidSelector.getValue());
      if (syntaxOid == null || syntaxOid.trim().isEmpty()) {
        showError("Syntax OID is required");
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
            showError("An attribute type with this OID already exists on " + serverName);
            oidField.focus();
            return false;
          }
          if (schema.getAttributeType(nameField.getValue()) != null) {
            showError("An attribute type with this name already exists on " + serverName);
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
          successCount++;
        } catch (Exception e) {
          failCount++;
          if (failedServers.length() > 0) {
            failedServers.append(", ");
          }
          failedServers.append(serverName).append(" (").append(e.getMessage()).append(")");
        }
      }

      // Reload schema
      loadSchemas();

      // Show appropriate message based on results
      if (successCount > 0 && failCount == 0) {
        showSuccess("Attribute type '" + nameField.getValue()
            + "' added successfully to " + successCount + " server(s)");
        return true;
      } else if (successCount > 0 && failCount > 0) {
        showError("Attribute type '" + nameField.getValue() + "' added to " + successCount
            + " server(s), but failed on: " + failedServers);
        return true; // Partial success
      } else {
        showError("Failed to add attribute type to any server: " + failedServers);
        return false;
      }
    } catch (Exception e) {
      showError("Failed to add attribute type: " + e.getMessage());
      logger.error("Failed to add attribute type", e);
      return false;
    }
  }
}