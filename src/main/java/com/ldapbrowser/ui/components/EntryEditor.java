package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
  private Button copyDnButton;
  private Button addAttributeButton;
  private Button saveButton;
  private Button testLoginButton;
  private Button refreshButton;
  private Button deleteEntryButton;
  private Checkbox showOperationalAttributesCheckbox;
  private Grid<AttributeRow> attributeGrid;

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

    copyDnButton = new Button(new Icon(VaadinIcon.COPY));
    copyDnButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    copyDnButton.getElement().setAttribute("title", "Copy DN to clipboard");
    copyDnButton.setEnabled(false);
    copyDnButton.addClickListener(e -> copyDnToClipboard());

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
        .setHeader("Values")
        .setFlexGrow(2);

    attributeGrid.addColumn(new ComponentRenderer<>(this::createActionButtons))
        .setHeader("Actions")
        .setFlexGrow(0)
        .setWidth("160px");

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

  private void setupLayout() {
    setSizeFull();
    setPadding(false);
    setSpacing(true);

    // Header with DN and copy button
    HorizontalLayout dnRow = new HorizontalLayout();
    dnRow.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    dnRow.setPadding(false);
    dnRow.setSpacing(true);
    dnRow.add(dnLabel, copyDnButton);
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
      refreshAttributeDisplay();
      setButtonsEnabled(true);
      copyDnButton.setEnabled(true);
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
    copyDnButton.setEnabled(false);
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

    for (Map.Entry<String, List<String>> attr : fullEntry.getAttributes().entrySet()) {
      String attrName = attr.getKey();
      
      // Filter operational attributes if checkbox is unchecked
      if (!showOperational && isOperationalAttribute(attrName)) {
        continue;
      }

      rows.add(new AttributeRow(attrName, attr.getValue()));
    }

    // Sort attributes: objectClass first, then alphabetically
    rows.sort((row1, row2) -> {
      String attr1 = row1.getName();
      String attr2 = row2.getName();

      if ("objectClass".equalsIgnoreCase(attr1)) {
        return -1;
      }
      if ("objectClass".equalsIgnoreCase(attr2)) {
        return 1;
      }

      return attr1.compareToIgnoreCase(attr2);
    });

    attributeGrid.setItems(rows);
  }

  private boolean isOperationalAttribute(String attributeName) {
    String lowerName = attributeName.toLowerCase();
    return lowerName.startsWith("create")
        || lowerName.startsWith("modify")
        || lowerName.equals("entryuuid")
        || lowerName.equals("entrydn")
        || lowerName.equals("entrycsn")
        || lowerName.equals("hassubordinates")
        || lowerName.equals("subschemasubentry")
        || lowerName.equals("structuralobjectclass");
  }

  private Span createAttributeNameComponent(AttributeRow row) {
    Span nameSpan = new Span(row.getName());
    nameSpan.getStyle().set("font-weight", "500");

    // Color code: objectClass special, operational attributes orange
    if ("objectClass".equalsIgnoreCase(row.getName())) {
      nameSpan.getStyle().set("color", "#d32f2f");
    } else if (isOperationalAttribute(row.getName())) {
      nameSpan.getStyle().set("color", "#f57c00");
    }

    return nameSpan;
  }

  private VerticalLayout createValueComponent(AttributeRow row) {
    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(false);
    layout.setSpacing(false);

    for (String value : row.getValues()) {
      Span valueSpan = new Span(value);
      valueSpan.getStyle().set("display", "block");
      valueSpan.getStyle().set("margin-bottom", "4px");
      if (value.length() > 50) {
        valueSpan.getStyle().set("font-size", "smaller");
      }
      layout.add(valueSpan);
    }

    return layout;
  }

  private HorizontalLayout createActionButtons(AttributeRow row) {
    Button editButton = new Button(new Icon(VaadinIcon.EDIT));
    editButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
    editButton.addClickListener(e -> openEditAttributeDialog(row));
    editButton.getElement().setAttribute("title", "Edit attribute");

    Button deleteButton = new Button(new Icon(VaadinIcon.TRASH));
    deleteButton.addThemeVariants(
        ButtonVariant.LUMO_SMALL,
        ButtonVariant.LUMO_TERTIARY,
        ButtonVariant.LUMO_ERROR
    );
    deleteButton.addClickListener(e -> deleteAttribute(row));
    deleteButton.getElement().setAttribute("title", "Delete attribute");

    HorizontalLayout layout = new HorizontalLayout(editButton, deleteButton);
    layout.setSpacing(false);
    layout.setPadding(false);
    return layout;
  }

  private void openAddAttributeDialog() {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Add Attribute");

    TextField nameField = new TextField("Attribute Name");
    nameField.setWidthFull();

    TextArea valueArea = new TextArea("Values (one per line)");
    valueArea.setWidthFull();
    valueArea.setHeight("200px");

    Button saveButton = new Button("Add", e -> {
      String name = nameField.getValue();
      String valuesText = valueArea.getValue();

      if (name == null || name.trim().isEmpty()) {
        showError("Attribute name is required.");
        return;
      }

      if (valuesText == null || valuesText.trim().isEmpty()) {
        showError("At least one value is required.");
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
      showSuccess("Added attribute '" + name.trim() + "' with " + values.size() + " value(s).");
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

  private void openEditAttributeDialog(AttributeRow row) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Edit Attribute: " + row.getName());

    TextArea valueArea = new TextArea("Values (one per line)");
    valueArea.setWidthFull();
    valueArea.setHeight("200px");
    valueArea.setValue(String.join("\n", row.getValues()));

    Button saveButton = new Button("Save", e -> {
      String valuesText = valueArea.getValue();
      
      if (valuesText == null || valuesText.trim().isEmpty()) {
        showError("At least one value is required.");
        return;
      }

      List<String> values = Arrays.asList(valuesText.split("\n"));
      values.replaceAll(String::trim);
      values.removeIf(String::isEmpty);

      currentEntry.getAttributes().put(row.getName(), new ArrayList<>(values));
      modifiedAttributes.add(row.getName());
      markPendingChanges();
      refreshAttributeDisplay();
      showSuccess("Updated attribute '" + row.getName() + "'.");
      dialog.close();
    });
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> dialog.close());

    VerticalLayout layout = new VerticalLayout(valueArea);
    layout.setWidth("400px");
    dialog.add(layout);
    dialog.getFooter().add(cancelButton, saveButton);
    dialog.open();
  }

  private void deleteAttribute(AttributeRow row) {
    ConfirmDialog dialog = new ConfirmDialog();
    dialog.setHeader("Delete Attribute");
    dialog.setText("Are you sure you want to delete the attribute '" + row.getName() + "'?");
    dialog.setCancelable(true);
    dialog.setConfirmText("Delete");
    dialog.addConfirmListener(e -> {
      currentEntry.getAttributes().remove(row.getName());
      modifiedAttributes.add(row.getName());
      markPendingChanges();
      refreshAttributeDisplay();
      showSuccess("Deleted attribute '" + row.getName() + "'.");
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
        showError("Could not load original entry for comparison.");
        return;
      }

      List<Modification> modifications = createModifications(originalEntry, currentEntry);
      if (modifications.isEmpty()) {
        showInfo("No changes to save.");
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
      showSuccess("Entry saved successfully.");
      refreshEntry();
    } catch (Exception e) {
      showError("Failed to save entry: " + e.getMessage());
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
        showInfo("Entry refreshed.");
      } else {
        showError("Entry not found.");
        clear();
      }
    } catch (Exception e) {
      showError("Failed to refresh entry: " + e.getMessage());
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
      showSuccess("Entry deleted successfully.");
      clear();
    } catch (Exception e) {
      showError("Failed to delete entry: " + e.getMessage());
      logger.error("Failed to delete entry", e);
    }
  }

  private void openTestLoginDialog() {
    if (currentEntry == null || serverConfig == null) {
      showError("No entry selected or server not configured.");
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
        showError("Password is required for authentication test.");
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
        showError("Unexpected error: " + ex.getMessage());
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
      showInfo("No DN to copy.");
      return;
    }

    String dn = currentEntry.getDn();
    getUI().ifPresent(ui -> {
      ui.getPage().executeJs("navigator.clipboard.writeText($0)", dn);
    });

    showSuccess("DN copied to clipboard: " + dn);
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

  private void showSuccess(String message) {
    Notification notification = Notification.show(
        message,
        3000,
        Notification.Position.BOTTOM_END
    );
    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  private void showError(String message) {
    Notification notification = Notification.show(
        message,
        5000,
        Notification.Position.BOTTOM_END
    );
    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
  }

  private void showInfo(String message) {
    Notification notification = Notification.show(
        message,
        3000,
        Notification.Position.BOTTOM_END
    );
    notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
  }

  /**
   * Data class for attribute rows in the grid.
   */
  public static class AttributeRow {
    private String name;
    private List<String> values;

    /**
     * Creates attribute row.
     *
     * @param name attribute name
     * @param values attribute values
     */
    public AttributeRow(String name, List<String> values) {
      this.name = name;
      this.values = new ArrayList<>(values);
      
      // Sort objectClass values alphabetically
      if ("objectClass".equalsIgnoreCase(name)) {
        this.values.sort(String.CASE_INSENSITIVE_ORDER);
      }
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<String> getValues() {
      return values;
    }

    public void setValues(List<String> values) {
      this.values = new ArrayList<>(values);
    }
  }
}
