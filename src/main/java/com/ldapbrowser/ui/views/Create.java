package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.MainLayout;
import com.unboundid.ldap.sdk.LDAPException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Create view for creating new LDAP entries.
 */
@Route(value = "create", layout = MainLayout.class)
@PageTitle("Create | LDAP Browser")
public class Create extends VerticalLayout {

  private final LdapService ldapService;
  private final ConfigurationService configService;

  // UI Components
  private TextField rdnField;
  private TextField parentDnField;
  private Button parentDnBrowseButton;
  private TextField dnField;
  private ComboBox<String> templateComboBox;
  private Grid<AttributeRow> attributeGrid;
  private Button addRowButton;
  private Button createButton;
  private Button clearButton;

  // Data
  private List<AttributeRow> attributeRows;

  /**
   * Creates the Create view.
   *
   * @param ldapService LDAP service
   * @param configService configuration service
   */
  public Create(LdapService ldapService, ConfigurationService configService) {
    this.ldapService = ldapService;
    this.configService = configService;
    this.attributeRows = new ArrayList<>();

    setSizeFull();
    setPadding(true);
    setSpacing(true);

    initializeComponents();
    setupLayout();

    // Add initial empty row
    addEmptyRow();
  }

  private void initializeComponents() {
    // RDN field
    rdnField = new TextField("Relative Distinguished Name (RDN)");
    rdnField.setPlaceholder("cn=");
    rdnField.setRequired(true);
    rdnField.setRequiredIndicatorVisible(true);
    rdnField.addValueChangeListener(e -> updateComputedDn());

    // Parent DN field
    parentDnField = new TextField("Parent DN");
    parentDnField.setPlaceholder("ou=people,dc=example,dc=com");
    parentDnField.setRequired(true);
    parentDnField.setRequiredIndicatorVisible(true);
    parentDnField.addValueChangeListener(e -> updateComputedDn());

    // Browse button for Parent DN
    parentDnBrowseButton = new Button(VaadinIcon.FOLDER_OPEN.create());
    parentDnBrowseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    parentDnBrowseButton.setTooltipText("Select DN from Directory");
    parentDnBrowseButton.addClickListener(e -> showBrowseDialog());

    // DN field (computed/read-only)
    dnField = new TextField("Distinguished Name (DN)");
    dnField.setWidthFull();
    dnField.setPlaceholder("Will be computed from RDN and Parent DN");
    dnField.setReadOnly(true);

    // Template dropdown
    templateComboBox = new ComboBox<>("Entry Template (Optional)");
    templateComboBox.setWidthFull();
    templateComboBox.setItems("None", "User", "Group", "Dynamic Group", "OU");
    templateComboBox.setValue("None");
    templateComboBox.setPlaceholder("Select a template to auto-populate attributes");
    templateComboBox.addValueChangeListener(e -> applyTemplate(e.getValue()));

    // Attribute grid
    attributeGrid = new Grid<>(AttributeRow.class, false);
    attributeGrid.setSizeFull();
    attributeGrid.setHeight("400px");

    // Configure grid columns
    attributeGrid.addColumn(new ComponentRenderer<>(this::createAttributeNameField))
        .setHeader("Attribute Name")
        .setFlexGrow(1);

    attributeGrid.addColumn(new ComponentRenderer<>(this::createAttributeValueField))
        .setHeader("Attribute Value")
        .setFlexGrow(2);

    attributeGrid.addColumn(new ComponentRenderer<>(this::createActionButtons))
        .setHeader("Actions")
        .setFlexGrow(0)
        .setWidth("100px");

    // Action buttons
    addRowButton = new Button("Add Row", new Icon(VaadinIcon.PLUS));
    addRowButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
    addRowButton.addClickListener(e -> addEmptyRow());

    createButton = new Button("Create Entry", new Icon(VaadinIcon.CHECK));
    createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    createButton.addClickListener(e -> createEntry());

    clearButton = new Button("Clear All", new Icon(VaadinIcon.ERASER));
    clearButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    clearButton.addClickListener(e -> clearAll());
  }

