package com.ldapbrowser.ui.dialogs;

import com.ldapbrowser.model.EntryTemplate;
import com.ldapbrowser.model.EntryTemplate.CreateTemplateSection;
import com.ldapbrowser.model.EntryTemplate.FieldType;
import com.ldapbrowser.model.EntryTemplate.SearchTemplateSection;
import com.ldapbrowser.model.EntryTemplate.TemplateAttribute;
import com.ldapbrowser.model.EntryTemplate.ViewEditTemplateSection;
import com.ldapbrowser.service.TemplateService;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for creating and editing entry templates.
 * Contains tabs for Create, View/Edit, and Search sections.
 */
public class TemplateEditorDialog extends Dialog {

  private final TemplateService templateService;
  private final String originalName;
  private final boolean isEdit;
  private Consumer<EntryTemplate> onSave;

  // Template name
  private TextField nameField;

  // Create section
  private Checkbox createEnabled;
  private TextField rdnField;
  private TextField parentFilterField;
  private Grid<TemplateAttribute> createAttributeGrid;
  private List<TemplateAttribute> createAttributes = new ArrayList<>();

  // View/Edit section
  private Checkbox viewEditEnabled;
  private TextField matchingFilterField;
  private Grid<TemplateAttribute> viewEditAttributeGrid;
  private List<TemplateAttribute> viewEditAttributes = new ArrayList<>();

  // Search section
  private Checkbox searchEnabled;
  private TextField searchFilterField;
  private TextField baseFilterField;
  private Select<String> scopeSelect;
  private TextField returnAttributesField;

  /**
   * Creates a template editor dialog for a new template.
   *
   * @param templateService template service
   */
  public TemplateEditorDialog(TemplateService templateService) {
    this(templateService, null);
  }

  /**
   * Creates a template editor dialog.
   *
   * @param templateService template service
   * @param template existing template to edit, or null for new
   */
  public TemplateEditorDialog(TemplateService templateService,
      EntryTemplate template) {
    this.templateService = templateService;
    this.isEdit = template != null;
    this.originalName = isEdit ? template.getName() : null;

    setHeaderTitle(isEdit ? "Edit Template" : "New Template");
    setWidth("900px");
    setHeight("700px");
    setModal(true);
    setDraggable(true);
    setResizable(true);

    VerticalLayout content = new VerticalLayout();
    content.setSizeFull();
    content.setPadding(false);
    content.setSpacing(true);

    // Template name
    nameField = new TextField("Template Name");
    nameField.setWidthFull();
    nameField.setRequired(true);
    nameField.setPlaceholder("e.g., User, Group, OU");
    content.add(nameField);

    // Section tabs
    TabSheet sections = new TabSheet();
    sections.setSizeFull();
    sections.add("Create", buildCreateTab());
    sections.add("View / Edit", buildViewEditTab());
    sections.add("Search", buildSearchTab());
    content.add(sections);
    content.expand(sections);

    add(content);

    // Footer buttons
    Button saveButton = new Button("Save", e -> save());
    saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    Button cancelButton = new Button("Cancel", e -> close());
    getFooter().add(cancelButton, saveButton);

    // Populate if editing
    if (template != null) {
      populateFrom(template);
    }
  }

  /**
   * Sets the callback invoked after a successful save.
   *
   * @param onSave save callback
   * @return this dialog for chaining
   */
  public TemplateEditorDialog onSave(Consumer<EntryTemplate> onSave) {
    this.onSave = onSave;
    return this;
  }

  // ----- Create tab ----------------------------------------------------

  private VerticalLayout buildCreateTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(false);
    layout.setSpacing(true);
    layout.setSizeFull();

    createEnabled = new Checkbox("Enable Create section");
    createEnabled.setValue(false);

    rdnField = new TextField("RDN Pattern");
    rdnField.setPlaceholder("uid={UID}");
    rdnField.setHelperText(
        "Use {FIELD} placeholders matching attribute names");

    parentFilterField = new TextField("Parent Filter");
    parentFilterField.setPlaceholder(
        "(&(objectClass=organizationalUnit)(ou=people))");
    parentFilterField.setHelperText(
        "LDAP filter to find parent DN candidates");

    HorizontalLayout rdnParentRow = new HorizontalLayout();
    rdnParentRow.setWidthFull();
    rdnParentRow.setDefaultVerticalComponentAlignment(
        com.vaadin.flow.component.orderedlayout.FlexComponent
            .Alignment.BASELINE);
    rdnParentRow.add(rdnField, parentFilterField);
    rdnParentRow.setFlexGrow(1, rdnField);
    rdnParentRow.setFlexGrow(2, parentFilterField);

