package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.EntryTemplate;
import com.ldapbrowser.model.EntryTemplate.CreateTemplateSection;
import com.ldapbrowser.model.EntryTemplate.FieldType;
import com.ldapbrowser.model.EntryTemplate.TemplateAttribute;
import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.TemplateService;
import com.ldapbrowser.service.TruststoreService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.components.TemplateFieldFactory;
import com.ldapbrowser.ui.dialogs.DnBrowserDialog;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchScope;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;
import com.vaadin.flow.theme.lumo.LumoUtility;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.annotation.security.RolesAllowed;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Create view for creating new LDAP entries.
 */
@Route(value = "create", layout = MainLayout.class)
@PageTitle("Create | LDAP Browser")
@RolesAllowed("ADMIN")
@UIScope
@Component
public class CreateView extends VerticalLayout {

  private final LdapService ldapService;
  private final ConfigurationService configService;
  private final TruststoreService truststoreService;
  private final TemplateService templateService;

  // UI Components
  private TextField rdnField;
  private TextField parentDnField;
  private Button parentDnBrowseButton;
  private ComboBox<String> parentDnCombo;
  private TextField dnField;
  private ComboBox<String> templateComboBox;
  private Grid<AttributeRow> attributeGrid;
  private Button addRowButton;
  private Button createButton;
  private Button clearButton;

  // Data
  private List<AttributeRow> attributeRows;
  private List<EntryTemplate> createTemplates = new ArrayList<>();
  private EntryTemplate activeTemplate;
  private List<AttributeRow> hiddenAttributeRows = new ArrayList<>();

  /**
   * Creates the Create view.
   *
   * @param ldapService LDAP service
   * @param configService configuration service
   * @param truststoreService truststore service
   * @param templateService template service
   */
  public CreateView(LdapService ldapService,
      ConfigurationService configService,
      TruststoreService truststoreService,
      TemplateService templateService) {
    this.ldapService = ldapService;
    this.configService = configService;
    this.truststoreService = truststoreService;
    this.templateService = templateService;
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
    templateComboBox = new ComboBox<>("Create Template");
    templateComboBox.setWidthFull();
    loadTemplateItems();
    templateComboBox.setValue("None");
    templateComboBox.setPlaceholder(
        "Select a template to auto-populate attributes");
    templateComboBox.addValueChangeListener(
        e -> applyTemplate(e.getValue()));

    // Parent DN combo (shown when template has parentFilter)
    parentDnCombo = new ComboBox<>("Parent DN");
    parentDnCombo.setWidthFull();
    parentDnCombo.setPlaceholder("Select a parent DN");
    parentDnCombo.setVisible(false);
    parentDnCombo.addValueChangeListener(e -> {
      if (e.getValue() != null) {
        parentDnField.setValue(e.getValue());
      }
    });

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

    // Template + RDN + Parent DN layout on the same row
    HorizontalLayout dnCompositionLayout = new HorizontalLayout();
    dnCompositionLayout.setWidthFull();
    dnCompositionLayout.setDefaultVerticalComponentAlignment(Alignment.BASELINE);
    dnCompositionLayout.setSpacing(false);

    // Template dropdown (to the left)
    templateComboBox.setWidth("200px");
    templateComboBox.setClearButtonVisible(true);

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
    
    // Spacer between template dropdown and RDN
    Span templateSpacer = new Span();
    templateSpacer.getStyle()
        .set("min-width", "var(--lumo-space-l)");

    dnCompositionLayout.add(templateComboBox,
        templateSpacer,
        rdnField, commaSeparator, parentDnLayout,
        parentDnCombo);
    dnCompositionLayout.setFlexGrow(1, parentDnLayout);
    dnCompositionLayout.setFlexGrow(1, parentDnCombo);

    // Button layout
    HorizontalLayout buttonLayout = new HorizontalLayout();
    buttonLayout.setSpacing(true);
    buttonLayout.add(addRowButton, clearButton, createButton);

    add(titleLayout, infoText, dnCompositionLayout,
        dnField, attributeGrid, buttonLayout);
    setFlexGrow(1, attributeGrid);
  }