  private void setupLayout() {
    addClassName("create-view");

    // Title with icon
    HorizontalLayout titleLayout = new HorizontalLayout();
    titleLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    titleLayout.setSpacing(true);

    Icon newEntryIcon = new Icon(VaadinIcon.PLUS_CIRCLE);
    newEntryIcon.setSize("20px");
    newEntryIcon.getStyle().set("color", "#4caf50");

    H3 title = new H3("Create New LDAP Entry");
    title.addClassNames(LumoUtility.Margin.NONE);
    title.getStyle().set("color", "#333");

    titleLayout.add(newEntryIcon, title);

    // Info text
    Span infoText = new Span("Fill in the DN and attributes to create a new LDAP entry. " +
        "At minimum, you need to specify the objectClass attribute.");
    infoText.getStyle().set("color", "#666").set("font-style", "italic").set("margin-bottom", "16px");

    // RDN and Parent DN layout
    HorizontalLayout dnCompositionLayout = new HorizontalLayout();
    dnCompositionLayout.setWidthFull();
    dnCompositionLayout.setDefaultVerticalComponentAlignment(Alignment.BASELINE);
    dnCompositionLayout.setSpacing(false);
    
    // RDN field
    rdnField.setWidth("300px");
    
    // Comma separator
    Span commaSeparator = new Span(",");
    commaSeparator.getStyle()
        .set("font-size", "var(--lumo-font-size-xl)")
        .set("font-weight", "bold")
        .set("padding", "0 var(--lumo-space-s)")
        .set("align-self", "flex-end")
        .set("padding-bottom", "var(--lumo-space-s)");
    
    // Parent DN field with browse button
    HorizontalLayout parentDnLayout = new HorizontalLayout();
    parentDnLayout.setSpacing(false);
    parentDnLayout.setDefaultVerticalComponentAlignment(Alignment.BASELINE);
    parentDnLayout.setFlexGrow(1, parentDnField);
    parentDnLayout.add(parentDnField, parentDnBrowseButton);
    
    dnCompositionLayout.add(rdnField, commaSeparator, parentDnLayout);

    // Button layout
    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setSpacing(true);
    buttonLayout.add(addRowButton, clearButton, createButton);

    add(titleLayout, infoText, dnCompositionLayout, dnField, templateComboBox, attributeGrid, buttonLayout);
    setFlexGrow(1, attributeGrid);
  }

  private TextField createAttributeNameField(AttributeRow row) {
    TextField nameField = new TextField();
    nameField.setWidthFull();
    nameField.setPlaceholder("e.g., objectClass, cn, mail");
    nameField.setValue(row.getAttributeName() != null ? row.getAttributeName() : "");
    nameField.addValueChangeListener(e -> row.setAttributeName(e.getValue()));
    return nameField;
  }

  private TextField createAttributeValueField(AttributeRow row) {
    TextField valueField = new TextField();
    valueField.setWidthFull();
    valueField.setPlaceholder("Enter attribute value");
    valueField.setValue(row.getAttributeValue() != null ? row.getAttributeValue() : "");
    valueField.addValueChangeListener(e -> row.setAttributeValue(e.getValue()));
    return valueField;
  }

  private HorizontalLayout createActionButtons(AttributeRow row) {
    Button deleteButton = new Button(new Icon(VaadinIcon.TRASH));
    deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
    deleteButton.addClickListener(e -> removeRow(row));
    deleteButton.getElement().setAttribute("title", "Remove row");

    HorizontalLayout layout = new HorizontalLayout(deleteButton);
    layout.setSpacing(false);
    layout.setPadding(false);

    return layout;
  }

  private void addEmptyRow() {
    AttributeRow newRow = new AttributeRow();
    attributeRows.add(newRow);
    refreshGrid();
  }

  private void removeRow(AttributeRow row) {
    attributeRows.remove(row);
    // Ensure at least one row remains
    if (attributeRows.isEmpty()) {
      addEmptyRow();
    } else {
      refreshGrid();
    }
  }

  private void refreshGrid() {
    attributeGrid.setItems(attributeRows);
    attributeGrid.getDataProvider().refreshAll();
  }