    createAttributeGrid = buildAttributeGrid(createAttributes, true);

    HorizontalLayout gridButtons = buildGridButtons(
        createAttributes, createAttributeGrid);

    createEnabled.addValueChangeListener(e -> {
      boolean enabled = Boolean.TRUE.equals(e.getValue());
      rdnField.setEnabled(enabled);
      parentFilterField.setEnabled(enabled);
      createAttributeGrid.setEnabled(enabled);
    });
    // Start disabled
    rdnField.setEnabled(false);
    parentFilterField.setEnabled(false);
    createAttributeGrid.setEnabled(false);

    layout.add(createEnabled, rdnParentRow,
        gridButtons, createAttributeGrid);
    layout.expand(createAttributeGrid);
    return layout;
  }

  // ----- View/Edit tab -------------------------------------------------

  private VerticalLayout buildViewEditTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(false);
    layout.setSpacing(true);
    layout.setSizeFull();

    viewEditEnabled = new Checkbox("Enable View/Edit section");
    viewEditEnabled.setValue(false);

    matchingFilterField = new TextField("Matching Filter");
    matchingFilterField.setWidthFull();
    matchingFilterField.setPlaceholder(
        "(objectClass=inetOrgPerson)");
    matchingFilterField.setHelperText(
        "LDAP filter to match entries for this template");

    viewEditAttributeGrid =
        buildAttributeGrid(viewEditAttributes, true);

    HorizontalLayout gridButtons = buildGridButtons(
        viewEditAttributes, viewEditAttributeGrid);

    viewEditEnabled.addValueChangeListener(e -> {
      boolean enabled = Boolean.TRUE.equals(e.getValue());
      matchingFilterField.setEnabled(enabled);
      viewEditAttributeGrid.setEnabled(enabled);
    });
    matchingFilterField.setEnabled(false);
    viewEditAttributeGrid.setEnabled(false);

    layout.add(viewEditEnabled, matchingFilterField,
        gridButtons, viewEditAttributeGrid);
    layout.expand(viewEditAttributeGrid);
    return layout;
  }

  // ----- Search tab ----------------------------------------------------

  private VerticalLayout buildSearchTab() {
    VerticalLayout layout = new VerticalLayout();
    layout.setPadding(false);
    layout.setSpacing(true);

    searchEnabled = new Checkbox("Enable Search section");
    searchEnabled.setValue(false);

    searchFilterField = new TextField("Search Filter");
    searchFilterField.setWidthFull();
    searchFilterField.setPlaceholder(
        "(&(objectClass=inetOrgPerson)(uid={SEARCH}))");
    searchFilterField.setHelperText(
        "{SEARCH} is replaced with the user's search text");

    baseFilterField = new TextField("Base Filter");
    baseFilterField.setWidthFull();
    baseFilterField.setPlaceholder(
        "(objectClass=organizationalUnit)");
    baseFilterField.setHelperText(
        "LDAP filter to find base DNs for the search");

    scopeSelect = new Select<>();
    scopeSelect.setLabel("Scope");
    scopeSelect.setItems("base", "one", "sub");
    scopeSelect.setValue("sub");
    scopeSelect.setWidth("150px");

    returnAttributesField = new TextField("Return Attributes");
    returnAttributesField.setWidthFull();
    returnAttributesField.setPlaceholder("cn,uid,mail");
    returnAttributesField.setHelperText(
        "Comma-separated list of attributes to return");

    searchEnabled.addValueChangeListener(e -> {
      boolean enabled = Boolean.TRUE.equals(e.getValue());
      searchFilterField.setEnabled(enabled);
      baseFilterField.setEnabled(enabled);
      scopeSelect.setEnabled(enabled);
      returnAttributesField.setEnabled(enabled);
    });
    searchFilterField.setEnabled(false);
    baseFilterField.setEnabled(false);
    scopeSelect.setEnabled(false);
    returnAttributesField.setEnabled(false);

    layout.add(searchEnabled, searchFilterField, baseFilterField,
        scopeSelect, returnAttributesField);
    return layout;
  }

  // ----- Shared attribute grid builder ---------------------------------

  private Grid<TemplateAttribute> buildAttributeGrid(
      List<TemplateAttribute> dataList, boolean showHidden) {
    Grid<TemplateAttribute> grid =
        new Grid<>(TemplateAttribute.class, false);
    grid.setSizeFull();

    grid.addColumn(new ComponentRenderer<>(attr -> {
      TextField tf = new TextField();
      tf.setWidthFull();
      tf.setValue(attr.getDisplayName() != null
          ? attr.getDisplayName() : "");
      tf.setPlaceholder("Display Name");
      tf.addValueChangeListener(e ->
          attr.setDisplayName(e.getValue()));
      return tf;
    })).setHeader("Display Name").setFlexGrow(2);

    grid.addColumn(new ComponentRenderer<>(attr -> {
      TextField tf = new TextField();
      tf.setWidthFull();
      tf.setValue(attr.getLdapAttributeName() != null
          ? attr.getLdapAttributeName() : "");
      tf.setPlaceholder("LDAP Attribute");
      tf.addValueChangeListener(e ->
          attr.setLdapAttributeName(e.getValue()));
      return tf;
    })).setHeader("LDAP Attribute").setFlexGrow(2);

    grid.addColumn(new ComponentRenderer<>(attr -> {
      Checkbox cb = new Checkbox();
      cb.setValue(attr.isRequired());
      cb.addValueChangeListener(e ->
          attr.setRequired(Boolean.TRUE.equals(e.getValue())));
      return cb;
    })).setHeader("Req").setFlexGrow(0).setWidth("60px");

    grid.addColumn(new ComponentRenderer<>(attr -> {
      Select<FieldType> sel = new Select<>();
      sel.setItems(FieldType.values());
      sel.setValue(attr.getFieldType());
      sel.setItemLabelGenerator(ft -> {
        switch (ft) {
          case TEXT: return "Text";
          case MULTI_VALUED_TEXT: return "Multi";
          case BOOLEAN: return "Bool";
          case SELECT_LIST: return "Select";
          case PASSWORD: return "Password";
          default: return ft.name();
        }
      });
      sel.addValueChangeListener(e ->
          attr.setFieldType(e.getValue()));
      return sel;
    })).setHeader("Type").setFlexGrow(0).setWidth("120px");

    if (showHidden) {
      grid.addColumn(new ComponentRenderer<>(attr -> {
        Checkbox cb = new Checkbox();
        cb.setValue(attr.isHidden());
        cb.addValueChangeListener(e ->
            attr.setHidden(Boolean.TRUE.equals(e.getValue())));
        return cb;
      })).setHeader("Hidden").setFlexGrow(0).setWidth("70px");
    }

    grid.addColumn(new ComponentRenderer<>(attr -> {
      TextField tf = new TextField();
      tf.setWidthFull();
      tf.setValue(String.join(",", attr.getValues()));
      tf.setPlaceholder("val1,val2");
      tf.addValueChangeListener(e -> {
        String v = e.getValue();
        if (v == null || v.trim().isEmpty()) {
          attr.setValues(new ArrayList<>());
        } else {
          attr.setValues(new ArrayList<>(
              Arrays.asList(v.split(",", -1))));
        }
      });
      return tf;
    })).setHeader("Values").setFlexGrow(2);

    grid.addColumn(new ComponentRenderer<>(attr -> {
      Button del = new Button(new Icon(VaadinIcon.TRASH));
      del.addThemeVariants(ButtonVariant.LUMO_SMALL,
          ButtonVariant.LUMO_TERTIARY,
          ButtonVariant.LUMO_ERROR);
      del.addClickListener(e -> {
        dataList.remove(attr);
        grid.setItems(dataList);
      });
      return del;
    })).setHeader("").setFlexGrow(0).setWidth("60px");

    grid.setItems(dataList);
    return grid;
  }

  private HorizontalLayout buildGridButtons(
      List<TemplateAttribute> dataList,
      Grid<TemplateAttribute> grid) {
    Button addRow = new Button("Add Row", new Icon(VaadinIcon.PLUS));
    addRow.addThemeVariants(ButtonVariant.LUMO_SMALL);
    addRow.addClickListener(e -> {
      dataList.add(new TemplateAttribute());
      grid.setItems(dataList);
    });

    Span hint = new Span("Define attributes for this section");
    hint.getStyle().set("color", "var(--lumo-secondary-text-color)")
        .set("font-size", "var(--lumo-font-size-s)");

    HorizontalLayout row = new HorizontalLayout(addRow, hint);
    row.setDefaultVerticalComponentAlignment(
        HorizontalLayout.Alignment.CENTER);
    return row;
  }

  // ----- Populate from existing template -------------------------------

  private void populateFrom(EntryTemplate template) {
    nameField.setValue(template.getName());

    if (template.getCreateSection() != null) {
      createEnabled.setValue(true);
      CreateTemplateSection cs = template.getCreateSection();
      rdnField.setValue(
          cs.getRdn() != null ? cs.getRdn() : "");
      parentFilterField.setValue(
          cs.getParentFilter() != null ? cs.getParentFilter() : "");
      createAttributes.addAll(deepCopyAttributes(cs.getAttributes()));
      createAttributeGrid.setItems(createAttributes);
    }

    if (template.getViewEditSection() != null) {
      viewEditEnabled.setValue(true);
      ViewEditTemplateSection vs = template.getViewEditSection();
      matchingFilterField.setValue(
          vs.getMatchingFilter() != null
              ? vs.getMatchingFilter() : "");
      viewEditAttributes.addAll(
          deepCopyAttributes(vs.getAttributes()));
      viewEditAttributeGrid.setItems(viewEditAttributes);
    }

    if (template.getSearchSection() != null) {
      searchEnabled.setValue(true);
      SearchTemplateSection ss = template.getSearchSection();
      searchFilterField.setValue(
          ss.getSearchFilter() != null
              ? ss.getSearchFilter() : "");
      baseFilterField.setValue(
          ss.getBaseFilter() != null ? ss.getBaseFilter() : "");
      scopeSelect.setValue(
          ss.getScope() != null ? ss.getScope() : "sub");
      returnAttributesField.setValue(
          ss.getReturnAttributes() != null
              ? String.join(",", ss.getReturnAttributes()) : "");
    }
  }

  private List<TemplateAttribute> deepCopyAttributes(
      List<TemplateAttribute> src) {
    List<TemplateAttribute> copy = new ArrayList<>();
    if (src == null) {
      return copy;
    }
    for (TemplateAttribute a : src) {
      TemplateAttribute c = new TemplateAttribute();
      c.setDisplayName(a.getDisplayName());
      c.setLdapAttributeName(a.getLdapAttributeName());
      c.setRequired(a.isRequired());
      c.setFieldType(a.getFieldType());
      c.setHidden(a.isHidden());
      c.setValues(new ArrayList<>(a.getValues()));
      copy.add(c);
    }
    return copy;
  }

  // ----- Save ----------------------------------------------------------

  private void save() {
    String name = nameField.getValue();
    if (name == null || name.trim().isEmpty()) {
      NotificationHelper.showError("Template name is required");
      return;
    }
    name = name.trim();

    // Check uniqueness (allow same name when editing)
    if (!name.equals(originalName)
        && templateService.templateExists(name)) {
      NotificationHelper.showError(
          "A template with this name already exists");
      return;
    }

    EntryTemplate template = new EntryTemplate(name);

    // Create section
    if (Boolean.TRUE.equals(createEnabled.getValue())) {
      CreateTemplateSection cs = new CreateTemplateSection();
      cs.setRdn(rdnField.getValue());
      cs.setParentFilter(parentFilterField.getValue());
      cs.setAttributes(new ArrayList<>(createAttributes));
      template.setCreateSection(cs);
    }

    // View/Edit section
    if (Boolean.TRUE.equals(viewEditEnabled.getValue())) {
      ViewEditTemplateSection vs = new ViewEditTemplateSection();
      vs.setMatchingFilter(matchingFilterField.getValue());
      vs.setAttributes(new ArrayList<>(viewEditAttributes));
      template.setViewEditSection(vs);
    }

    // Search section
    if (Boolean.TRUE.equals(searchEnabled.getValue())) {
      SearchTemplateSection ss = new SearchTemplateSection();
      ss.setSearchFilter(searchFilterField.getValue());
      ss.setBaseFilter(baseFilterField.getValue());
      ss.setScope(scopeSelect.getValue());
      String retAttrs = returnAttributesField.getValue();
      if (retAttrs != null && !retAttrs.trim().isEmpty()) {
        ss.setReturnAttributes(new ArrayList<>(
            Arrays.asList(retAttrs.split(",", -1))));
      }
      template.setSearchSection(ss);
    }

    try {
      // If renaming, delete old entry first
      if (isEdit && originalName != null
          && !originalName.equals(name)) {
        templateService.deleteTemplate(originalName);
      }
      templateService.saveTemplate(template);
      NotificationHelper.showSuccess(
          "Template '" + name + "' saved");
      if (onSave != null) {
        onSave.accept(template);
      }
      close();
    } catch (Exception e) {
      NotificationHelper.showError(
          "Error saving template: " + e.getMessage());
    }
  }
}
