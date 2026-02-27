package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.ldapbrowser.ui.utils.SchemaDetailDialogHelper;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.unboundid.ldap.sdk.schema.AttributeTypeDefinition;
import com.unboundid.ldap.sdk.schema.ObjectClassDefinition;
import com.unboundid.ldap.sdk.schema.Schema;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu;
import com.vaadin.flow.component.grid.contextmenu.GridMenuItem;
import com.vaadin.flow.component.grid.contextmenu.GridSubMenu;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component for editing LDAP entry attributes.
 * Provides full CRUD operations on entry attributes.
 */
public class EntryEditor extends VerticalLayout {

  private static final Logger logger = LoggerFactory.getLogger(EntryEditor.class);

  private final LdapService ldapService;
  private final ConfigurationService configService;

  private LdapServerConfig serverConfig;
  private LdapEntry currentEntry;
  private LdapEntry fullEntry;
  private boolean hasPendingChanges;
  
  // Track which attributes have been modified by the user
  private final java.util.Set<String> modifiedAttributes = new java.util.HashSet<>();
  
  // Track pending changes for visual indication
  private final Map<String, PendingChangeType> pendingChanges = new java.util.HashMap<>();
  
  /**
   * Types of pending changes for visual indication.
   */
  private enum PendingChangeType {
    ADDED,    // Value was added
    DELETED,  // Value was deleted
    MODIFIED  // Value was modified (replaced)
  }

  // Store original attribute values before modification for backout LDIF
  private final Map<String, List<String>> originalAttributeValues =
      new java.util.HashMap<>();

  // UI Components
  private Span dnLabel;
  private MenuBar copyMenuBar;
  private Button expandButton;
  private Button searchFromHereButton;
  private TextField searchField;
  private Button addAttributeButton;
  private Button pendingChangesButton;
  private Button testLoginButton;
  private Button refreshButton;
  private Button deleteEntryButton;
  private Button ldifButton;
  private Button showOperationalAttributesButton;
  private Button renameMoveButton;
  private boolean showOperationalAttributes = false;
  private Grid<AttributeRow> attributeGrid;
  private ListDataProvider<AttributeRow> attributeDataProvider;
  
  // Listener for expand action
  private Runnable expandListener;

  /**
   * Creates entry editor.
   *
   * @param ldapService LDAP service
   * @param configService configuration service
   */
  public EntryEditor(LdapService ldapService, ConfigurationService configService) {
    this.ldapService = ldapService;
    this.configService = configService;
    
    initializeComponents();
    setupLayout();
  }

  private void initializeComponents() {
    // DN label and copy button
    dnLabel = new Span("No entry selected");
    dnLabel.getStyle()
        .set("font-weight", "bold")
        .set("font-family", "monospace");

    // Create copy menu bar with dropdown options
    copyMenuBar = new MenuBar();
    copyMenuBar.addThemeVariants(com.vaadin.flow.component.menubar.MenuBarVariant.LUMO_TERTIARY);
    copyMenuBar.setEnabled(false);
    
    MenuItem copyMenuItem = copyMenuBar.addItem(new Icon(VaadinIcon.COPY));
    copyMenuItem.getElement().setAttribute("title", "Copy options");
    
    SubMenu copySubMenu = copyMenuItem.getSubMenu();
    copySubMenu.addItem("Copy DN", e -> copyDnToClipboard());
    copySubMenu.addItem("Copy Entry", e -> copyEntryToClipboard(false));
    copySubMenu.addItem("Copy Entry with Operational Attributes", 
        e -> copyEntryToClipboard(true));

    expandButton = new Button(new Icon(VaadinIcon.EXPAND_SQUARE));
    expandButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    expandButton.getElement().setAttribute("title", "Expand in dialog");
    expandButton.setEnabled(false);
    expandButton.addClickListener(e -> {
      if (expandListener != null) {
        expandListener.run();
      }
    });

    searchFromHereButton = new Button(new Icon(VaadinIcon.SEARCH));
    searchFromHereButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    searchFromHereButton.getElement().setAttribute("title", "Search from here");
    searchFromHereButton.setEnabled(false);
    searchFromHereButton.addClickListener(e -> searchFromCurrentEntry());

    // Operational attributes toggle button
    showOperationalAttributesButton = new Button(new Icon(VaadinIcon.EYE));
    showOperationalAttributesButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    showOperationalAttributesButton.getElement().setAttribute("title",
        "Show operational attributes");
    showOperationalAttributesButton.addClickListener(e -> {
      showOperationalAttributes = !showOperationalAttributes;
      if (showOperationalAttributes) {
        showOperationalAttributesButton.addThemeVariants(
            ButtonVariant.LUMO_PRIMARY);
        showOperationalAttributesButton.getElement().setAttribute("title",
            "Hide operational attributes");
      } else {
        showOperationalAttributesButton.removeThemeVariants(
            ButtonVariant.LUMO_PRIMARY);
        showOperationalAttributesButton.getElement().setAttribute("title",
            "Show operational attributes");
      }
      if (currentEntry != null && serverConfig != null) {
        refreshEntry();
      }
    });

    // Attribute grid
    attributeGrid = new Grid<>(AttributeRow.class, false);
    attributeGrid.setSizeFull();

    // Pending changes icon column (far left, no header)
    attributeGrid.addColumn(new ComponentRenderer<>(this::createPendingChangeIcon))
        .setHeader("")
        .setFlexGrow(0)
        .setWidth("50px")
        .setResizable(false);

    attributeGrid.addColumn(new ComponentRenderer<>(this::createAttributeNameComponent))
        .setHeader("Attribute")
        .setFlexGrow(1)
        .setResizable(true)
        .setSortable(true)
        .setComparator(AttributeRow::getName);

    attributeGrid.addColumn(new ComponentRenderer<>(this::createValueComponent))
        .setHeader("Value")
        .setFlexGrow(2)
        .setResizable(true);

    // Initialize context menu for attribute actions
    initializeAttributeContextMenu();

    // Action buttons - icon only with tooltips
    addAttributeButton = new Button(new Icon(VaadinIcon.PLUS));
    addAttributeButton.getElement().setAttribute("title", "Add attribute");
    addAttributeButton.addClickListener(e -> openAddAttributeDialog());

    pendingChangesButton = new Button(new Icon(VaadinIcon.CHECK));
    pendingChangesButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    pendingChangesButton.getElement().setAttribute("title", "Pending changes");
    pendingChangesButton.setEnabled(false);
    pendingChangesButton.addClickListener(e -> openPendingChangesDialog());

    testLoginButton = new Button(new Icon(VaadinIcon.KEY));
    testLoginButton.getElement().setAttribute("title", "Test login");
    testLoginButton.addClickListener(e -> openTestLoginDialog());

    refreshButton = new Button(new Icon(VaadinIcon.REFRESH));
    refreshButton.getElement().setAttribute("title", "Refresh");
    refreshButton.addClickListener(e -> refreshEntry());

    deleteEntryButton = new Button(new Icon(VaadinIcon.TRASH));
    deleteEntryButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
    deleteEntryButton.getElement().setAttribute("title", "Delete entry");
    deleteEntryButton.addClickListener(e -> confirmDeleteEntry());

    ldifButton = new Button(new Icon(VaadinIcon.FILE_TEXT_O));
    ldifButton.getElement().setAttribute("title", "LDIF");
    ldifButton.addClickListener(e -> openLdifDialog());

    renameMoveButton = new Button(new Icon(VaadinIcon.EXCHANGE));
    renameMoveButton.getElement().setAttribute("title", "Rename / Move");
    renameMoveButton.addClickListener(e -> openRenameMoveDialog());

    setButtonsEnabled(false);
    clearPendingChanges();
  }

  private void initializeAttributeContextMenu() {
    GridContextMenu<AttributeRow> contextMenu = new GridContextMenu<>(attributeGrid);
    
    contextMenu.setDynamicContentHandler(row -> {
      contextMenu.removeAll();
      
      if (row == null) {
        return false;
      }
      
      // Add Copy submenu at the top
      GridMenuItem<AttributeRow> copyMenuItem = contextMenu.addItem("Copy");
      GridSubMenu<AttributeRow> copySubMenu = copyMenuItem.getSubMenu();
      copySubMenu.addItem("Attribute Name", event -> copyAttributeName(row));
      copySubMenu.addItem("Value", event -> copyAttributeValue(row));
      copySubMenu.addItem("LDIF Name Value", event -> copyAttributeLdifFormat(row));
      copySubMenu.addItem("Search Filter", event -> copyAttributeSearchFilter(row));
      
      // Add Edit submenu with edit operations
      GridMenuItem<AttributeRow> editMenuItem = contextMenu.addItem("Edit");
      GridSubMenu<AttributeRow> editSubMenu = editMenuItem.getSubMenu();
      editSubMenu.addItem("Edit Value", event -> openEditValueDialog(row));
      editSubMenu.addItem("Add Value", event -> openAddValueDialog(row));
      editSubMenu.addItem("Delete Value", event -> deleteValue(row));
      editSubMenu.addItem("Delete All Values", event -> deleteAllValues(row));
      
      // Add schema detail menu items
      String attributeName = row.getName();
      if (attributeName != null && !attributeName.trim().isEmpty()) {
        if (attributeName.equalsIgnoreCase("objectClass")) {
          // For objectClass attributes, show object class details
          String objectClassName = row.getValue();
          if (objectClassName != null && !objectClassName.trim().isEmpty()) {
            contextMenu.addItem("View Object Class Type in Schema", 
                event -> showObjectClassInSchema(objectClassName));
          }
        } else {
          // For all other attributes, show attribute type details
          contextMenu.addItem("View Attribute Type in Schema", 
              event -> showAttributeTypeInSchema(attributeName));
        }
      }
      
      return true;
    });
  }