  private void createEntry() {
    // Get selected servers from MainLayout
    Set<String> selectedServers = MainLayout.getSelectedServers();
    if (selectedServers == null || selectedServers.isEmpty()) {
      showError("Please select a server from the top menu");
      return;
    }

    if (selectedServers.size() > 1) {
      showError("Please select only one server to create entries");
      return;
    }

    String selectedServer = selectedServers.iterator().next();
    LdapServerConfig serverConfig = configService.loadConfigurations().stream()
        .filter(config -> config.getName().equals(selectedServer))
        .findFirst()
        .orElse(null);

    if (serverConfig == null) {
      showError("Selected server configuration not found");
      return;
    }

    String dn = dnField.getValue();
    if (dn == null || dn.trim().isEmpty()) {
      showError("Distinguished Name (DN) is required");
      return;
    }

    // Collect attributes
    Map<String, List<String>> attributes = new LinkedHashMap<>();
    boolean hasValidAttributes = false;

    for (AttributeRow row : attributeRows) {
      String name = row.getAttributeName();
      String value = row.getAttributeValue();

      if (name != null && !name.trim().isEmpty() && value != null && !value.trim().isEmpty()) {
        name = name.trim();
        value = value.trim();

        // Add to attributes map (supporting multi-valued attributes)
        attributes.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        hasValidAttributes = true;
      }
    }

    if (!hasValidAttributes) {
      showError("At least one attribute with a valid name and value is required");
      return;
    }

    // Check if objectClass is specified
    if (!attributes.containsKey("objectClass")) {
      showError("The 'objectClass' attribute is required for LDAP entries");
      return;
    }

    try {
      // Create LdapEntry
      LdapEntry newEntry = new LdapEntry();
      newEntry.setDn(dn.trim());
      newEntry.setAttributes(attributes);

      // Add the entry using LdapService
      ldapService.addEntry(serverConfig, newEntry);

      showSuccess("Entry created successfully: " + dn);

      // Clear form after successful creation
      clearAll();

    } catch (LDAPException e) {
      showError("Failed to create entry: " + e.getMessage());
    } catch (Exception e) {
      showError("Unexpected error: " + e.getMessage());
    }
  }

  private void applyTemplate(String template) {
    if (template == null || "None".equals(template)) {
      return;
    }

    // Clear existing attributes but keep non-empty user-entered ones
    List<AttributeRow> existingRows = new ArrayList<>();
    for (AttributeRow row : attributeRows) {
      if (row.getAttributeName() != null && !row.getAttributeName().trim().isEmpty() &&
          row.getAttributeValue() != null && !row.getAttributeValue().trim().isEmpty()) {
        existingRows.add(row);
      }
    }

    attributeRows.clear();
    attributeRows.addAll(existingRows);

    // Add template-specific attributes
    switch (template) {
      case "User":
        addTemplateAttribute("objectClass", "inetOrgPerson");
        addTemplateAttribute("cn", "");
        addTemplateAttribute("sn", "");
        addTemplateAttribute("uid", "");
        addTemplateAttribute("mail", "");
        break;

      case "Group":
        addTemplateAttribute("objectClass", "groupOfUniqueNames");
        addTemplateAttribute("cn", "");
        addTemplateAttribute("uniqueMember", "");
        break;

      case "Dynamic Group":
        addTemplateAttribute("objectClass", "groupOfURLs");
        addTemplateAttribute("cn", "");
        addTemplateAttribute("memberURL", "");
        break;

      case "OU":
        addTemplateAttribute("objectClass", "organizationalUnit");
        addTemplateAttribute("ou", "");
        break;
    }

    // Add an empty row at the end for additional attributes
    addEmptyRow();
    refreshGrid();
  }

  private void addTemplateAttribute(String name, String value) {
    // Check if attribute already exists to avoid duplicates
    boolean exists = attributeRows.stream()
        .anyMatch(row -> name.equals(row.getAttributeName()));

    if (!exists) {
      attributeRows.add(new AttributeRow(name, value));
    }
  }

  /**
   * Updates the computed DN field based on RDN and Parent DN.
   */
  private void updateComputedDn() {
    String rdn = rdnField.getValue();
    String parentDn = parentDnField.getValue();
    
    if (rdn != null && !rdn.trim().isEmpty() && parentDn != null && !parentDn.trim().isEmpty()) {
      dnField.setValue(rdn.trim() + "," + parentDn.trim());
    } else if (rdn != null && !rdn.trim().isEmpty()) {
      dnField.setValue(rdn.trim());
    } else {
      dnField.setValue("");
    }
  }

