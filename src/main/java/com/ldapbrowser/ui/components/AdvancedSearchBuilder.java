package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.unboundid.ldap.sdk.Filter;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Advanced Search Builder component for constructing complex LDAP search filters.
 * Provides a visual interface for building filters with multiple groups and units.
 */
public class AdvancedSearchBuilder extends VerticalLayout {

  // Common LDAP attributes
  private static final String[] COMMON_ATTRIBUTES = {
      "cn", "sn", "givenName", "mail", "uid", "ou", "dc", "telephoneNumber",
      "description", "objectClass", "member", "memberOf", "distinguishedName"
  };

  /**
   * Logical operators for combining filter expressions.
   */
  public enum LogicalOperator {
    AND("&", "AND"),
    OR("|", "OR"),
    NOT("!", "NOT");

    private final String symbol;
    private final String displayName;

    LogicalOperator(String symbol, String displayName) {
      this.symbol = symbol;
      this.displayName = displayName;
    }

    public String getSymbol() {
      return symbol;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  /**
   * Filter operators for individual filter expressions.
   */
  public enum FilterOperator {
    EQUALS("=", "Equals"),
    NOT_EQUALS("!=", "Not Equals"),
    GREATER_EQUAL(">=", "Greater or Equal"),
    LESS_EQUAL("<=", "Less or Equal"),
    STARTS_WITH("=*", "Starts With"),
    ENDS_WITH("*=", "Ends With"),
    CONTAINS("=**=", "Contains"),
    EXISTS("=*", "Exists"),
    NOT_EXISTS("!=*", "Does Not Exist");

    private final String symbol;
    private final String displayName;

    FilterOperator(String symbol, String displayName) {
      this.symbol = symbol;
      this.displayName = displayName;
    }

    public String getSymbol() {
      return symbol;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  private final LdapService ldapService;

  // UI Components
  private TextField searchBaseField;
  private ComboBox<LogicalOperator> rootLogicalOperator;
  private VerticalLayout filterGroupsContainer;
  private List<FilterGroup> filterGroups;
  private Button addFilterGroupButton;
  private TextArea generatedFilterArea;
  private TextArea resultFilterField;
  private HorizontalLayout rootOperatorLayout;

  /**
   * Creates a reusable filter builder dialog with Apply/Cancel buttons.
   * This eliminates code duplication between SearchView and LdapTreeGrid.
   *
   * @param ldapService the LDAP service instance
   * @param title dialog title (e.g., "Filter Builder" or "Edit LDAP Filter")
   * @param existingFilter optional existing filter string to pre-populate (can be null)
   * @param searchBase optional search base DN to pre-populate (can be null)
   * @param onApply callback invoked with the generated filter when user clicks Apply
   * @return configured Dialog ready to be opened
   */
  public static Dialog createFilterBuilderDialog(
      LdapService ldapService,
      String title,
      String existingFilter,
      String searchBase,
      java.util.function.Consumer<String> onApply) {
    
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle(title);
    dialog.setWidth("900px");
    dialog.setHeight("700px");
    dialog.setModal(true);
    dialog.setCloseOnOutsideClick(false);

    AdvancedSearchBuilder filterBuilder = new AdvancedSearchBuilder(ldapService);
    filterBuilder.getStyle()
        .set("border", "1px solid var(--lumo-contrast-20pct)")
        .set("border-radius", "var(--lumo-border-radius-m)")
        .set("background-color", "var(--lumo-contrast-5pct)")
        .set("padding", "var(--lumo-space-m)");

    // Pre-populate if editing an existing filter
    if (existingFilter != null && !existingFilter.isEmpty()) {
      filterBuilder.setFilterFromString(existingFilter);
    }

    // Pre-populate search base if provided
    if (searchBase != null && !searchBase.isEmpty()) {
      filterBuilder.setSearchBase(searchBase);
    }

    filterBuilder.setSizeFull();

    // Apply button
    Button applyButton = new Button("Apply", e -> {
      String generatedFilter = filterBuilder.getGeneratedFilter();
      if (generatedFilter != null && !generatedFilter.isEmpty()) {
        onApply.accept(generatedFilter);
        dialog.close();
      } else {
        NotificationHelper.showWarning("Please build a valid filter first", 3000);
      }
    });
    applyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    // Cancel button
    Button cancelButton = new Button("Cancel", e -> dialog.close());

    dialog.getFooter().add(cancelButton, applyButton);
    dialog.add(filterBuilder);

    return dialog;
  }

  /**
   * Creates the Advanced Search Builder.
   *
   * @param ldapService LDAP service for operations
   */
  public AdvancedSearchBuilder(LdapService ldapService) {
    this.ldapService = ldapService;
    this.filterGroups = new ArrayList<>();

    initializeComponents();
    setupLayout();
    addInitialFilterGroup();
  }

  private void initializeComponents() {
    // Search base field
    searchBaseField = new TextField("Search Base");
    searchBaseField.setPlaceholder("dc=example,dc=com");
    searchBaseField.setHelperText("Enter the base DN from which to start the search. "
        + "Leave empty to search from the root of the environment");
    searchBaseField.setWidthFull();
    searchBaseField.getStyle().set("margin-bottom", "10px");

    // Root logical operator for combining filter groups
    rootLogicalOperator = new ComboBox<>("Combine Groups With");
    rootLogicalOperator.setItems(LogicalOperator.values());
    rootLogicalOperator.setValue(LogicalOperator.AND);
    rootLogicalOperator.setWidth("150px");
    rootLogicalOperator.addValueChangeListener(e -> updateGeneratedFilter());

    // Filter groups container
    filterGroupsContainer = new VerticalLayout();
    filterGroupsContainer.setPadding(false);
    filterGroupsContainer.setSpacing(true);
    filterGroupsContainer.addClassName("filter-groups-container");

    // Add filter group button
    addFilterGroupButton = new Button("Add Filter Group", new Icon(VaadinIcon.PLUS));
    addFilterGroupButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
    addFilterGroupButton.addClickListener(e -> addFilterGroup());

    // Generated Filter Area (editable)
    generatedFilterArea = new TextArea();
    generatedFilterArea.setPlaceholder("(Generated LDAP filter will appear here "
        + "and can be edited)");
    generatedFilterArea.setWidthFull();
    generatedFilterArea.setHeight("100px");
    generatedFilterArea.getStyle().set("font-family", "monospace");
    generatedFilterArea.addValueChangeListener(e -> {
      // Update the internal result filter when manually edited
      if (resultFilterField != null) {
        resultFilterField.setValue(e.getValue());
      }
    });

    // Result filter field (hidden, for internal use and compatibility)
    resultFilterField = new TextArea("Generated LDAP Filter");
    resultFilterField.setWidthFull();
    resultFilterField.setReadOnly(true);
    resultFilterField.setHeight("80px");
    resultFilterField.getStyle().set("font-family", "monospace");
    resultFilterField.setVisible(false);
  }

  private void setupLayout() {
    setSizeFull();
    setPadding(false);
    setSpacing(true);
    addClassName("advanced-search-builder");

    // Instructions
    Span instructions = new Span("Build complex LDAP search filters using filter groups. "
        + "Each group contains one or more filter units. Multiple groups can be combined with "
        + "logical operators (AND, OR, NOT).");
    instructions.addClassName("instructions");
    instructions.getStyle().set("font-size", "14px").set("color", "#666")
        .set("margin-bottom", "15px");

    // Root logical operator (only show if multiple groups)
    rootOperatorLayout = new HorizontalLayout();
    rootOperatorLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    rootOperatorLayout.add(new Span("Combine filter groups with:"), rootLogicalOperator);
    rootOperatorLayout.setVisible(false); // Initially hidden
    rootOperatorLayout.addClassName("root-operator-layout");

    // Generated filter container with copy button
    VerticalLayout generatedFilterContainer = new VerticalLayout();
    generatedFilterContainer.setPadding(false);
    generatedFilterContainer.setSpacing(false);
    generatedFilterContainer.setWidthFull();
    
    // Header with label and copy button
    HorizontalLayout filterHeader = new HorizontalLayout();
    filterHeader.setWidthFull();
    filterHeader.setDefaultVerticalComponentAlignment(Alignment.CENTER);
    filterHeader.getStyle().set("margin-bottom", "5px");
    
    Span filterLabel = new Span("Generated LDAP Filter");
    filterLabel.getStyle().set("font-weight", "bold").set("font-size", "var(--lumo-font-size-s)");
    
    Button copyButton = new Button(new Icon(VaadinIcon.COPY));
    copyButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
    copyButton.setTooltipText("Copy filter to clipboard");
    copyButton.addClickListener(e -> {
      String filter = generatedFilterArea.getValue();
      if (filter != null && !filter.isEmpty()) {
        getElement().executeJs(
            "navigator.clipboard.writeText($0).then(() => {}, () => {});",
            filter
        );
        NotificationHelper.showInfo("Filter copied to clipboard", 2000);
      }
    });
    
    filterHeader.add(filterLabel, copyButton);
    filterHeader.setFlexGrow(1, filterLabel);
    
    generatedFilterContainer.add(filterHeader, generatedFilterArea);
    generatedFilterContainer.getStyle().set("margin-bottom", "10px");

    add(instructions, rootOperatorLayout,
        filterGroupsContainer, addFilterGroupButton, generatedFilterContainer, resultFilterField);
  }

  private void addInitialFilterGroup() {
    addFilterGroup();
  }

  private void addFilterGroup() {
    FilterGroup group = new FilterGroup();
    filterGroups.add(group);
    filterGroupsContainer.add(group);
    updateGeneratedFilter();
    // Show root operator selector if we have more than one group
    updateRootOperatorVisibility();
  }

  private void removeFilterGroup(FilterGroup group) {
    filterGroups.remove(group);
    filterGroupsContainer.remove(group);
    updateGeneratedFilter();
    // Hide root operator selector if we only have one group
    updateRootOperatorVisibility();
  }

  private void updateRootOperatorVisibility() {
    boolean showRootOperator = filterGroups.size() > 1;
    rootOperatorLayout.setVisible(showRootOperator);
  }

  private void updateGeneratedFilter() {
    String filter = buildLdapFilter();
    resultFilterField.setValue(filter);
    // Update the editable generated filter area
    if (generatedFilterArea != null) {
      generatedFilterArea.setValue(filter);
    }
  }

  private String buildLdapFilter() {
    if (filterGroups.isEmpty()) {
      return "";
    }

    List<String> groupFilters = new ArrayList<>();
    for (FilterGroup group : filterGroups) {
      String groupFilter = group.buildFilterExpression();
      if (!groupFilter.isEmpty()) {
        groupFilters.add(groupFilter);
      }
    }

    if (groupFilters.isEmpty()) {
      return "";
    }

    if (groupFilters.size() == 1) {
      return groupFilters.get(0);
    }

    // Multiple groups - combine with root logical operator
    LogicalOperator rootOp = rootLogicalOperator.getValue();
    StringBuilder filter = new StringBuilder();

    if (rootOp == LogicalOperator.NOT) {
      // NOT operator applies to the entire combined expression
      filter.append("(!").append(buildCombinedExpression(groupFilters,
          LogicalOperator.AND)).append(")");
    } else {
      filter.append(buildCombinedExpression(groupFilters, rootOp));
    }

    return filter.toString();
  }

  private String buildCombinedExpression(List<String> expressions, LogicalOperator operator) {
    if (expressions.size() == 1) {
      return expressions.get(0);
    }

    StringBuilder result = new StringBuilder();
    result.append("(").append(operator.getSymbol());
    for (String expr : expressions) {
      result.append(expr);
    }
    result.append(")");

    return result.toString();
  }

  /**
   * Get the search base DN.
   *
   * @return the search base DN
   */
  public String getSearchBase() {
    return searchBaseField.getValue();
  }

  /**
   * Set the search base DN.
   *
   * @param searchBase the search base DN to set
   */
  public void setSearchBase(String searchBase) {
    searchBaseField.setValue(searchBase);
  }

  /**
   * Set the server configuration for the DN selector field.
   *
   * @param serverConfig the server configuration
   */
  public void setServerConfig(LdapServerConfig serverConfig) {
    // Future: if we implement DnSelectorField, set it here
  }

  /**
   * Get the generated LDAP filter.
   *
   * @return the generated LDAP filter
   */
  public String getGeneratedFilter() {
    return resultFilterField.getValue();
  }

  /**
   * Clear all search criteria.
   */
  public void clear() {
    filterGroups.clear();
    filterGroupsContainer.removeAll();
    searchBaseField.clear();
    generatedFilterArea.clear();
    resultFilterField.clear();
    addInitialFilterGroup();
  }

  /**
   * Parse and populate the filter builder from an LDAP filter string.
   * This method attempts to parse the filter and populate the visual builder.
   * 
   * @param filterString the LDAP filter string to parse (e.g., "(cn=john)")
   */
  public void setFilterFromString(String filterString) {
    if (filterString == null || filterString.trim().isEmpty()) {
      return;
    }

    // Clear existing filter groups
    filterGroups.clear();
    filterGroupsContainer.removeAll();

    try {
      // Parse the filter string and populate UI
      parseAndPopulateFilter(filterString.trim());
    } catch (Exception e) {
      // If parsing fails, fall back to manual filter text
      NotificationHelper.showWarning(
          "Could not parse filter into visual builder. "
          + "You can edit it manually in the text area.", 5000);
      generatedFilterArea.setValue(filterString.trim());
      resultFilterField.setValue(filterString.trim());
      addInitialFilterGroup();
    }
  }

  /**
   * Parse a filter string and populate the filter builder UI.
   */
  private void parseAndPopulateFilter(String filter) {
    // Remove outer parentheses if present
    String trimmed = filter.trim();
    if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }

    // Check if it's a logical operator at the root level
    if (trimmed.startsWith("&") || trimmed.startsWith("|") || trimmed.startsWith("!")) {
      char operator = trimmed.charAt(0);
      String content = trimmed.substring(1);
      
      // Set root operator
      switch (operator) {
        case '&':
          rootLogicalOperator.setValue(LogicalOperator.AND);
          break;
        case '|':
          rootLogicalOperator.setValue(LogicalOperator.OR);
          break;
        case '!':
          rootLogicalOperator.setValue(LogicalOperator.NOT);
          break;
      }

      // Parse sub-filters as separate groups
      List<String> subFilters = extractSubFilters(content);
      for (String subFilter : subFilters) {
        FilterGroup group = new FilterGroup();
        filterGroups.add(group);
        filterGroupsContainer.add(group);
        group.populateFromFilter(subFilter);
      }
    } else {
      // Single filter expression - create one group with one unit
      FilterGroup group = new FilterGroup();
      filterGroups.add(group);
      filterGroupsContainer.add(group);
      group.populateFromFilter("(" + trimmed + ")");
    }

    if (filterGroups.isEmpty()) {
      addInitialFilterGroup();
    }

    updateRootOperatorVisibility();
    updateGeneratedFilter();
  }

  /**
   * Escapes special characters in LDAP filter values according to RFC 4515.
   * 
   * @param value the raw value to escape
   * @return the escaped value safe for LDAP filters
   */
  private String escapeFilterValue(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    
    return value
        .replace("\\", "\\5c")  // Backslash must be first!
        .replace("*", "\\2a")
        .replace("(", "\\28")
        .replace(")", "\\29")
        .replace("\0", "\\00");
  }

  /**
   * Extract individual sub-filters from a compound filter.
   * Handles nested parentheses properly.
   */
  private List<String> extractSubFilters(String content) {
    List<String> filters = new ArrayList<>();
    int depth = 0;
    int startIdx = 0;

    for (int i = 0; i < content.length(); i++) {
      char c = content.charAt(i);
      if (c == '(') {
        if (depth == 0) {
          startIdx = i;
        }
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          filters.add(content.substring(startIdx, i + 1));
        }
      }
    }

    return filters;
  }

  /**
   * Filter Group - represents a logical grouping of filter units.
   */
  private class FilterGroup extends VerticalLayout {
    private ComboBox<LogicalOperator> groupLogicalOperator;
    private VerticalLayout filterUnitsContainer;
    private List<FilterUnit> filterUnits;
    private Button addFilterUnitButton;
    private Button removeGroupButton;
    private Span groupLabel;

    public FilterGroup() {
      this.filterUnits = new ArrayList<>();
      initializeGroupComponents();
      setupGroupLayout();
      addInitialFilterUnit();
    }

    private void initializeGroupComponents() {
      // Group label and controls
      groupLabel = new Span("Filter Group " + (filterGroups.size() + 1));
      groupLabel.getStyle().set("font-weight", "bold").set("color", "#1976d2");

      // Group logical operator (for combining filter units within this group)
      groupLogicalOperator = new ComboBox<>("Combine Units With");
      groupLogicalOperator.setItems(LogicalOperator.AND, LogicalOperator.OR,
          LogicalOperator.NOT);
      groupLogicalOperator.setValue(LogicalOperator.AND);
      groupLogicalOperator.setWidth("150px");
      groupLogicalOperator.addValueChangeListener(e -> updateGeneratedFilter());

      // Filter units container
      filterUnitsContainer = new VerticalLayout();
      filterUnitsContainer.setPadding(false);
      filterUnitsContainer.setSpacing(true);
      filterUnitsContainer.addClassName("filter-units-container");

      // Add filter unit button
      addFilterUnitButton = new Button("Add Filter Unit", new Icon(VaadinIcon.PLUS_CIRCLE));
      addFilterUnitButton.addThemeVariants(ButtonVariant.LUMO_SMALL,
          ButtonVariant.LUMO_TERTIARY);
      addFilterUnitButton.addClickListener(e -> addFilterUnit());

      // Force proper styling with inline styles
      addFilterUnitButton.getStyle().set("background", "transparent");
      addFilterUnitButton.getStyle().set("border", "2px solid var(--lumo-primary-color)");
      addFilterUnitButton.getStyle().set("color", "var(--lumo-primary-color)");
      addFilterUnitButton.getStyle().set("border-radius", "var(--lumo-border-radius-s)");

      // Remove group button
      removeGroupButton = new Button("Remove Group", new Icon(VaadinIcon.TRASH));
      removeGroupButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
      removeGroupButton.addClickListener(e -> removeFilterGroup(this));

      // Force proper styling with inline styles
      removeGroupButton.getStyle().set("background", "var(--lumo-error-color)");
      removeGroupButton.getStyle().set("border", "2px solid var(--lumo-error-color)");
      removeGroupButton.getStyle().set("color", "var(--lumo-error-contrast-color)");
      removeGroupButton.getStyle().set("border-radius", "var(--lumo-border-radius-s)");
    }

    private void setupGroupLayout() {
      setPadding(true);
      setSpacing(true);
      addClassName("filter-group");
      getStyle().set("border", "2px solid #e3f2fd")
          .set("border-radius", "8px")
          .set("background", "#fafafa")
          .set("margin", "8px 0");

      // Header with group label and controls
      HorizontalLayout header = new HorizontalLayout();
      header.setDefaultVerticalComponentAlignment(Alignment.CENTER);
      header.setWidthFull();
      header.add(groupLabel, groupLogicalOperator, removeGroupButton);
      header.setFlexGrow(1, groupLabel);

      add(header, filterUnitsContainer, addFilterUnitButton);
    }

    private void addInitialFilterUnit() {
      addFilterUnit();
    }

    private void addFilterUnit() {
      FilterUnit unit = new FilterUnit();
      filterUnits.add(unit);
      filterUnitsContainer.add(unit);
      updateGeneratedFilter();
    }

    private void removeFilterUnit(FilterUnit unit) {
      filterUnits.remove(unit);
      filterUnitsContainer.remove(unit);
      updateGeneratedFilter();

      // If this was the last unit, remove the group
      if (filterUnits.isEmpty()) {
        removeFilterGroup(this);
      }
    }

    public String buildFilterExpression() {
      if (filterUnits.isEmpty()) {
        return "";
      }

      List<String> unitFilters = new ArrayList<>();
      for (FilterUnit unit : filterUnits) {
        String unitFilter = unit.buildFilterExpression();
        if (!unitFilter.isEmpty()) {
          unitFilters.add(unitFilter);
        }
      }

      if (unitFilters.isEmpty()) {
        return "";
      }

      if (unitFilters.size() == 1) {
        return unitFilters.get(0);
      }

      // Multiple units - combine with group logical operator
      LogicalOperator groupOp = groupLogicalOperator.getValue();
      if (groupOp == LogicalOperator.NOT) {
        return "(!" + buildCombinedExpression(unitFilters, LogicalOperator.AND) + ")";
      } else {
        return "(" + groupOp.getSymbol() + String.join("", unitFilters) + ")";
      }
    }

    /**
     * Populate this filter group from a filter string.
     */
    public void populateFromFilter(String filter) {
      // Clear existing units (except keep structure)
      filterUnits.clear();
      filterUnitsContainer.removeAll();

      String trimmed = filter.trim();
      if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
        trimmed = trimmed.substring(1, trimmed.length() - 1);
      }

      // Check for logical operator
      if (trimmed.startsWith("&") || trimmed.startsWith("|") || trimmed.startsWith("!")) {
        char operator = trimmed.charAt(0);
        String content = trimmed.substring(1);

        // Set group operator
        switch (operator) {
          case '&':
            groupLogicalOperator.setValue(LogicalOperator.AND);
            break;
          case '|':
            groupLogicalOperator.setValue(LogicalOperator.OR);
            break;
          case '!':
            groupLogicalOperator.setValue(LogicalOperator.NOT);
            break;
        }

        // Extract and populate sub-filters
        List<String> subFilters = extractSubFilters(content);
        for (String subFilter : subFilters) {
          FilterUnit unit = new FilterUnit();
          filterUnits.add(unit);
          filterUnitsContainer.add(unit);
          unit.populateFromFilter(subFilter);
        }
      } else {
        // Single filter expression
        FilterUnit unit = new FilterUnit();
        filterUnits.add(unit);
        filterUnitsContainer.add(unit);
        unit.populateFromFilter("(" + trimmed + ")");
      }

      if (filterUnits.isEmpty()) {
        addInitialFilterUnit();
      }
    }
  }