  /**
   * Initializes the context menu for DN label operations.
   */
  private void initializeDnContextMenu() {
    ContextMenu dnContextMenu = new ContextMenu();
    dnContextMenu.setTarget(dnLabel);
    dnContextMenu.setOpenOnClick(false);
    
    // Add Copy submenu
    MenuItem copyMenuItem = dnContextMenu.addItem("Copy");
    SubMenu copySubMenu = copyMenuItem.getSubMenu();
    copySubMenu.addItem("Copy DN", e -> copyDnToClipboard());
    copySubMenu.addItem("Copy Entry", e -> copyEntryToClipboard(false));
    copySubMenu.addItem("Copy Entry with Operational Attributes", 
        e -> copyEntryToClipboard(true));
    
    // Add Search from here option
    dnContextMenu.addItem("Search from here", e -> searchFromCurrentEntry());
  }

  private void setupLayout() {
    setSizeFull();
    setPadding(false);
    setSpacing(true);

    // Add context menu to DN label for DN-specific operations
    initializeDnContextMenu();

    // Header with DN label, DN value, expand button, and search field all on one row
    HorizontalLayout dnRow = new HorizontalLayout();
    dnRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    dnRow.setPadding(false);
    dnRow.setSpacing(true);
    dnRow.setWidthFull();
    
    // "DN:" label prefix
    Span dnPrefix = new Span("DN:");
    dnPrefix.getStyle()
        .set("font-weight", "bold")
        .set("white-space", "nowrap");
    
    // Search field for filtering attributes
    searchField = new TextField();
    searchField.setPlaceholder("Filter attributes...");
    searchField.setClearButtonVisible(true);
    searchField.setWidth("300px");
    searchField.setValueChangeMode(ValueChangeMode.EAGER);
    searchField.addValueChangeListener(e -> applyAttributeFilter());
    
    dnRow.add(dnPrefix, dnLabel, expandButton, searchField);
    dnRow.setFlexGrow(1, dnLabel);

    // Action buttons
    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    buttonLayout.setPadding(false);
    buttonLayout.setSpacing(true);
    buttonLayout.add(
        addAttributeButton,
        pendingChangesButton,
        testLoginButton,
        refreshButton,
        deleteEntryButton,
        ldifButton,
        renameMoveButton,
        showOperationalAttributesButton
    );

    add(dnRow, buttonLayout, attributeGrid);
    setFlexGrow(1, attributeGrid);
  }

  /**
   * Sets server configuration.
   *
   * @param serverConfig server config
   */
  public void setServerConfig(LdapServerConfig serverConfig) {
    this.serverConfig = serverConfig;
  }

  /**
   * Gets the current server configuration.
   *
   * @return the current server config
   */
  public LdapServerConfig getServerConfig() {
    return this.serverConfig;
  }

  /**
   * Gets the current entry being edited.
   *
   * @return the current LDAP entry
   */
  public LdapEntry getCurrentEntry() {
    return this.currentEntry;
  }

  /**
   * Sets the expand listener that will be called when the expand button is clicked.
   *
   * @param listener the runnable to execute when expand is clicked
   */
  public void setExpandListener(Runnable listener) {
    this.expandListener = listener;
  }

  /**
   * Edits an LDAP entry.
   *
   * @param entry entry to edit
   */
  public void editEntry(LdapEntry entry) {
    this.currentEntry = entry;
    this.fullEntry = entry;
    clearPendingChanges();

    if (entry != null) {
      dnLabel.setText(entry.getDn());
      
      // If operational attributes toggle is already on,
      // fetch entry with operational attributes
      if (showOperationalAttributes
          && serverConfig != null) {
        try {
          LdapEntry entryWithOperational = ldapService.readEntry(
              serverConfig,
              entry.getDn(),
              true
          );
          if (entryWithOperational != null) {
            entryWithOperational.setServerName(entry.getServerName());
            this.fullEntry = entryWithOperational;
          }
        } catch (Exception e) {
          logger.debug("Failed to fetch operational attributes: {}", e.getMessage());
          // Continue with the entry we have
        }
      }
      
      refreshAttributeDisplay();
      setButtonsEnabled(true);
      expandButton.setEnabled(true);
      showOperationalAttributesButton.setEnabled(true);
    } else {
      clear();
    }
  }

  /**
   * Clears the editor.
   */
  public void clear() {
    currentEntry = null;
    fullEntry = null;
    clearPendingChanges();
    dnLabel.setText("No entry selected");
    if (attributeDataProvider != null) {
      attributeDataProvider.getItems().clear();
      attributeDataProvider.refreshAll();
    } else {
      attributeGrid.setItems(Collections.emptyList());
    }
    setButtonsEnabled(false);
    expandButton.setEnabled(false);
    showOperationalAttributes = false;
    showOperationalAttributesButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
    showOperationalAttributesButton.getElement().setAttribute("title",
        "Show operational attributes");
    searchField.clear();
  }

  private void refreshAttributeDisplay() {
    if (currentEntry == null) {
      attributeGrid.setItems(Collections.emptyList());
      return;
    }

    List<AttributeRow> rows = new ArrayList<>();
    boolean showOperational = showOperationalAttributes;

    // Collect attributes with their values in sorted order
    List<Map.Entry<String, List<String>>> sortedAttrs = new ArrayList<>();
    
    // Add regular attributes from currentEntry (which includes pending changes)
    for (Map.Entry<String, List<String>> attr : currentEntry.getAttributes().entrySet()) {
      String attrName = attr.getKey();
      
      // Filter operational attributes if checkbox is unchecked
      if (!showOperational && isOperationalAttribute(attrName)) {
        continue;
      }

      sortedAttrs.add(attr);
    }
    
    // Add operational attributes if checkbox is checked
    // Use fullEntry for operational attributes since they are read-only
    if (showOperational && fullEntry != null
        && fullEntry.getOperationalAttributes() != null) {
      for (Map.Entry<String, List<String>> attr : fullEntry.getOperationalAttributes().entrySet()) {
        sortedAttrs.add(attr);
      }
    }

    // Sort attributes: by classification (required, optional, operational), then alphabetically
    sortedAttrs.sort((attr1, attr2) -> {
      String name1 = attr1.getKey();
      String name2 = attr2.getKey();

      // Get classifications for both attributes
      AttributeClassification class1 = classifyAttribute(name1);
      AttributeClassification class2 = classifyAttribute(name2);

      // Compare by classification first
      int classCompare = Integer.compare(class1.ordinal(), class2.ordinal());
      if (classCompare != 0) {
        return classCompare;
      }

      // Within same classification, sort alphabetically
      return name1.compareToIgnoreCase(name2);
    });

    // Create one row per attribute name/value pair
    for (Map.Entry<String, List<String>> attr : sortedAttrs) {
      String attrName = attr.getKey();
      List<String> values = attr.getValue();
      
      // Sort objectClass values alphabetically
      if ("objectClass".equalsIgnoreCase(attrName)) {
        values = new ArrayList<>(values);
        values.sort(String.CASE_INSENSITIVE_ORDER);
      }
      
      // Create a row for each value
      for (int i = 0; i < values.size(); i++) {
        boolean isFirst = (i == 0);
        rows.add(new AttributeRow(attrName, values.get(i), i, isFirst));
      }
    }

    attributeDataProvider = new ListDataProvider<>(rows);
    attributeGrid.setDataProvider(attributeDataProvider);
    applyAttributeFilter();
  }

  /**
   * Determines if an attribute is operational based on its schema definition.
   * An attribute is operational if its usage is directoryOperation, dSAOperation,
   * or distributedOperation (anything other than userApplications).
   *
   * @param attributeName the attribute name to check
   * @return true if the attribute is operational according to schema
   */
  private boolean isOperationalAttribute(String attributeName) {
    if (serverConfig == null) {
      return false;
    }
    
    try {
      Schema schema = ldapService.getSchema(serverConfig);
      if (schema == null) {
        return false;
      }
      
      AttributeTypeDefinition attrDef = schema.getAttributeType(attributeName);
      if (attrDef == null) {
        return false;
      }
      
      // Check usage - operational attributes have usage
      // other than "userApplications"
      if (attrDef.getUsage() != null) {
        String usage = attrDef.getUsage().getName();
        // Operational attributes have usage: directoryOperation,
        // dSAOperation, or distributedOperation
        return !usage.equalsIgnoreCase("userApplications");
      }
      
      return false;
    } catch (Exception e) {
      logger.debug("Failed to determine operational status for attribute {}: {}", 
          attributeName, e.getMessage());
      return false;
    }
  }