  private TextField createAttributeNameField(AttributeRow row) {
    TextField nameField = new TextField();
    nameField.setWidthFull();
    nameField.setPlaceholder("e.g., objectClass, cn, mail");
    if (row.getDisplayName() != null
        && !row.getDisplayName().isEmpty()) {
      nameField.setValue(row.getDisplayName());
      nameField.setReadOnly(true);
      nameField.setTooltipText(row.getAttributeName());
    } else {
      nameField.setValue(
          row.getAttributeName() != null
              ? row.getAttributeName() : "");
      nameField.addValueChangeListener(
          e -> row.setAttributeName(e.getValue()));
    }
    return nameField;
  }

  private com.vaadin.flow.component.Component createAttributeValueField(
      AttributeRow row) {
    if (row.getFieldType() != null
        && row.getFieldType() != FieldType.TEXT) {
      com.vaadin.flow.component.Component field =
          com.ldapbrowser.ui.components.TemplateFieldFactory
              .createField(row.getFieldType(),
                  row.getAttributeValue(), row.getSelectValues());
      if (field instanceof com.vaadin.flow.component.HasValue<?, ?> hv) {
        @SuppressWarnings("unchecked")
        com.vaadin.flow.component.HasValue<?, String> typedHv =
            (com.vaadin.flow.component.HasValue<?, String>) hv;
        typedHv.addValueChangeListener(
            e -> row.setAttributeValue(
                e.getValue() != null ? e.getValue().toString() : ""));
      }
      return field;
    }
    TextField valueField = new TextField();
    valueField.setWidthFull();
    valueField.setPlaceholder("Enter attribute value");
    if (row.getDisplayName() != null
        && !row.getDisplayName().isEmpty()) {
      valueField.setTooltipText("Enter a single value");
    }
    valueField.setValue(
        row.getAttributeValue() != null
            ? row.getAttributeValue() : "");
    valueField.addValueChangeListener(
        e -> row.setAttributeValue(e.getValue()));
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
      NotificationHelper.showError("Please select a server from the top menu");
      return;
    }

    if (selectedServers.size() > 1) {
      NotificationHelper.showError("Please select only one server to create entries");
      return;
    }

    String selectedServer = selectedServers.iterator().next();
    LdapServerConfig serverConfig = configService.loadConfigurations().stream()
        .filter(config -> config.getName().equals(selectedServer))
        .findFirst()
        .orElse(null);

    if (serverConfig == null) {
      NotificationHelper.showError("Selected server configuration not found");
      return;
    }

    String dn = dnField.getValue();
    if (dn == null || dn.trim().isEmpty()) {
      NotificationHelper.showError("Distinguished Name (DN) is required");
      return;
    }

    // Collect attributes
    Map<String, List<String>> attributes = new LinkedHashMap<>();
    boolean hasValidAttributes = false;

    for (AttributeRow row : attributeRows) {
      String name = row.getAttributeName();
      String value = row.getAttributeValue();

      if (name != null && !name.trim().isEmpty()
          && value != null && !value.trim().isEmpty()) {
        // MULTI_VALUED_TEXT: split by newline into separate values
        List<String> vals;
        if (row.getFieldType() == FieldType.MULTI_VALUED_TEXT) {
          vals = com.ldapbrowser.ui.components
              .TemplateFieldFactory.getMultiValues(value);
        } else {
          vals = List.of(value.trim());
        }
        // Support comma-separated attribute names
        for (String singleName : name.split(",")) {
          singleName = singleName.trim();
          if (!singleName.isEmpty()) {
            for (String v : vals) {
              attributes.computeIfAbsent(
                  singleName, k -> new ArrayList<>()).add(v);
              hasValidAttributes = true;
            }
          }
        }
      }
    }

    // Include hidden template attributes
    for (AttributeRow row : hiddenAttributeRows) {
      String name = row.getAttributeName();
      String value = row.getAttributeValue();
      if (name != null && !name.trim().isEmpty()
          && value != null && !value.trim().isEmpty()) {
        value = value.trim();
        for (String singleName : name.split(",")) {
          singleName = singleName.trim();
          if (!singleName.isEmpty()) {
            attributes.computeIfAbsent(
                singleName, k -> new ArrayList<>()).add(value);
            hasValidAttributes = true;
          }
        }
      }
    }