  /**
   * Filter Unit - represents a single LDAP filter expression like (cn=john).
   */
  private class FilterUnit extends HorizontalLayout {
    private ComboBox<String> attributeCombo;
    private ComboBox<FilterOperator> operatorCombo;
    private TextField valueField;
    private Button removeButton;

    public FilterUnit() {
      initializeUnitComponents();
      setupUnitLayout();
    }

    private void initializeUnitComponents() {
      // Attribute selector
      attributeCombo = new ComboBox<>("Attribute");
      attributeCombo.setItems(Arrays.asList(COMMON_ATTRIBUTES));
      attributeCombo.setAllowCustomValue(true);
      attributeCombo.setWidth("150px");
      attributeCombo.addValueChangeListener(e -> updateGeneratedFilter());
      attributeCombo.addCustomValueSetListener(e -> {
        attributeCombo.setValue(e.getDetail());
        updateGeneratedFilter();
      });

      // Operator selector
      operatorCombo = new ComboBox<>("Operator");
      operatorCombo.setItems(FilterOperator.values());
      operatorCombo.setValue(FilterOperator.EQUALS);
      operatorCombo.setWidth("150px");
      operatorCombo.addValueChangeListener(e -> {
        updateValueFieldVisibility();
        updateGeneratedFilter();
      });

      // Value field
      valueField = new TextField("Value");
      valueField.setPlaceholder("Enter value");
      valueField.addValueChangeListener(e -> updateGeneratedFilter());

      // Remove button
      removeButton = new Button(new Icon(VaadinIcon.TRASH));
      removeButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
      removeButton.addClickListener(e -> {
        // Find parent filter group and remove this unit
        FilterGroup parentGroup = findParentFilterGroup();
        if (parentGroup != null) {
          parentGroup.removeFilterUnit(this);
        }
      });

      // Force proper styling with inline styles
      removeButton.getStyle().set("background", "var(--lumo-error-color)");
      removeButton.getStyle().set("border", "2px solid var(--lumo-error-color)");
      removeButton.getStyle().set("color", "var(--lumo-error-contrast-color)");
      removeButton.getStyle().set("border-radius", "var(--lumo-border-radius-s)");
    }