  /**
   * Creates a pending change icon for the attribute row.
   *
   * @param row the attribute row
   * @return the icon component showing pending changes
   */
  private Span createPendingChangeIcon(AttributeRow row) {
    String changeKey = row.getName() + ":" + row.getValue();
    PendingChangeType changeType = pendingChanges.get(changeKey);

    if (changeType == null) {
      return new Span();
    }

    Span iconSpan = new Span();
    Icon icon;

    if (changeType == PendingChangeType.ADDED) {
      icon = new Icon(VaadinIcon.PLUS_CIRCLE);
      icon.setColor("green");
      icon.getElement().setAttribute("title", "Value will be added");
    } else if (changeType == PendingChangeType.MODIFIED) {
      icon = new Icon(VaadinIcon.EDIT);
      icon.setColor("#1565c0");
      icon.getElement().setAttribute(
          "title", "Value will be modified");
    } else { // DELETED
      icon = new Icon(VaadinIcon.MINUS_CIRCLE);
      icon.setColor("red");
      icon.getElement().setAttribute("title", "Value will be deleted");
    }

    iconSpan.add(icon);
    return iconSpan;
  }
  
  private Span createAttributeNameComponent(AttributeRow row) {
    // Only show attribute name for the first value of each attribute
    if (!row.isFirstValueOfAttribute()) {
      return new Span();
    }
    
    Span nameSpan = new Span(row.getName());
    nameSpan.getStyle().set("font-weight", "500");

    // Get attribute classification from schema
    AttributeClassification classification = classifyAttribute(row.getName());
    
    // Color code based on schema classification
    switch (classification) {
      case REQUIRED:
        nameSpan.getStyle().set("color", "#d32f2f"); // Red for required
        nameSpan.getElement().setAttribute("title", "Required attribute");
        break;
      case OPTIONAL:
        nameSpan.getStyle().set("color", "#1976d2"); // Blue for optional
        nameSpan.getElement().setAttribute("title", "Optional attribute");
        break;
      case OPERATIONAL:
        nameSpan.getStyle().set("color", "#f57c00"); // Orange for operational
        nameSpan.getElement().setAttribute("title", "Operational attribute");
        break;
      case UNKNOWN:
      default:
        // Default color for unknown attributes
        nameSpan.getElement().setAttribute("title", "Attribute");
        break;
    }

    return nameSpan;
  }
  
  /**
   * Classification of LDAP attributes based on schema.
   */
  private enum AttributeClassification {
    REQUIRED,    // Required by one or more object classes
    OPTIONAL,    // Optional (allowed but not required)
    OPERATIONAL, // Operational/system attribute
    UNKNOWN      // Not found in schema or no schema available
  }
  
  /**
   * Classifies an attribute based on the server's schema.
   *
   * @param attributeName the attribute name to classify
   * @return the classification of the attribute
   */
  private AttributeClassification classifyAttribute(String attributeName) {
    // First check if it's a known operational attribute
    if (isOperationalAttribute(attributeName)) {
      return AttributeClassification.OPERATIONAL;
    }
    
    // If no server config, return unknown
    if (serverConfig == null || currentEntry == null) {
      return AttributeClassification.UNKNOWN;
    }
    
    try {
      // Get the schema from cache
      Schema schema = ldapService.getSchema(serverConfig);
      if (schema == null) {
        return AttributeClassification.UNKNOWN;
      }
      
      // Check if the attribute type is operational in the schema
      AttributeTypeDefinition attrType = schema.getAttributeType(attributeName);
      if (attrType != null && attrType.isOperational()) {
        return AttributeClassification.OPERATIONAL;
      }
      
      // Get the object classes for this entry
      List<String> objectClasses = currentEntry.getAttributeValues("objectClass");
      if (objectClasses == null || objectClasses.isEmpty()) {
        return AttributeClassification.UNKNOWN;
      }
      
      // Build sets of required and optional attributes from all object classes
      Set<String> requiredAttributes = new HashSet<>();
      Set<String> optionalAttributes = new HashSet<>();
      
      for (String ocName : objectClasses) {
        ObjectClassDefinition oc = schema.getObjectClass(ocName);
        if (oc != null) {
          // Add required attributes
          String[] required = oc.getRequiredAttributes();
          if (required != null) {
            for (String attr : required) {
              requiredAttributes.add(attr.toLowerCase());
            }
          }
          
          // Add optional attributes
          String[] optional = oc.getOptionalAttributes();
          if (optional != null) {
            for (String attr : optional) {
              optionalAttributes.add(attr.toLowerCase());
            }
          }
        }
      }
      
      // Check if this attribute is required or optional
      String lowerAttrName = attributeName.toLowerCase();
      if (requiredAttributes.contains(lowerAttrName)) {
        return AttributeClassification.REQUIRED;
      } else if (optionalAttributes.contains(lowerAttrName)) {
        return AttributeClassification.OPTIONAL;
      }
      
    } catch (Exception e) {
      logger.debug("Error classifying attribute {}: {}", attributeName, e.getMessage());
    }
    
    return AttributeClassification.UNKNOWN;
  }

  private Span createValueComponent(AttributeRow row) {
    Span valueSpan = new Span(row.getValue());
    
    if (row.getValue().length() > 50) {
      valueSpan.getStyle().set("font-size", "smaller");
    }

    // Apply visual highlighting for pending changes
    // Use value in key to handle sorted attributes (e.g., objectClass)
    String changeKey = row.getName() + ":" + row.getValue();
    PendingChangeType changeType = pendingChanges.get(changeKey);
    
    if (changeType == PendingChangeType.DELETED) {
      // Red background for deleted values
      valueSpan.getStyle()
          .set("background-color", "#ffebee")
          .set("color", "#c62828")
          .set("padding", "4px 8px")
          .set("border-radius", "4px")
          .set("text-decoration", "line-through");
    } else if (changeType == PendingChangeType.ADDED) {
      // Green background for added values
      valueSpan.getStyle()
          .set("background-color", "#e8f5e9")
          .set("color", "#2e7d32")
          .set("padding", "4px 8px")
          .set("border-radius", "4px");
    } else if (changeType == PendingChangeType.MODIFIED) {
      // Blue background for modified values
      valueSpan.getStyle()
          .set("background-color", "#e3f2fd")
          .set("color", "#1565c0")
          .set("padding", "4px 8px")
          .set("border-radius", "4px");
    }

    return valueSpan;
  }

  private void openAddAttributeDialog() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Add Attribute");

    // Get valid attributes from schema
    List<String> validAttributes = getValidAttributesFromSchema();
    
    ComboBox<String> nameField = new ComboBox<>("Attribute Name");
    nameField.setWidthFull();
    nameField.setItems(validAttributes);
    nameField.setAllowCustomValue(true);
    nameField.addCustomValueSetListener(event -> {
      String customValue = event.getDetail();
      if (customValue != null && !customValue.trim().isEmpty()) {
        nameField.setValue(customValue);
      }
    });
    nameField.setPlaceholder("Select or type attribute name");

    TextArea valueArea = new TextArea("Values (one per line)");
    valueArea.setWidthFull();
    valueArea.setHeight("200px");