    if (!hasValidAttributes) {
      NotificationHelper.showError("At least one attribute with a valid name and value is required");
      return;
    }

    // Check if objectClass is specified
    if (!attributes.containsKey("objectClass")) {
      NotificationHelper.showError("The 'objectClass' attribute is required for LDAP entries");
      return;
    }

    try {
      // Create LdapEntry
      LdapEntry newEntry = new LdapEntry();
      newEntry.setDn(dn.trim());
      newEntry.setAttributes(attributes);

      // Add the entry using LdapService
      ldapService.addEntry(serverConfig, newEntry);

      NotificationHelper.showSuccess("Entry created successfully: " + dn);

      // Clear form after successful creation
      clearAll();

    } catch (LDAPException e) {
      NotificationHelper.showError("Failed to create entry: " + e.getMessage());
    } catch (Exception e) {
      NotificationHelper.showError("Unexpected error: " + e.getMessage());
    }
  }

  private void applyTemplate(String templateName) {
    if (templateName == null || "None".equals(templateName)) {
      activeTemplate = null;
      hiddenAttributeRows.clear();
      showManualParentDn(true);
      return;
    }

    // Find the matching configured template
    EntryTemplate tmpl = createTemplates.stream()
        .filter(t -> t.getName().equals(templateName))
        .findFirst()
        .orElse(null);
    activeTemplate = tmpl;

    // Clear existing attributes but keep non-empty user-entered ones
    List<AttributeRow> existingRows = new ArrayList<>();
    for (AttributeRow row : attributeRows) {
      if (row.getAttributeName() != null
          && !row.getAttributeName().trim().isEmpty()
          && row.getAttributeValue() != null
          && !row.getAttributeValue().trim().isEmpty()) {
        existingRows.add(row);
      }
    }
    attributeRows.clear();
    hiddenAttributeRows.clear();
    attributeRows.addAll(existingRows);

    if (tmpl == null || tmpl.getCreateSection() == null) {
      showManualParentDn(true);
      addEmptyRow();
      refreshGrid();
      return;
    }

    CreateTemplateSection cs = tmpl.getCreateSection();

    // Apply RDN pattern
    if (cs.getRdn() != null && !cs.getRdn().isEmpty()) {
      int braceIndex = cs.getRdn().indexOf('{');
      if (braceIndex > 0) {
        rdnField.setValue(cs.getRdn().substring(0, braceIndex));
      } else {
        rdnField.setValue(cs.getRdn());
      }
      rdnField.setPlaceholder(cs.getRdn());
    }

    // Apply parent filter
    if (cs.getParentFilter() != null
        && !cs.getParentFilter().isEmpty()) {
      resolveParentDnCandidates(
          cs.getParentFilter(), cs.getBaseDn());
      showManualParentDn(false);
    } else {
      showManualParentDn(true);
    }

    // Apply attributes from template
    for (TemplateAttribute attr : cs.getAttributes()) {
      List<String> values = attr.getValues();
      String firstVal = values.isEmpty() ? "" : values.get(0);
      if (attr.isHidden()) {
        // Hidden: store separately, injected at create time
        for (String v : values) {
          hiddenAttributeRows.add(
              new AttributeRow(attr.getLdapAttributeName(), v));
        }
      } else if (attr.getFieldType() == FieldType.SELECT_LIST) {
        // SELECT_LIST: single row with dropdown, values are options
        addTemplateAttributeWithType(
            attr.getLdapAttributeName(), firstVal,
            attr.getFieldType(), values,
            attr.getDisplayName());
      } else if (attr.getFieldType() == FieldType.SEARCH) {
        // SEARCH: resolve base/filter to list of DNs
        // Rejoin values in case commas in DN caused incorrect split
        String baseFilter = String.join(",", values);
        List<String> dnResults =
            resolveSearchDnCandidates(baseFilter);
        addTemplateAttributeWithType(
            attr.getLdapAttributeName(), "",
            attr.getFieldType(), dnResults,
            attr.getDisplayName());
      } else if (attr.getFieldType() == FieldType.TEXT
          || attr.getFieldType() == null) {
        // TEXT type: use joined values as a single placeholder
        String joined = String.join(",", values);
        addTemplateAttributeWithType(
            attr.getLdapAttributeName(), joined,
            attr.getFieldType(), values,
            attr.getDisplayName());
      } else {
        addTemplateAttributeWithType(
            attr.getLdapAttributeName(), firstVal,
            attr.getFieldType(), values,
            attr.getDisplayName());
        // Extra rows for multi-valued attrs
        for (int i = 1; i < values.size(); i++) {
          AttributeRow extraRow =
              new AttributeRow(attr.getLdapAttributeName(),
                  values.get(i));
          extraRow.setDisplayName(attr.getDisplayName());
          attributeRows.add(extraRow);
        }
      }
    }

    addEmptyRow();
    refreshGrid();
  }

  private void loadTemplateItems() {
    List<String> items = new ArrayList<>();
    items.add("None");
    try {
      LdapServerConfig serverCfg = getSelectedServerConfig();
      List<EntryTemplate> all =
          (serverCfg != null)
              ? templateService.getTemplatesForServer(serverCfg)
              : templateService.loadTemplates();
      createTemplates = all.stream()
          .filter(t -> t.getCreateSection() != null)
          .toList();
      for (EntryTemplate t : createTemplates) {
        items.add(t.getName());
      }
    } catch (Exception e) {
      // templates file may not exist yet
    }
    templateComboBox.setItems(items);
  }

  /**
   * Gets the config for the first currently selected server.
   */
  private LdapServerConfig getSelectedServerConfig() {
    Set<String> names = MainLayout.getSelectedServers();
    if (names == null || names.isEmpty()) {
      return null;
    }
    String firstName = names.iterator().next();
    return configService.loadConfigurations().stream()
        .filter(c -> firstName.equals(c.getName()))
        .findFirst()
        .orElse(null);
  }

  /**
   * Shows or hides the manual parent DN field vs combo.
   */
  private void showManualParentDn(boolean showManual) {
    parentDnField.getParent().ifPresent(
        p -> p.setVisible(showManual));
    parentDnBrowseButton.setVisible(showManual);
    parentDnCombo.setVisible(!showManual);
  }

  /**
   * Resolves parent DN candidates from an LDAP filter.
   * If templateBaseDn names a key in the server's otherBases,
   * that DN is used as the search base instead of naming contexts.
   */
  private void resolveParentDnCandidates(
      String filter, String templateBaseDn) {
    Set<String> selectedServerNames = MainLayout.getSelectedServers();
    if (selectedServerNames == null || selectedServerNames.isEmpty()) {
      return;
    }
    List<LdapServerConfig> configs =
        configService.loadConfigurations().stream()
            .filter(c -> selectedServerNames.contains(c.getName()))
            .toList();

    boolean useNamedBase = templateBaseDn != null
        && !templateBaseDn.trim().isEmpty()
        && !"Default".equalsIgnoreCase(templateBaseDn.trim());

    List<String> candidates = new ArrayList<>();
    for (LdapServerConfig cfg : configs) {
      try {
        List<String> bases;
        if (useNamedBase) {
          String namedDn = cfg.getOtherBases() != null
              ? cfg.getOtherBases().get(templateBaseDn.trim())
              : null;
          if (namedDn != null && !namedDn.isEmpty()) {
            bases = List.of(namedDn);
          } else {
            bases = ldapService.getNamingContexts(cfg);
          }
        } else {
          bases = ldapService.getNamingContexts(cfg);
        }
        for (String base : bases) {
          List<LdapEntry> results =
              ldapService.search(cfg, base, filter,
                  SearchScope.SUB, "dn");
          for (LdapEntry entry : results) {
            candidates.add(entry.getDn());
          }
        }
      } catch (Exception e) {
        // skip servers that fail
      }
    }
    parentDnCombo.setItems(candidates);
    if (candidates.size() == 1) {
      parentDnCombo.setValue(candidates.get(0));
    }
  }

  /**
   * Resolves a SEARCH type attribute value containing a
   * {@code <base>/<filter>} string into a list of DNs.
   *
   * <p>Base resolution rules:
   * <ul>
   *   <li>Blank base: uses server default search base
   *   <li>Named base: looked up in server config Other Bases
   *   <li>DN base: used directly as the search base
   * </ul>
   *
   * @param baseFilter the value in the form {@code base/filter}
   * @return list of DNs matching the search
   */
  private List<String> resolveSearchDnCandidates(
      String baseFilter) {
    List<String> results = new ArrayList<>();
    if (baseFilter == null || baseFilter.isEmpty()) {
      return results;
    }

    // Split on the first '/' to separate base from filter
    int slashIdx = baseFilter.indexOf('/');
    if (slashIdx < 0) {
      return results;
    }
    String basePart = baseFilter.substring(0, slashIdx).trim();
    String filterPart = baseFilter.substring(slashIdx + 1).trim();
    if (filterPart.isEmpty()) {
      return results;
    }

    LdapServerConfig serverCfg = getSelectedServerConfig();
    if (serverCfg == null) {
      return results;
    }

    try {
      String searchBase;
      if (basePart.isEmpty()) {
        // Use server's default base DN
        searchBase = serverCfg.getBaseDn();
        if (searchBase == null || searchBase.isEmpty()) {
          List<String> contexts =
              ldapService.getNamingContexts(serverCfg);
          searchBase = contexts.isEmpty() ? "" : contexts.get(0);
        }
      } else {
        // Check if basePart is a named base from Other Bases
        String namedDn = serverCfg.getOtherBases() != null
            ? serverCfg.getOtherBases().get(basePart) : null;
        if (namedDn != null && !namedDn.isEmpty()) {
          searchBase = namedDn;
        } else {
          // Use as literal DN
          searchBase = basePart;
        }
      }

      // Search returning only DNs (1.1 = no attributes)
      List<LdapEntry> entries = ldapService.search(
          serverCfg, searchBase, filterPart,
          SearchScope.SUB, "1.1");
      for (LdapEntry entry : entries) {
        results.add(entry.getDn());
      }
    } catch (Exception e) {
      // silently return empty results on error
    }

    return results;
  }

  private void addTemplateAttribute(String name, String value) {
    // Check if attribute already exists to avoid duplicates
    boolean exists = attributeRows.stream()
        .anyMatch(row -> name.equals(row.getAttributeName()));

    if (!exists) {
      attributeRows.add(new AttributeRow(name, value));
    }
  }

  private void addTemplateAttributeWithType(String name, String value,
      FieldType fieldType, List<String> selectValues,
      String displayName) {
    boolean exists = attributeRows.stream()
        .anyMatch(row -> name.equals(row.getAttributeName()));
    if (!exists) {
      AttributeRow row = new AttributeRow(name, value);
      row.setFieldType(fieldType);
      row.setSelectValues(selectValues);
      row.setDisplayName(displayName);
      attributeRows.add(row);
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
      NotificationHelper.showError("Please select at least one server from the top menu");
      return;
    }

    // Get server configurations
    List<LdapServerConfig> selectedConfigs = configService.loadConfigurations().stream()
        .filter(config -> selectedServerNames.contains(config.getName()))
        .toList();

    if (selectedConfigs.isEmpty()) {
      NotificationHelper.showError("Selected servers not found in configuration");
      return;
    }

    new DnBrowserDialog(ldapService, truststoreService, "Select Parent DN")
        .withServerConfigs(selectedConfigs)
        .withValidation(
            entry -> isValidDnForParent(entry),
            "Please select a valid DN (not a server or Root DSE)"
        )
        .onDnSelected(dn -> parentDnField.setValue(dn))
        .open();
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
    parentDnCombo.clear();
    showManualParentDn(true);
    templateComboBox.setValue("None");
    activeTemplate = null;
    attributeRows.clear();
    hiddenAttributeRows.clear();
    addEmptyRow(); // Add one empty row
  }

  /**
   * Data class for attribute rows in the grid
   */
  public static class AttributeRow {
    private String attributeName;
    private String attributeValue;
    private String displayName;
    private FieldType fieldType;
    private List<String> selectValues;

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

    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }

    public FieldType getFieldType() {
      return fieldType;
    }

    public void setFieldType(FieldType fieldType) {
      this.fieldType = fieldType;
    }

    public List<String> getSelectValues() {
      return selectValues;
    }

    public void setSelectValues(List<String> selectValues) {
      this.selectValues = selectValues;
    }
  }
}