    private void setupUnitLayout() {
      setDefaultVerticalComponentAlignment(Alignment.END);
      setSpacing(true);
      setWidthFull();
      addClassName("filter-unit");
      getStyle().set("padding", "8px")
          .set("border", "1px solid #ddd")
          .set("border-radius", "4px")
          .set("background", "white");

      add(attributeCombo, operatorCombo, valueField, removeButton);
      setFlexGrow(0, attributeCombo, operatorCombo, removeButton);
      setFlexGrow(1, valueField);
    }

    private void updateValueFieldVisibility() {
      FilterOperator operator = operatorCombo.getValue();
      boolean needsValue = operator != FilterOperator.EXISTS
          && operator != FilterOperator.NOT_EXISTS;
      valueField.setVisible(needsValue);
    }

    private FilterGroup findParentFilterGroup() {
      return filterGroups.stream()
          .filter(group -> group.filterUnits.contains(this))
          .findFirst()
          .orElse(null);
    }

    public String buildFilterExpression() {
      String attribute = attributeCombo.getValue();
      FilterOperator operator = operatorCombo.getValue();
      String value = valueField.getValue();

      if (attribute == null || attribute.trim().isEmpty()) {
        return "";
      }

      switch (operator) {
        case EQUALS:
          return value != null && !value.trim().isEmpty() 
              ? "(" + attribute + "=" + escapeFilterValue(value) + ")" : "";
        case NOT_EQUALS:
          return value != null && !value.trim().isEmpty() 
              ? "(!(" + attribute + "=" + escapeFilterValue(value) + "))" : "";
        case GREATER_EQUAL:
          return value != null && !value.trim().isEmpty() 
              ? "(" + attribute + ">=" + escapeFilterValue(value) + ")" : "";
        case LESS_EQUAL:
          return value != null && !value.trim().isEmpty() 
              ? "(" + attribute + "<=" + escapeFilterValue(value) + ")" : "";
        case STARTS_WITH:
          return value != null && !value.trim().isEmpty() 
              ? "(" + attribute + "=" + escapeFilterValue(value) + "*)" : "";
        case ENDS_WITH:
          return value != null && !value.trim().isEmpty() 
              ? "(" + attribute + "=*" + escapeFilterValue(value) + ")" : "";
        case CONTAINS:
          return value != null && !value.trim().isEmpty() 
              ? "(" + attribute + "=*" + escapeFilterValue(value) + "*)" : "";
        case EXISTS:
          return "(" + attribute + "=*)";
        case NOT_EXISTS:
          return "(!(" + attribute + "=*))";
        default:
          return "";
      }
    }