  /**
   * Shows a dialog to browse and select a DN from the LDAP directory.
   */
  private void showBrowseDialog() {
    // Get selected servers from MainLayout
    Set<String> selectedServerNames = MainLayout.getSelectedServers();
    if (selectedServerNames == null || selectedServerNames.isEmpty()) {
      Notification notification = Notification.show(
          "Please select at least one server from the top menu",
          3000,
          Notification.Position.MIDDLE);
      notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    // Get server configurations
    List<LdapServerConfig> selectedConfigs = configService.loadConfigurations().stream()
        .filter(config -> selectedServerNames.contains(config.getName()))
        .toList();

    if (selectedConfigs.isEmpty()) {
      Notification notification = Notification.show(
          "Selected servers not found in configuration",
          3000,
          Notification.Position.MIDDLE);
      notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
      return;
    }

    // Create dialog with tree browser
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Select DN from Directory");
    dialog.setWidth("800px");
    dialog.setHeight("600px");
    dialog.setModal(true);
    dialog.setCloseOnOutsideClick(false);

    // Create the tree browser component
    com.ldapbrowser.ui.components.LdapTreeBrowser treeBrowser = 
        new com.ldapbrowser.ui.components.LdapTreeBrowser(ldapService);

    // Load the tree with all selected server configurations
    try {
      treeBrowser.setServerConfigs(selectedConfigs);
      treeBrowser.loadServers();
    } catch (Exception ex) {
      Notification notification = Notification.show(
          "Failed to load LDAP tree: " + ex.getMessage(),
          5000,
          Notification.Position.MIDDLE);
      notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
      dialog.close();
      return;
    }

    // Handle entry selection
    treeBrowser.addSelectionListener(event -> {
      LdapEntry selectedEntry = event.getSelectedEntry();
      if (selectedEntry != null && isValidDnForParent(selectedEntry)) {
        parentDnField.setValue(selectedEntry.getDn());
        dialog.close();
      }
    });

    // Add buttons
    Button selectButton = new Button("Select", e -> {
      LdapEntry selectedEntry = treeBrowser.getSelectedEntry();
      if (selectedEntry != null && isValidDnForParent(selectedEntry)) {
        parentDnField.setValue(selectedEntry.getDn());
        dialog.close();
      } else if (selectedEntry != null) {
        Notification.show("Please select a valid DN (not a server or Root DSE)",
            3000, Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_ERROR);
      } else {
        Notification.show("Please select an entry",
            3000, Notification.Position.MIDDLE)
            .addThemeVariants(NotificationVariant.LUMO_ERROR);
      }
    });
    selectButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    HorizontalLayout buttonLayout = new HorizontalLayout(cancelButton, selectButton);
    buttonLayout.setJustifyContentMode(JustifyContentMode.END);
    buttonLayout.setWidthFull();

    VerticalLayout dialogLayout = new VerticalLayout(treeBrowser, buttonLayout);
    dialogLayout.setSizeFull();
    dialogLayout.setPadding(false);
    dialogLayout.setSpacing(false);
    dialogLayout.setFlexGrow(1, treeBrowser);

    dialog.add(dialogLayout);
    dialog.open();
  }

  /**
   * Validates if an entry is a valid DN for use as a parent DN.
   */
  private boolean isValidDnForParent(LdapEntry entry) {
    if (entry == null || entry.getDn() == null || entry.getDn().isEmpty()) {
      return false;
    }
    // Exclude server nodes and Root DSE
    String dn = entry.getDn().toLowerCase();
    return !dn.equals("root dse") && !dn.equals("servers");
  }

  private void clearAll() {
    rdnField.clear();
    parentDnField.clear();
    templateComboBox.setValue("None");
    attributeRows.clear();
    addEmptyRow(); // Add one empty row
  }

  private void showSuccess(String message) {
    Notification notification = Notification.show(message, 4000, Notification.Position.TOP_END);
    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  private void showError(String message) {
    Notification notification = Notification.show(message, 5000, Notification.Position.TOP_END);
    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
  }

  /**
   * Data class for attribute rows in the grid
   */
  public static class AttributeRow {
    private String attributeName;
    private String attributeValue;

    public AttributeRow() {
    }

    public AttributeRow(String attributeName, String attributeValue) {
      this.attributeName = attributeName;
      this.attributeValue = attributeValue;
    }

    public String getAttributeName() {
      return attributeName;
    }

    public void setAttributeName(String attributeName) {
      this.attributeName = attributeName;
    }

    public String getAttributeValue() {
      return attributeValue;
    }

    public void setAttributeValue(String attributeValue) {
      this.attributeValue = attributeValue;
    }
  }
}