    Button saveButton = new Button("Add", e -> {
      String name = nameField.getValue();
      String valuesText = valueArea.getValue();

      if (name == null || name.trim().isEmpty()) {
        NotificationHelper.showError("Attribute name is required.");
        return;
      }

      if (valuesText == null || valuesText.trim().isEmpty()) {
        NotificationHelper.showError("At least one value is required.");
        return;
      }

      List<String> values = Arrays.asList(valuesText.split("\n"));
      values.replaceAll(String::trim);
      values.removeIf(String::isEmpty);

      // Update or add the attribute
      currentEntry.getAttributes().put(name.trim(), new ArrayList<>(values));
      
      // Track all values as pending additions
      for (String value : values) {
        String changeKey = name.trim() + ":" + value;
        pendingChanges.put(changeKey, PendingChangeType.ADDED);
      }
      
      modifiedAttributes.add(name.trim());
      markPendingChanges();
      refreshAttributeDisplay();
      NotificationHelper.showSuccess(
          "Added attribute '" + name.trim() + "' with " + values.size()
              + " value(s).");
      dialog.close();
    });
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    VerticalLayout layout = new VerticalLayout(nameField, valueArea);
    layout.setWidth("400px");
    dialog.add(layout);
    dialog.getFooter().add(cancelButton, saveButton);
    dialog.open();
  }
  
  /**
   * Gets list of valid attributes from schema based on entry's objectClasses.
   *
   * @return list of valid attribute names
   */
  private List<String> getValidAttributesFromSchema() {
    List<String> validAttributes = new ArrayList<>();
    
    if (serverConfig == null || currentEntry == null) {
      return validAttributes;
    }
    
    try {
      Schema schema = ldapService.getSchema(serverConfig);
      if (schema == null) {
        return validAttributes;
      }
      
      List<String> objectClasses = currentEntry.getAttributeValues("objectClass");
      if (objectClasses == null || objectClasses.isEmpty()) {
        return validAttributes;
      }
      
      Set<String> attributeSet = new HashSet<>();
      
      // Collect all MUST and MAY attributes from all objectClasses
      for (String ocName : objectClasses) {
        ObjectClassDefinition oc = schema.getObjectClass(ocName);
        if (oc != null) {
          // Add required attributes
          String[] required = oc.getRequiredAttributes();
          if (required != null) {
            for (String attr : required) {
              attributeSet.add(attr);
            }
          }
          
          // Add optional attributes
          String[] optional = oc.getOptionalAttributes();
          if (optional != null) {
            for (String attr : optional) {
              attributeSet.add(attr);
            }
          }
        }
      }
      
      // Convert to sorted list
      validAttributes.addAll(attributeSet);
      validAttributes.sort(String.CASE_INSENSITIVE_ORDER);
      
    } catch (Exception e) {
      logger.debug("Error getting valid attributes from schema: {}", e.getMessage());
    }
    
    return validAttributes;
  }

  private void openEditValueDialog(AttributeRow row) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Edit Value: " + row.getName());

    TextField valueField = new TextField("Value");
    valueField.setWidthFull();
    valueField.setValue(row.getValue() != null ? row.getValue() : "");

    Button saveButton = new Button("Save", e -> {
      String newValue = valueField.getValue();
      
      if (newValue == null || newValue.trim().isEmpty()) {
        NotificationHelper.showError("Value cannot be empty.");
        return;
      }

      List<String> allValues = currentEntry.getAttributeValues(row.getName());
      if (allValues != null && row.getValueIndex() < allValues.size()) {
        // Save original values before first modification
        if (!originalAttributeValues.containsKey(row.getName())) {
          originalAttributeValues.put(
              row.getName(), new ArrayList<>(allValues));
        }
        allValues.set(row.getValueIndex(), newValue.trim());
        currentEntry.getAttributes().put(
            row.getName(), new ArrayList<>(allValues));

        // Track the new value as MODIFIED for highlighting
        String changeKey = row.getName() + ":" + newValue.trim();
        pendingChanges.put(changeKey, PendingChangeType.MODIFIED);

        modifiedAttributes.add(row.getName());
        markPendingChanges();
        refreshAttributeDisplay();
        NotificationHelper.showSuccess(
            "Pending change: updated value for '"
                + row.getName() + "'.");
        dialog.close();
      } else {
        NotificationHelper.showError("Unable to update value.");
      }
    });
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    VerticalLayout layout = new VerticalLayout(valueField);
    layout.setWidth("400px");
    dialog.add(layout);
    dialog.getFooter().add(cancelButton, saveButton);
    dialog.open();
  }

  private void openAddValueDialog(AttributeRow row) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Add Value: " + row.getName());

    TextField valueField = new TextField("New Value");
    valueField.setWidthFull();
    valueField.setPlaceholder("Enter new value...");

    Button addButton = new Button("Add", e -> {
      String newValue = valueField.getValue();
      
      if (newValue == null || newValue.trim().isEmpty()) {
        NotificationHelper.showError("Value cannot be empty.");
        return;
      }

      List<String> allValues = currentEntry.getAttributeValues(row.getName());
      if (allValues == null) {
        allValues = new ArrayList<>();
      } else {
        allValues = new ArrayList<>(allValues);
      }
      
      // Check for duplicate values
      if (allValues.contains(newValue.trim())) {
        NotificationHelper.showWarning("This value already exists for '" + row.getName() + "'.");
        return;
      }
      
      allValues.add(newValue.trim());
      currentEntry.getAttributes().put(row.getName(), allValues);
      
      // Track this as a pending addition using the value itself
      String changeKey = row.getName() + ":" + newValue.trim();
      pendingChanges.put(changeKey, PendingChangeType.ADDED);
      
      modifiedAttributes.add(row.getName());
      markPendingChanges();
      refreshAttributeDisplay();
      NotificationHelper.showSuccess("Added new value to '" + row.getName() + "'.");
      dialog.close();
    });
    addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    VerticalLayout layout = new VerticalLayout(valueField);
    layout.setWidth("400px");
    dialog.add(layout);
    dialog.getFooter().add(cancelButton, addButton);
    dialog.open();
  }

  private void deleteValue(AttributeRow row) {
    List<String> allValues = currentEntry.getAttributeValues(row.getName());
    
    if (allValues == null || row.getValueIndex() >= allValues.size()) {
      NotificationHelper.showError("Unable to delete value.");
      return;
    }

    // Directly mark as pending deletion without dialog
    String changeKey = row.getName() + ":" + row.getValue();
    pendingChanges.put(changeKey, PendingChangeType.DELETED);
    
    modifiedAttributes.add(row.getName());
    markPendingChanges();
    refreshAttributeDisplay();
    NotificationHelper.showSuccess(
        "Marked value for deletion from '" + row.getName() + "'.");
  }

  private void deleteAllValues(AttributeRow row) {
    ConfirmDialog dialog = new ConfirmDialog();
    dialog.setHeader("Delete All Values");
    dialog.setText("Are you sure you want to delete all values for the attribute '"
        + row.getName() + "'?");
    dialog.setCancelable(true);
    dialog.setConfirmText("Delete");
    dialog.addConfirmListener(e -> {
      // Mark all values as deleted (keep them visible in grid)
      List<String> allValues = currentEntry.getAttributes().get(row.getName());
      if (allValues != null) {
        for (String value : allValues) {
          String changeKey = row.getName() + ":" + value;
          pendingChanges.put(changeKey, PendingChangeType.DELETED);
        }
      }
      modifiedAttributes.add(row.getName());
      markPendingChanges();
      refreshAttributeDisplay();
      NotificationHelper.showSuccess("Deleted all values for '" + row.getName() + "'.");
    });
    dialog.open();
  }

  private void saveChanges() {
    if (currentEntry == null || serverConfig == null) {
      return;
    }

    try {
      // Reload original entry to compare (without operational attributes)
      LdapEntry originalEntry = ldapService.readEntry(serverConfig, currentEntry.getDn(), false);
      if (originalEntry == null) {
        NotificationHelper.showError("Could not load original entry for comparison.");
        return;
      }

      List<Modification> modifications = createModifications(originalEntry, currentEntry);
      if (modifications.isEmpty()) {
        NotificationHelper.showInfo("No changes to save.");
        return;
      }

      // Apply modifications
      for (Modification mod : modifications) {
        if (mod.getModificationType() == ModificationType.DELETE) {
          ldapService.deleteAttribute(serverConfig, currentEntry.getDn(), mod.getAttributeName());
        } else if (mod.getModificationType() == ModificationType.ADD) {
          ldapService.addAttribute(
              serverConfig,
              currentEntry.getDn(),
              mod.getAttributeName(),
              Arrays.asList(mod.getValues())
          );
        } else if (mod.getModificationType() == ModificationType.REPLACE) {
          ldapService.modifyAttribute(
              serverConfig,
              currentEntry.getDn(),
              mod.getAttributeName(),
              Arrays.asList(mod.getValues())
          );
        }
      }

      clearPendingChanges();
      NotificationHelper.showModifySuccess("Entry saved successfully.");
      refreshEntry();
    } catch (Exception e) {
      NotificationHelper.showError("Failed to save entry: " + e.getMessage());
      logger.error("Failed to save entry", e);
    }
  }

  private List<Modification> createModifications(LdapEntry original, LdapEntry modified) {
    List<Modification> modifications = new ArrayList<>();

    // Only create modifications for attributes that were explicitly changed by the user
    for (String attrName : modifiedAttributes) {
      List<String> originalValues = original.getAttributeValues(attrName);
      List<String> modifiedValues = new ArrayList<>(modified.getAttributeValues(attrName));
      
      // Filter out values marked as DELETED from modifiedValues
      modifiedValues.removeIf(value -> {
        String changeKey = attrName + ":" + value;
        return pendingChanges.get(changeKey) == PendingChangeType.DELETED;
      });

      if (modifiedValues.isEmpty()) {
        // Attribute was deleted
        modifications.add(new Modification(ModificationType.DELETE, attrName));
      } else if (originalValues.isEmpty()) {
        // New attribute was added
        modifications.add(
            new Modification(
                ModificationType.ADD,
                attrName,
                modifiedValues.toArray(new String[0])
            )
        );
      } else if (!originalValues.equals(modifiedValues)) {
        // Existing attribute was modified
        modifications.add(
            new Modification(
                ModificationType.REPLACE,
                attrName,
                modifiedValues.toArray(new String[0])
            )
        );
      }
    }

    return modifications;
  }

  private void refreshEntry() {
    if (currentEntry == null || serverConfig == null) {
      return;
    }

    try {
      LdapEntry refreshedEntry = ldapService.readEntry(
          serverConfig,
          currentEntry.getDn(),
          showOperationalAttributes
      );
      
      if (refreshedEntry != null) {
        refreshedEntry.setServerName(currentEntry.getServerName());
        editEntry(refreshedEntry);
        clearPendingChanges();
        NotificationHelper.showInfo("Entry refreshed.");
      } else {
        NotificationHelper.showError("Entry not found.");
        clear();
      }
    } catch (Exception e) {
      NotificationHelper.showError("Failed to refresh entry: " + e.getMessage());
      logger.error("Failed to refresh entry", e);
    }
  }

  private void confirmDeleteEntry() {
    if (currentEntry == null) {
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Delete Entry");

    Span text = new Span("Are you sure you want to delete this entry?");
    Span dnText = new Span("DN: " + currentEntry.getDn());
    dnText.getStyle()
        .set("font-family", "monospace")
        .set("font-weight", "bold")
        .set("word-break", "break-all");

    VerticalLayout layout = new VerticalLayout(text, dnText);
    layout.setPadding(false);
    layout.setSpacing(true);
    dialog.add(layout);

    Button deleteButton = new Button("Delete", new Icon(VaadinIcon.TRASH));
    deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR,
        ButtonVariant.LUMO_PRIMARY);
    deleteButton.addClickListener(e -> {
      dialog.close();
      deleteEntry();
    });

    Button ldifBtn = new Button("LDIF", new Icon(VaadinIcon.FILE_TEXT_O));
    ldifBtn.addClickListener(e -> {
      dialog.close();
      openDeleteEntryLdifDialog();
    });

    Button cancelButton = new Button("Cancel", ev -> dialog.close());

    dialog.getFooter().add(cancelButton, ldifBtn, deleteButton);
    dialog.open();
  }

  private void deleteEntry() {
    if (currentEntry == null || serverConfig == null) {
      return;
    }

    try {
      ldapService.deleteEntry(serverConfig, currentEntry.getDn());
      NotificationHelper.showSuccess("Entry deleted successfully.");
      clear();
    } catch (Exception e) {
      NotificationHelper.showError("Failed to delete entry: " + e.getMessage());
      logger.error("Failed to delete entry", e);
    }
  }

  private void openTestLoginDialog() {
    if (currentEntry == null || serverConfig == null) {
      NotificationHelper.showError("No entry selected or server not configured.");
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Test LDAP Authentication");
    dialog.setWidth("400px");

    Span infoText = new Span("Enter password to test authentication for:");
    infoText.getStyle().set("margin-bottom", "10px");

    Span dnText = new Span(currentEntry.getDn());
    dnText.getStyle()
        .set("font-family", "monospace")
        .set("font-weight", "bold")
        .set("word-break", "break-all")
        .set("margin-bottom", "15px");

    PasswordField passwordField = new PasswordField("Password");
    passwordField.setWidthFull();
    passwordField.setPlaceholder("Enter password for authentication test");

    Span resultDiv = new Span();
    resultDiv.getStyle().set("margin-top", "10px");

    Button testButton = new Button("Test", new Icon(VaadinIcon.PLAY));
    testButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    testButton.addClickListener(e -> {
      String password = passwordField.getValue();
      
      if (password == null || password.trim().isEmpty()) {
        NotificationHelper.showError("Password is required for authentication test.");
        return;
      }

      try {
        boolean success = ldapService.testBind(serverConfig, currentEntry.getDn(), password);
        
        resultDiv.removeAll();
        
        if (success) {
          Icon successIcon = new Icon(VaadinIcon.CHECK_CIRCLE);
          successIcon.getStyle().set("color", "var(--lumo-success-color)");
          Span successText = new Span("Authentication successful");
          successText.getStyle().set("color", "var(--lumo-success-text-color)");

          HorizontalLayout successLayout = new HorizontalLayout(successIcon, successText);
          successLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
          successLayout.getStyle()
              .set("padding", "10px")
              .set("background", "var(--lumo-success-color-10pct)")
              .set("border-radius", "var(--lumo-border-radius-m)");

          resultDiv.add(successLayout);
        } else {
          Icon errorIcon = new Icon(VaadinIcon.CLOSE_CIRCLE);
          errorIcon.getStyle().set("color", "var(--lumo-error-color)");
          Span errorText = new Span("Authentication failed");
          errorText.getStyle().set("color", "var(--lumo-error-text-color)");

          HorizontalLayout errorLayout = new HorizontalLayout(errorIcon, errorText);
          errorLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
          errorLayout.getStyle()
              .set("padding", "10px")
              .set("background", "var(--lumo-error-color-10pct)")
              .set("border-radius", "var(--lumo-border-radius-m)");

          resultDiv.add(errorLayout);
        }

        passwordField.clear();
      } catch (Exception ex) {
        NotificationHelper.showError("Unexpected error: " + ex.getMessage());
        logger.error("Test bind error", ex);
      }
    });

    Button cancelButton = new Button("Close", e -> dialog.close());

    VerticalLayout layout = new VerticalLayout(infoText, dnText, passwordField, resultDiv);
    layout.setPadding(false);
    layout.setSpacing(true);

    dialog.add(layout);
    dialog.getFooter().add(cancelButton, testButton);
    dialog.open();
  }

  private void copyDnToClipboard() {
    if (currentEntry == null || currentEntry.getDn() == null) {
      NotificationHelper.showInfo("No DN to copy.");
      return;
    }

    String dn = currentEntry.getDn();
    getUI().ifPresent(ui -> {
      ui.getPage().executeJs("navigator.clipboard.writeText($0)", dn);
    });

    NotificationHelper.showSuccess("DN copied to clipboard");
  }

  private void copyEntryToClipboard(boolean includeOperational) {
    if (currentEntry == null || serverConfig == null) {
      NotificationHelper.showInfo("No entry to copy.");
      return;
    }

    try {
      // Fetch the entry with or without operational attributes
      LdapEntry entryToCopy = ldapService.readEntry(
          serverConfig,
          currentEntry.getDn(),
          includeOperational
      );

      if (entryToCopy == null) {
        NotificationHelper.showError("Failed to fetch entry for copying.");
        return;
      }

      // Format the entry as LDIF-style text
      StringBuilder ldifText = new StringBuilder();
      ldifText.append("dn: ").append(entryToCopy.getDn()).append("\n");

      // Sort regular attributes for consistent output
      List<String> attributeNames = new ArrayList<>(entryToCopy.getAttributes().keySet());
      attributeNames.sort(String.CASE_INSENSITIVE_ORDER);

      for (String attrName : attributeNames) {
        // Skip operational attributes if not requested
        if (!includeOperational && isOperationalAttribute(attrName)) {
          continue;
        }

        List<String> values = entryToCopy.getAttributeValues(attrName);
        if (values != null) {
          for (String value : values) {
            ldifText.append(attrName).append(": ").append(value).append("\n");
          }
        }
      }
      
      // Add operational attributes if requested
      if (includeOperational && entryToCopy.getOperationalAttributes() != null) {
        List<String> operationalNames = 
            new ArrayList<>(entryToCopy.getOperationalAttributes().keySet());
        operationalNames.sort(String.CASE_INSENSITIVE_ORDER);
        
        for (String attrName : operationalNames) {
          List<String> values = entryToCopy.getOperationalAttributes().get(attrName);
          if (values != null) {
            for (String value : values) {
              ldifText.append(attrName).append(": ").append(value).append("\n");
            }
          }
        }
      }

      // Copy to clipboard
      String textToCopy = ldifText.toString();
      getUI().ifPresent(ui -> {
        ui.getPage().executeJs("navigator.clipboard.writeText($0)", textToCopy);
      });

      String message = includeOperational 
          ? "Entry copied to clipboard (with operational attributes)" 
          : "Entry copied to clipboard";
      NotificationHelper.showSuccess(message);

    } catch (Exception e) {
      NotificationHelper.showError("Failed to copy entry: " + e.getMessage());
      logger.error("Failed to copy entry", e);
    }
  }

  /**
   * Copies the attribute value to clipboard.
   *
   * @param row the attribute row
   */
  private void copyAttributeValue(AttributeRow row) {
    if (row == null || row.getValue() == null) {
      NotificationHelper.showInfo("No value to copy.");
      return;
    }

    String value = row.getValue();
    getUI().ifPresent(ui -> {
      ui.getPage().executeJs("navigator.clipboard.writeText($0)", value);
    });

    NotificationHelper.showSuccess("Value copied to clipboard");
  }

  /**
   * Copies the attribute name to clipboard.
   *
   * @param row the attribute row
   */
  private void copyAttributeName(AttributeRow row) {
    if (row == null || row.getName() == null) {
      NotificationHelper.showInfo("No attribute name to copy.");
      return;
    }

    String name = row.getName();
    getUI().ifPresent(ui -> {
      ui.getPage().executeJs("navigator.clipboard.writeText($0)", name);
    });

    NotificationHelper.showSuccess("Attribute name copied to clipboard");
  }

  /**
   * Copies the attribute in LDIF format (name: value) to clipboard.
   *
   * @param row the attribute row
   */
  private void copyAttributeLdifFormat(AttributeRow row) {
    if (row == null || row.getName() == null || row.getValue() == null) {
      NotificationHelper.showInfo("No attribute to copy.");
      return;
    }

    String ldifText = row.getName() + ": " + row.getValue();
    getUI().ifPresent(ui -> {
      ui.getPage().executeJs("navigator.clipboard.writeText($0)", ldifText);
    });

    NotificationHelper.showSuccess("LDIF format copied to clipboard");
  }

  /**
   * Copies the attribute as a search filter (name=value) to clipboard.
   *
   * @param row the attribute row
   */
  private void copyAttributeSearchFilter(AttributeRow row) {
    if (row == null || row.getName() == null || row.getValue() == null) {
      NotificationHelper.showInfo("No attribute to copy.");
      return;
    }

    String searchFilter = "(" + row.getName() + "=" + row.getValue() + ")";
    getUI().ifPresent(ui -> {
      ui.getPage().executeJs("navigator.clipboard.writeText($0)", searchFilter);
    });

    NotificationHelper.showSuccess("Search filter copied to clipboard");
  }

  private void searchFromCurrentEntry() {
    if (currentEntry == null || currentEntry.getDn() == null) {
      NotificationHelper.showInfo("No entry selected.");
      return;
    }

    String dn = currentEntry.getDn();
    getUI().ifPresent(ui -> {
      ui.navigate("search", 
          com.vaadin.flow.router.QueryParameters.simple(
              java.util.Map.of("searchBase", dn)
          )
      );
    });
  }

  /**
   * Applies the search filter to the attribute grid.
   */
  private void applyAttributeFilter() {
    if (attributeDataProvider == null) {
      return;
    }
    
    String filterText = searchField.getValue();
    if (filterText == null || filterText.trim().isEmpty()) {
      attributeDataProvider.clearFilters();
    } else {
      String lowerCaseFilter = filterText.toLowerCase().trim();
      attributeDataProvider.setFilter(row -> 
          (row.getName() != null && row.getName().toLowerCase().contains(lowerCaseFilter))
          || (row.getValue() != null && row.getValue().toLowerCase().contains(lowerCaseFilter))
      );
    }
  }

  private void setButtonsEnabled(boolean enabled) {
    addAttributeButton.setEnabled(enabled);
    testLoginButton.setEnabled(enabled);
    refreshButton.setEnabled(enabled);
    deleteEntryButton.setEnabled(enabled);
    ldifButton.setEnabled(enabled);
    renameMoveButton.setEnabled(enabled);
    showOperationalAttributesButton.setEnabled(enabled);
    // pendingChangesButton is enabled/disabled based on pending state
    if (!enabled) {
      pendingChangesButton.setEnabled(false);
    }
  }

  private void markPendingChanges() {
    hasPendingChanges = true;
    pendingChangesButton.setEnabled(true);
    pendingChangesButton.getStyle().set("font-weight", "bold");
  }

  private void clearPendingChanges() {
    hasPendingChanges = false;
    modifiedAttributes.clear();
    pendingChanges.clear();
    originalAttributeValues.clear();
    pendingChangesButton.setEnabled(false);
    pendingChangesButton.getStyle().remove("font-weight");
  }

  /**
   * Opens the Pending Changes dialog showing changes in LDIF format
   * with a Commit Changes button.
   */
  private void openPendingChangesDialog() {
    if (currentEntry == null || serverConfig == null || !hasPendingChanges) {
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Pending Changes");
    dialog.setWidth("600px");

    // Build LDIF for pending changes
    String changeLdif = buildPendingChangesLdif();
    String reverseLdif = buildPendingChangesReverseLdif();

    // Pending changes LDIF
    Span changesLabel = new Span("Pending Changes (LDIF):");
    changesLabel.getStyle().set("font-weight", "bold");

    TextArea changesArea = new TextArea();
    changesArea.setWidthFull();
    changesArea.setHeight("200px");
    changesArea.setReadOnly(true);
    changesArea.setValue(changeLdif);

    Button copyChangesBtn = new Button(new Icon(VaadinIcon.COPY));
    copyChangesBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    copyChangesBtn.getElement().setAttribute("title", "Copy to clipboard");
    copyChangesBtn.addClickListener(e -> {
      getUI().ifPresent(ui -> ui.getPage().executeJs(
          "navigator.clipboard.writeText($0)", changeLdif));
      NotificationHelper.showSuccess("Changes LDIF copied to clipboard");
    });

    HorizontalLayout changesHeader = new HorizontalLayout(
        changesLabel, copyChangesBtn);
    changesHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    // Reverse/backout LDIF
    Span reverseLabel = new Span("Backout Changes (LDIF):");
    reverseLabel.getStyle().set("font-weight", "bold");

    TextArea reverseArea = new TextArea();
    reverseArea.setWidthFull();
    reverseArea.setHeight("200px");
    reverseArea.setReadOnly(true);
    reverseArea.setValue(reverseLdif);

    Button copyReverseBtn = new Button(new Icon(VaadinIcon.COPY));
    copyReverseBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    copyReverseBtn.getElement().setAttribute("title", "Copy to clipboard");
    copyReverseBtn.addClickListener(e -> {
      getUI().ifPresent(ui -> ui.getPage().executeJs(
          "navigator.clipboard.writeText($0)", reverseLdif));
      NotificationHelper.showSuccess("Backout LDIF copied to clipboard");
    });

    HorizontalLayout reverseHeader = new HorizontalLayout(
        reverseLabel, copyReverseBtn);
    reverseHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    VerticalLayout layout = new VerticalLayout(
        changesHeader, changesArea, reverseHeader, reverseArea);
    layout.setPadding(false);
    layout.setSpacing(true);
    dialog.add(layout);

    Button commitButton = new Button("Commit Changes",
        new Icon(VaadinIcon.CHECK));
    commitButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    commitButton.addClickListener(e -> {
      dialog.close();
      saveChanges();
    });

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.getFooter().add(cancelButton, commitButton);
    dialog.open();
  }

  /**
   * Builds LDIF text representing the pending changes.
   *
   * @return LDIF formatted string of pending changes
   */
  private String buildPendingChangesLdif() {
    if (currentEntry == null) {
      return "";
    }

    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(currentEntry.getDn()).append("\n");
    ldif.append("changetype: modify\n");

    boolean hasChanges = false;

    for (String attrName : modifiedAttributes) {
      List<String> currentValues = currentEntry.getAttributeValues(attrName);

      // Check for added values
      List<String> addedValues = new ArrayList<>();
      List<String> deletedValues = new ArrayList<>();

      if (currentValues != null) {
        for (String value : currentValues) {
          String changeKey = attrName + ":" + value;
          PendingChangeType changeType = pendingChanges.get(changeKey);
          if (changeType == PendingChangeType.ADDED) {
            addedValues.add(value);
          } else if (changeType == PendingChangeType.DELETED) {
            deletedValues.add(value);
          }
        }
      }

      // Check for all-values-deleted from pendingChanges map
      for (Map.Entry<String, PendingChangeType> entry
          : pendingChanges.entrySet()) {
        if (entry.getValue() == PendingChangeType.DELETED
            && entry.getKey().startsWith(attrName + ":")) {
          String value = entry.getKey().substring(
              attrName.length() + 1);
          if (!deletedValues.contains(value)) {
            deletedValues.add(value);
          }
        }
      }

      if (!deletedValues.isEmpty()) {
        if (hasChanges) {
          ldif.append("-\n");
        }
        ldif.append("delete: ").append(attrName).append("\n");
        for (String value : deletedValues) {
          ldif.append(attrName).append(": ").append(value).append("\n");
        }
        hasChanges = true;
      }

      if (!addedValues.isEmpty()) {
        if (hasChanges) {
          ldif.append("-\n");
        }
        ldif.append("add: ").append(attrName).append("\n");
        for (String value : addedValues) {
          ldif.append(attrName).append(": ").append(value).append("\n");
        }
        hasChanges = true;
      }

      // Check for replaced values (modified but not a simple add/delete)
      if (addedValues.isEmpty() && deletedValues.isEmpty()) {
        if (hasChanges) {
          ldif.append("-\n");
        }
        ldif.append("replace: ").append(attrName).append("\n");
        if (currentValues != null) {
          for (String value : currentValues) {
            ldif.append(attrName).append(": ")
                .append(value).append("\n");
          }
        }
        hasChanges = true;
      }
    }

    return ldif.toString();
  }

  /**
   * Builds LDIF text to reverse/backout the pending changes.
   *
   * @return LDIF formatted string to undo pending changes
   */
  private String buildPendingChangesReverseLdif() {
    if (currentEntry == null) {
      return "";
    }

    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(currentEntry.getDn()).append("\n");
    ldif.append("changetype: modify\n");

    boolean hasChanges = false;

    for (String attrName : modifiedAttributes) {
      List<String> currentValues = currentEntry.getAttributeValues(attrName);

      List<String> addedValues = new ArrayList<>();
      List<String> deletedValues = new ArrayList<>();

      if (currentValues != null) {
        for (String value : currentValues) {
          String changeKey = attrName + ":" + value;
          PendingChangeType changeType = pendingChanges.get(changeKey);
          if (changeType == PendingChangeType.ADDED) {
            addedValues.add(value);
          } else if (changeType == PendingChangeType.DELETED) {
            deletedValues.add(value);
          }
        }
      }

      for (Map.Entry<String, PendingChangeType> entry
          : pendingChanges.entrySet()) {
        if (entry.getValue() == PendingChangeType.DELETED
            && entry.getKey().startsWith(attrName + ":")) {
          String value = entry.getKey().substring(
              attrName.length() + 1);
          if (!deletedValues.contains(value)) {
            deletedValues.add(value);
          }
        }
      }

      // Reverse: added values should be deleted
      if (!addedValues.isEmpty()) {
        if (hasChanges) {
          ldif.append("-\n");
        }
        ldif.append("delete: ").append(attrName).append("\n");
        for (String value : addedValues) {
          ldif.append(attrName).append(": ").append(value).append("\n");
        }
        hasChanges = true;
      }

      // Reverse: deleted values should be added back
      if (!deletedValues.isEmpty()) {
        if (hasChanges) {
          ldif.append("-\n");
        }
        ldif.append("add: ").append(attrName).append("\n");
        for (String value : deletedValues) {
          ldif.append(attrName).append(": ").append(value).append("\n");
        }
        hasChanges = true;
      }

      // Reverse: modified values should be replaced with originals
      if (addedValues.isEmpty() && deletedValues.isEmpty()) {
        List<String> origValues =
            originalAttributeValues.get(attrName);
        if (origValues != null) {
          if (hasChanges) {
            ldif.append("-\n");
          }
          ldif.append("replace: ").append(attrName).append("\n");
          for (String value : origValues) {
            ldif.append(attrName).append(": ")
                .append(value).append("\n");
          }
          hasChanges = true;
        }
      }
    }

    return ldif.toString();
  }

  /**
   * Opens the LDIF dialog showing the entry in LDIF format
   * for both creating and deleting.
   */
  private void openLdifDialog() {
    if (currentEntry == null || serverConfig == null) {
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("LDIF");
    dialog.setWidth("600px");

    // Build create LDIF
    String createLdif = buildCreateEntryLdif();

    Span createLabel = new Span("Create Entry (LDIF):");
    createLabel.getStyle().set("font-weight", "bold");

    TextArea createArea = new TextArea();
    createArea.setWidthFull();
    createArea.setHeight("200px");
    createArea.setReadOnly(true);
    createArea.setValue(createLdif);

    Button copyCreateBtn = new Button(new Icon(VaadinIcon.COPY));
    copyCreateBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    copyCreateBtn.getElement().setAttribute("title", "Copy to clipboard");
    copyCreateBtn.addClickListener(e -> {
      getUI().ifPresent(ui -> ui.getPage().executeJs(
          "navigator.clipboard.writeText($0)", createLdif));
      NotificationHelper.showSuccess("Create LDIF copied to clipboard");
    });

    HorizontalLayout createHeader = new HorizontalLayout(
        createLabel, copyCreateBtn);
    createHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    // Build delete LDIF
    String deleteLdif = buildDeleteEntryLdif();

    Span deleteLabel = new Span("Delete Entry (LDIF):");
    deleteLabel.getStyle().set("font-weight", "bold");

    TextArea deleteArea = new TextArea();
    deleteArea.setWidthFull();
    deleteArea.setHeight("80px");
    deleteArea.setReadOnly(true);
    deleteArea.setValue(deleteLdif);

    Button copyDeleteBtn = new Button(new Icon(VaadinIcon.COPY));
    copyDeleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    copyDeleteBtn.getElement().setAttribute("title", "Copy to clipboard");
    copyDeleteBtn.addClickListener(e -> {
      getUI().ifPresent(ui -> ui.getPage().executeJs(
          "navigator.clipboard.writeText($0)", deleteLdif));
      NotificationHelper.showSuccess("Delete LDIF copied to clipboard");
    });

    HorizontalLayout deleteHeader = new HorizontalLayout(
        deleteLabel, copyDeleteBtn);
    deleteHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    VerticalLayout layout = new VerticalLayout(
        createHeader, createArea, deleteHeader, deleteArea);
    layout.setPadding(false);
    layout.setSpacing(true);
    dialog.add(layout);

    Button closeButton = new Button("Close", e -> dialog.close());
    dialog.getFooter().add(closeButton);
    dialog.open();
  }

  /**
   * Opens a dialog showing LDIF text for deleting the entry
   * (from the Delete Entry dialog).
   */
  private void openDeleteEntryLdifDialog() {
    if (currentEntry == null || serverConfig == null) {
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Delete Entry - LDIF");
    dialog.setWidth("600px");

    // Delete entry LDIF
    String deleteLdif = buildDeleteEntryLdif();

    Span deleteLabel = new Span("Delete Entry (LDIF):");
    deleteLabel.getStyle().set("font-weight", "bold");

    TextArea deleteArea = new TextArea();
    deleteArea.setWidthFull();
    deleteArea.setHeight("80px");
    deleteArea.setReadOnly(true);
    deleteArea.setValue(deleteLdif);

    Button copyDeleteBtn = new Button(new Icon(VaadinIcon.COPY));
    copyDeleteBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    copyDeleteBtn.getElement().setAttribute("title", "Copy to clipboard");
    copyDeleteBtn.addClickListener(e -> {
      getUI().ifPresent(ui -> ui.getPage().executeJs(
          "navigator.clipboard.writeText($0)", deleteLdif));
      NotificationHelper.showSuccess("Delete LDIF copied to clipboard");
    });

    HorizontalLayout deleteHeader = new HorizontalLayout(
        deleteLabel, copyDeleteBtn);
    deleteHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    // Create entry LDIF (to recreate the entry if needed)
    String createLdif = buildCreateEntryLdif();

    Span createLabel = new Span("Create Entry (LDIF):");
    createLabel.getStyle().set("font-weight", "bold");

    TextArea createArea = new TextArea();
    createArea.setWidthFull();
    createArea.setHeight("200px");
    createArea.setReadOnly(true);
    createArea.setValue(createLdif);

    Button copyCreateBtn = new Button(new Icon(VaadinIcon.COPY));
    copyCreateBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    copyCreateBtn.getElement().setAttribute("title", "Copy to clipboard");
    copyCreateBtn.addClickListener(e -> {
      getUI().ifPresent(ui -> ui.getPage().executeJs(
          "navigator.clipboard.writeText($0)", createLdif));
      NotificationHelper.showSuccess("Create LDIF copied to clipboard");
    });

    HorizontalLayout createHeader = new HorizontalLayout(
        createLabel, copyCreateBtn);
    createHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    VerticalLayout layout = new VerticalLayout(
        deleteHeader, deleteArea, createHeader, createArea);
    layout.setPadding(false);
    layout.setSpacing(true);
    dialog.add(layout);

    Button closeButton = new Button("Close", e -> dialog.close());
    dialog.getFooter().add(closeButton);
    dialog.open();
  }

  /**
   * Builds LDIF text to create the current entry.
   *
   * @return LDIF formatted string to add the entry
   */
  private String buildCreateEntryLdif() {
    if (currentEntry == null) {
      return "";
    }

    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(currentEntry.getDn()).append("\n");
    ldif.append("changetype: add\n");

    List<String> attrNames = new ArrayList<>(
        currentEntry.getAttributes().keySet());
    attrNames.sort(String.CASE_INSENSITIVE_ORDER);

    for (String attrName : attrNames) {
      List<String> values = currentEntry.getAttributeValues(attrName);
      if (values != null) {
        for (String value : values) {
          ldif.append(attrName).append(": ")
              .append(value).append("\n");
        }
      }
    }

    return ldif.toString();
  }

  /**
   * Builds LDIF text to delete the current entry.
   *
   * @return LDIF formatted string to delete the entry
   */
  private String buildDeleteEntryLdif() {
    if (currentEntry == null) {
      return "";
    }

    return "dn: " + currentEntry.getDn() + "\n"
        + "changetype: delete\n";
  }

  /**
   * Opens the Rename / Move dialog.
   */
  private void openRenameMoveDialog() {
    if (currentEntry == null || serverConfig == null) {
      return;
    }

    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Rename / Move");
    dialog.setWidth("600px");

    String currentDn = currentEntry.getDn();

    // Current DN display
    Span currentDnLabel = new Span("Current DN:");
    currentDnLabel.getStyle().set("font-weight", "bold");
    Span currentDnValue = new Span(currentDn);
    currentDnValue.getStyle()
        .set("font-family", "monospace")
        .set("word-break", "break-all");

    // Extract current RDN and Parent DN
    String currentRdn = "";
    String currentParentDn = "";
    int commaIndex = currentDn.indexOf(',');
    if (commaIndex > 0) {
      currentRdn = currentDn.substring(0, commaIndex);
      currentParentDn = currentDn.substring(commaIndex + 1);
    } else {
      currentRdn = currentDn;
    }

    TextField rdnField = new TextField(
        "Relative Distinguished Name (RDN)");
    rdnField.setWidthFull();
    rdnField.setValue(currentRdn);

    TextField parentDnField = new TextField("Parent DN");
    parentDnField.setWidthFull();
    parentDnField.setValue(currentParentDn);

    RadioButtonGroup<String> deleteOldRdnRadio =
        new RadioButtonGroup<>();
    deleteOldRdnRadio.setLabel("Delete old RDN");
    deleteOldRdnRadio.setItems("Yes", "No");
    deleteOldRdnRadio.setValue("Yes");

    VerticalLayout formLayout = new VerticalLayout(
        currentDnLabel, currentDnValue,
        rdnField, parentDnField, deleteOldRdnRadio);
    formLayout.setPadding(false);
    formLayout.setSpacing(true);
    dialog.add(formLayout);

    // Capture values for lambdas
    final String origRdn = currentRdn;
    final String origParentDn = currentParentDn;

    Button applyButton = new Button("Apply", new Icon(VaadinIcon.CHECK));
    applyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    applyButton.addClickListener(e -> {
      String newRdn = rdnField.getValue();
      String newParentDn = parentDnField.getValue();
      boolean deleteOldRdn = "Yes".equals(
          deleteOldRdnRadio.getValue());

      if (newRdn == null || newRdn.trim().isEmpty()) {
        NotificationHelper.showError("RDN is required.");
        return;
      }

      // Determine if parent changed
      String parentParam = newParentDn != null
          && !newParentDn.trim().isEmpty()
          && !newParentDn.trim().equals(origParentDn)
          ? newParentDn.trim() : null;

      try {
        ldapService.modifyDN(
            serverConfig,
            currentDn,
            newRdn.trim(),
            parentParam,
            deleteOldRdn
        );

        String newDn = parentParam != null
            ? newRdn.trim() + "," + parentParam
            : newRdn.trim() + "," + origParentDn;

        NotificationHelper.showSuccess(
            "Entry renamed/moved to: " + newDn);
        dialog.close();

        // Refresh to show the updated entry
        LdapEntry refreshedEntry = ldapService.readEntry(
            serverConfig, newDn, showOperationalAttributes);
        if (refreshedEntry != null) {
          refreshedEntry.setServerName(
              currentEntry.getServerName());
          editEntry(refreshedEntry);
        }
      } catch (Exception ex) {
        NotificationHelper.showError(
            "Failed to rename/move: " + ex.getMessage());
        logger.error("Failed to rename/move entry", ex);
      }
    });

    Button ldifBtn = new Button("LDIF", new Icon(VaadinIcon.FILE_TEXT_O));
    ldifBtn.addClickListener(e -> {
      String newRdn = rdnField.getValue();
      String newParentDn = parentDnField.getValue();
      boolean deleteOldRdn = "Yes".equals(
          deleteOldRdnRadio.getValue());
      openRenameMoveLdifDialog(
          currentDn, newRdn, newParentDn,
          origParentDn, deleteOldRdn);
    });

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.getFooter().add(cancelButton, ldifBtn, applyButton);
    dialog.open();
  }

  /**
   * Opens a dialog showing LDIF for rename/move and its undo.
   *
   * @param currentDn the current DN
   * @param newRdn the new RDN
   * @param newParentDn the new parent DN
   * @param origParentDn the original parent DN
   * @param deleteOldRdn whether to delete the old RDN
   */
  private void openRenameMoveLdifDialog(
      String currentDn, String newRdn, String newParentDn,
      String origParentDn, boolean deleteOldRdn
  ) {
    Dialog ldifDialog = new Dialog();
    ldifDialog.setHeaderTitle("Rename / Move - LDIF");
    ldifDialog.setWidth("600px");

    // Determine effective new parent
    String effectiveNewParent = (newParentDn != null
        && !newParentDn.trim().isEmpty())
        ? newParentDn.trim() : origParentDn;

    // Build rename LDIF
    StringBuilder renameLdif = new StringBuilder();
    renameLdif.append("dn: ").append(currentDn).append("\n");
    renameLdif.append("changetype: moddn\n");
    renameLdif.append("newrdn: ").append(
        newRdn != null ? newRdn.trim() : "").append("\n");
    renameLdif.append("deleteoldrdn: ")
        .append(deleteOldRdn ? "1" : "0").append("\n");
    if (newParentDn != null && !newParentDn.trim().isEmpty()
        && !newParentDn.trim().equals(origParentDn)) {
      renameLdif.append("newsuperior: ")
          .append(newParentDn.trim()).append("\n");
    }

    String renameText = renameLdif.toString();

    Span renameLabel = new Span("Rename / Move (LDIF):");
    renameLabel.getStyle().set("font-weight", "bold");

    TextArea renameArea = new TextArea();
    renameArea.setWidthFull();
    renameArea.setHeight("150px");
    renameArea.setReadOnly(true);
    renameArea.setValue(renameText);

    Button copyRenameBtn = new Button(new Icon(VaadinIcon.COPY));
    copyRenameBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    copyRenameBtn.getElement().setAttribute("title", "Copy to clipboard");
    copyRenameBtn.addClickListener(e -> {
      getUI().ifPresent(ui -> ui.getPage().executeJs(
          "navigator.clipboard.writeText($0)", renameText));
      NotificationHelper.showSuccess(
          "Rename LDIF copied to clipboard");
    });

    HorizontalLayout renameHeader = new HorizontalLayout(
        renameLabel, copyRenameBtn);
    renameHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    // Build undo LDIF
    String newDn = (newRdn != null ? newRdn.trim() : "")
        + "," + effectiveNewParent;
    // Extract original RDN from current DN
    String origRdn = currentDn.contains(",")
        ? currentDn.substring(0, currentDn.indexOf(','))
        : currentDn;

    StringBuilder undoLdif = new StringBuilder();
    undoLdif.append("dn: ").append(newDn).append("\n");
    undoLdif.append("changetype: moddn\n");
    undoLdif.append("newrdn: ").append(origRdn).append("\n");
    undoLdif.append("deleteoldrdn: ")
        .append(deleteOldRdn ? "1" : "0").append("\n");
    if (newParentDn != null && !newParentDn.trim().isEmpty()
        && !newParentDn.trim().equals(origParentDn)) {
      undoLdif.append("newsuperior: ")
          .append(origParentDn).append("\n");
    }

    String undoText = undoLdif.toString();

    Span undoLabel = new Span("Undo Rename / Move (LDIF):");
    undoLabel.getStyle().set("font-weight", "bold");

    TextArea undoArea = new TextArea();
    undoArea.setWidthFull();
    undoArea.setHeight("150px");
    undoArea.setReadOnly(true);
    undoArea.setValue(undoText);

    Button copyUndoBtn = new Button(new Icon(VaadinIcon.COPY));
    copyUndoBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    copyUndoBtn.getElement().setAttribute("title", "Copy to clipboard");
    copyUndoBtn.addClickListener(e -> {
      getUI().ifPresent(ui -> ui.getPage().executeJs(
          "navigator.clipboard.writeText($0)", undoText));
      NotificationHelper.showSuccess(
          "Undo LDIF copied to clipboard");
    });

    HorizontalLayout undoHeader = new HorizontalLayout(
        undoLabel, copyUndoBtn);
    undoHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);

    VerticalLayout layout = new VerticalLayout(
        renameHeader, renameArea, undoHeader, undoArea);
    layout.setPadding(false);
    layout.setSpacing(true);
    ldifDialog.add(layout);

    Button closeButton = new Button("Close",
        e -> ldifDialog.close());
    ldifDialog.getFooter().add(closeButton);
    ldifDialog.open();
  }

  /**
   * Shows attribute type details in a modal dialog.
   *
   * @param attributeName the attribute name to display
   */
  private void showAttributeTypeInSchema(String attributeName) {
    if (serverConfig == null) {
      NotificationHelper.showError("No server configuration available");
      return;
    }

    try {
      Schema schema = ldapService.getSchema(serverConfig);
      if (schema == null) {
        NotificationHelper.showError("Schema not available for this server");
        return;
      }

      AttributeTypeDefinition attrType = schema.getAttributeType(attributeName);
      if (attrType == null) {
        NotificationHelper.showError("Attribute type '" + attributeName 
            + "' not found in schema");
        return;
      }

      SchemaDetailDialogHelper.showAttributeTypeDialog(attrType, serverConfig.getName(), schema);
    } catch (Exception e) {
      logger.error("Error displaying attribute type details: {}", e.getMessage(), e);
      NotificationHelper.showError("Error displaying schema details: " + e.getMessage());
    }
  }

  /**
   * Shows object class details in a modal dialog.
   *
   * @param objectClassName the object class name to display
   */
  private void showObjectClassInSchema(String objectClassName) {
    if (serverConfig == null) {
      NotificationHelper.showError("No server configuration available");
      return;
    }

    try {
      Schema schema = ldapService.getSchema(serverConfig);
      if (schema == null) {
        NotificationHelper.showError("Schema not available for this server");
        return;
      }

      ObjectClassDefinition objClass = schema.getObjectClass(objectClassName);
      if (objClass == null) {
        NotificationHelper.showError("Object class '" + objectClassName 
            + "' not found in schema");
        return;
      }

      SchemaDetailDialogHelper.showObjectClassDialog(objClass, serverConfig.getName(), schema);
    } catch (Exception e) {
      logger.error("Error displaying object class details: {}", e.getMessage(), e);
      NotificationHelper.showError("Error displaying schema details: " + e.getMessage());
    }
  }

  /**
   * Data class for attribute rows in the grid.
   * Each row represents a single attribute name/value pair.
   */
  public static class AttributeRow {
    private String name;
    private String value;
    private int valueIndex;
    private boolean isFirstValueOfAttribute;

    /**
     * Creates attribute row for a single value.
     *
     * @param name attribute name
     * @param value single attribute value
     * @param valueIndex index of this value (0-based)
     * @param isFirstValueOfAttribute true if first value for this attribute
     */
    public AttributeRow(String name, String value, int valueIndex,
        boolean isFirstValueOfAttribute) {
      this.name = name;
      this.value = value;
      this.valueIndex = valueIndex;
      this.isFirstValueOfAttribute = isFirstValueOfAttribute;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public int getValueIndex() {
      return valueIndex;
    }

    public void setValueIndex(int valueIndex) {
      this.valueIndex = valueIndex;
    }

    public boolean isFirstValueOfAttribute() {
      return isFirstValueOfAttribute;
    }

    public void setFirstValueOfAttribute(boolean isFirstValueOfAttribute) {
      this.isFirstValueOfAttribute = isFirstValueOfAttribute;
    }
  }
}
