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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

  // UI Components
  private Span dnLabel;
  private MenuBar copyMenuBar;
  private Button expandButton;
  private Button searchFromHereButton;
  private Button addAttributeButton;
  private Button saveButton;
  private Button testLoginButton;
  private Button refreshButton;
  private Button deleteEntryButton;
  private Checkbox showOperationalAttributesCheckbox;
  private Grid<AttributeRow> attributeGrid;
  
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

    // Operational attributes checkbox
    showOperationalAttributesCheckbox = new Checkbox("Show operational attributes");
    showOperationalAttributesCheckbox.setValue(false);
    showOperationalAttributesCheckbox.addValueChangeListener(e -> {
      // When toggling, refresh the entry from LDAP to get operational attributes if needed
      if (currentEntry != null && serverConfig != null) {
        refreshEntry();
      }
    });

    // Attribute grid
    attributeGrid = new Grid<>(AttributeRow.class, false);
    attributeGrid.setSizeFull();

    attributeGrid.addColumn(new ComponentRenderer<>(this::createAttributeNameComponent))
        .setHeader("Attribute")
        .setFlexGrow(1)
        .setSortable(true)
        .setComparator(AttributeRow::getName);

    attributeGrid.addColumn(new ComponentRenderer<>(this::createValueComponent))
        .setHeader("Value")
        .setFlexGrow(2);

    // Initialize context menu for attribute actions
    initializeAttributeContextMenu();

    // Action buttons
    addAttributeButton = new Button("Add Attribute", new Icon(VaadinIcon.PLUS));
    addAttributeButton.addClickListener(e -> openAddAttributeDialog());

    saveButton = new Button("Save Changes", new Icon(VaadinIcon.CHECK));
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    saveButton.addClickListener(e -> saveChanges());

    testLoginButton = new Button("Test Login", new Icon(VaadinIcon.KEY));
    testLoginButton.addClickListener(e -> openTestLoginDialog());

    refreshButton = new Button("Refresh", new Icon(VaadinIcon.REFRESH));
    refreshButton.addClickListener(e -> refreshEntry());

    deleteEntryButton = new Button("Delete Entry", new Icon(VaadinIcon.TRASH));
    deleteEntryButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
    deleteEntryButton.addClickListener(e -> confirmDeleteEntry());

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
      
      contextMenu.addItem("Edit Value", event -> openEditValueDialog(row));
      contextMenu.addItem("Add Value", event -> openAddValueDialog(row));
      contextMenu.addItem("Delete Value", event -> deleteValue(row));
      contextMenu.addItem("Delete All Values", event -> deleteAllValues(row));
      
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

  private void setupLayout() {
    setSizeFull();
    setPadding(false);
    setSpacing(true);

    // Header with DN, copy button, expand button, and search button
    HorizontalLayout dnRow = new HorizontalLayout();
    dnRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    dnRow.setPadding(false);
    dnRow.setSpacing(true);
    dnRow.add(dnLabel, copyMenuBar, expandButton, searchFromHereButton);
    dnRow.setFlexGrow(1, dnLabel);

    // Action buttons with operational attributes checkbox on the right
    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    buttonLayout.setPadding(false);
    buttonLayout.setSpacing(true);
    buttonLayout.add(
        addAttributeButton,
        saveButton,
        testLoginButton,
        refreshButton,
        deleteEntryButton
    );

    // Add spacer and checkbox on the right
    Span spacer = new Span();
    buttonLayout.add(spacer, showOperationalAttributesCheckbox);
    buttonLayout.setFlexGrow(1, spacer);

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
      dnLabel.setText("DN: " + entry.getDn());
      
      // If operational attributes checkbox is already checked, fetch entry with operational attributes
      if (showOperationalAttributesCheckbox.getValue() && serverConfig != null) {
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
      copyMenuBar.setEnabled(true);
      expandButton.setEnabled(true);
      searchFromHereButton.setEnabled(true);
      showOperationalAttributesCheckbox.setEnabled(true);
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
    attributeGrid.setItems(Collections.emptyList());
    setButtonsEnabled(false);
    copyMenuBar.setEnabled(false);
    expandButton.setEnabled(false);
    searchFromHereButton.setEnabled(false);
    showOperationalAttributesCheckbox.setEnabled(false);
    showOperationalAttributesCheckbox.setValue(false);
  }

  private void refreshAttributeDisplay() {
    if (fullEntry == null) {
      attributeGrid.setItems(Collections.emptyList());
      return;
    }

    List<AttributeRow> rows = new ArrayList<>();
    boolean showOperational = showOperationalAttributesCheckbox.getValue();

    // Collect attributes with their values in sorted order
    List<Map.Entry<String, List<String>>> sortedAttrs = new ArrayList<>();
    
    // Add regular attributes
    for (Map.Entry<String, List<String>> attr : fullEntry.getAttributes().entrySet()) {
      String attrName = attr.getKey();
      
      // Filter operational attributes if checkbox is unchecked
      if (!showOperational && isOperationalAttribute(attrName)) {
        continue;
      }

      sortedAttrs.add(attr);
    }
    
    // Add operational attributes if checkbox is checked
    if (showOperational && fullEntry.getOperationalAttributes() != null) {
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

    attributeGrid.setItems(rows);
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
      
      // Check usage - operational attributes have usage other than "userApplications"
      if (attrDef.getUsage() != null) {
        String usage = attrDef.getUsage().getName();
        // Operational attributes have usage: directoryOperation, dSAOperation, or distributedOperation
        return !usage.equalsIgnoreCase("userApplications");
      }
      
      return false;
    } catch (Exception e) {
      logger.debug("Failed to determine operational status for attribute {}: {}", 
          attributeName, e.getMessage());
      return false;
    }
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
      modifiedAttributes.add(name.trim());
      markPendingChanges();
      refreshAttributeDisplay();
      NotificationHelper.showSuccess("Added attribute '" + name.trim() + "' with " + values.size() + " value(s).");
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
        allValues.set(row.getValueIndex(), newValue.trim());
        currentEntry.getAttributes().put(row.getName(), new ArrayList<>(allValues));
        modifiedAttributes.add(row.getName());
        markPendingChanges();
        refreshAttributeDisplay();
        NotificationHelper.showSuccess("Updated value for '" + row.getName() + "'.");
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

    if (allValues.size() == 1) {
      NotificationHelper.showWarning("Cannot delete the last value. Use 'Delete All Values' to remove the entire attribute.");
      return;
    }

    ConfirmDialog dialog = new ConfirmDialog();
    dialog.setHeader("Delete Value");
    dialog.setText("Are you sure you want to delete this value from '" + row.getName() + "'?\n\nValue: " + row.getValue());
    dialog.setCancelable(true);
    dialog.setConfirmText("Delete");
    dialog.addConfirmListener(e -> {
      allValues.remove(row.getValueIndex());
      currentEntry.getAttributes().put(row.getName(), new ArrayList<>(allValues));
      modifiedAttributes.add(row.getName());
      markPendingChanges();
      refreshAttributeDisplay();
      NotificationHelper.showSuccess("Deleted value from '" + row.getName() + "'.");
    });
    dialog.open();
  }

  private void deleteAllValues(AttributeRow row) {
    ConfirmDialog dialog = new ConfirmDialog();
    dialog.setHeader("Delete All Values");
    dialog.setText("Are you sure you want to delete all values for the attribute '" + row.getName() + "'?");
    dialog.setCancelable(true);
    dialog.setConfirmText("Delete");
    dialog.addConfirmListener(e -> {
      currentEntry.getAttributes().remove(row.getName());
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
      NotificationHelper.showSuccess("Entry saved successfully.");
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
      List<String> modifiedValues = modified.getAttributeValues(attrName);

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
          showOperationalAttributesCheckbox.getValue()
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

    ConfirmDialog dialog = new ConfirmDialog();
    dialog.setHeader("Delete Entry");
    dialog.setText("Are you sure you want to delete this entry?\n\nDN: " + currentEntry.getDn());
    dialog.setCancelable(true);
    dialog.setConfirmText("Delete");
    dialog.addConfirmListener(e -> deleteEntry());
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

  private void setButtonsEnabled(boolean enabled) {
    addAttributeButton.setEnabled(enabled);
    saveButton.setEnabled(enabled);
    testLoginButton.setEnabled(enabled);
    refreshButton.setEnabled(enabled);
    deleteEntryButton.setEnabled(enabled);
  }

  private void markPendingChanges() {
    hasPendingChanges = true;
    saveButton.setText("Save Changes *");
    saveButton.getStyle().set("font-weight", "bold");
  }

  private void clearPendingChanges() {
    hasPendingChanges = false;
    modifiedAttributes.clear();
    saveButton.setText("Save Changes");
    saveButton.getStyle().remove("font-weight");
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
     * @param isFirstValueOfAttribute true if this is the first value for this attribute
     */
    public AttributeRow(String name, String value, int valueIndex, boolean isFirstValueOfAttribute) {
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