    /**
     * Populate this filter unit from a filter string.
     */
    public void populateFromFilter(String filter) {
      String trimmed = filter.trim();
      if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
        trimmed = trimmed.substring(1, trimmed.length() - 1);
      }

      // Handle NOT operator wrapping
      boolean isNegated = false;
      if (trimmed.startsWith("!")) {
        isNegated = true;
        trimmed = trimmed.substring(1);
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
          trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
      }

      // Parse the filter expression
      int equalPos = trimmed.indexOf('=');
      if (equalPos > 0) {
        String attr = trimmed.substring(0, equalPos).trim();
        String val = trimmed.substring(equalPos + 1).trim();

        attributeCombo.setValue(attr);

        // Determine operator based on value pattern
        if (val.equals("*")) {
          operatorCombo.setValue(isNegated ? FilterOperator.NOT_EXISTS : FilterOperator.EXISTS);
          valueField.setValue("");
        } else if (val.startsWith("*") && val.endsWith("*") && val.length() > 2) {
          operatorCombo.setValue(FilterOperator.CONTAINS);
          valueField.setValue(val.substring(1, val.length() - 1));
        } else if (val.startsWith("*")) {
          operatorCombo.setValue(FilterOperator.ENDS_WITH);
          valueField.setValue(val.substring(1));
        } else if (val.endsWith("*")) {
          operatorCombo.setValue(FilterOperator.STARTS_WITH);
          valueField.setValue(val.substring(0, val.length() - 1));
        } else {
          operatorCombo.setValue(isNegated ? FilterOperator.NOT_EQUALS : FilterOperator.EQUALS);
          valueField.setValue(val);
        }

        updateValueFieldVisibility();
      } else if (trimmed.contains(">=")) {
        int pos = trimmed.indexOf(">=");
        attributeCombo.setValue(trimmed.substring(0, pos).trim());
        operatorCombo.setValue(FilterOperator.GREATER_EQUAL);
        valueField.setValue(trimmed.substring(pos + 2).trim());
        updateValueFieldVisibility();
      } else if (trimmed.contains("<=")) {
        int pos = trimmed.indexOf("<=");
        attributeCombo.setValue(trimmed.substring(0, pos).trim());
        operatorCombo.setValue(FilterOperator.LESS_EQUAL);
        valueField.setValue(trimmed.substring(pos + 2).trim());
        updateValueFieldVisibility();
      }
    }
  }
}
