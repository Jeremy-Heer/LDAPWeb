package com.ldapbrowser.ui.components;

import com.ldapbrowser.model.EntryTemplate.FieldType;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating Vaadin input components based on template
 * field types. Centralizes the mapping from {@link FieldType} to
 * appropriate UI controls.
 */
public final class TemplateFieldFactory {

  private TemplateFieldFactory() {
    // utility class
  }

  /**
   * Creates an input component for the given field type.
   *
   * @param fieldType the template field type
   * @param currentValue the current value to display
   * @param selectValues values for SELECT_LIST fields
   * @return a Vaadin component that implements HasValue
   */
  public static Component createField(FieldType fieldType,
      String currentValue,
      List<String> selectValues) {
    if (fieldType == null) {
      fieldType = FieldType.TEXT;
    }
    return switch (fieldType) {
      case PASSWORD -> createPasswordField(currentValue);
      case BOOLEAN -> createBooleanField(currentValue);
      case SELECT_LIST -> createSelectField(currentValue, selectValues);
      case MULTI_VALUED_TEXT -> createMultiValuedField(currentValue);
      case SEARCH -> createSearchField(currentValue, selectValues);
      default -> createTextField(currentValue);
    };
  }

  /**
   * Splits a multi-valued text value into individual lines,
   * trimming whitespace and discarding blank entries.
   *
   * @param value the raw multi-line text
   * @return list of non-blank line values
   */
  public static List<String> getMultiValues(String value) {
    List<String> result = new ArrayList<>();
    if (value == null || value.isEmpty()) {
      return result;
    }
    for (String line : value.split("\n")) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }

  /**
   * Extracts the string value from a component created by this
   * factory.
   *
   * @param component component created by {@link #createField}
   * @return current string value
   */
  @SuppressWarnings("unchecked")
  public static String getValue(Component component) {
    if (component instanceof HasValue<?, ?> hv) {
      Object val = hv.getValue();
      return val != null ? val.toString() : "";
    }
    return "";
  }

  /**
   * Masks a value for display when the field type is PASSWORD.
   *
   * @param value the raw value
   * @param fieldType the template field type
   * @return masked string if PASSWORD, original value otherwise
   */
  public static String maskIfPassword(String value,
      FieldType fieldType) {
    if (fieldType == FieldType.PASSWORD
        && value != null && !value.isEmpty()) {
      return "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";
    }
    return value;
  }

  private static Component createTextField(String currentValue) {
    TextField field = new TextField();
    field.setWidthFull();
    field.setPlaceholder("Enter attribute value");
    field.setTooltipText("Enter a single value");
    field.setValue(currentValue != null ? currentValue : "");
    return field;
  }

  private static Component createPasswordField(String currentValue) {
    PasswordField field = new PasswordField();
    field.setWidthFull();
    field.setPlaceholder("Enter password");
    field.setValue(currentValue != null ? currentValue : "");
    return field;
  }

  private static Component createBooleanField(String currentValue) {
    ComboBox<String> combo = new ComboBox<>();
    combo.setItems("TRUE", "FALSE");
    combo.setWidthFull();
    combo.setPlaceholder("Select true or false");
    if (currentValue != null && !currentValue.isEmpty()) {
      combo.setValue(currentValue.toUpperCase());
    }
    return combo;
  }

  private static Component createSelectField(String currentValue,
      List<String> selectValues) {
    ComboBox<String> combo = new ComboBox<>();
    if (selectValues != null && !selectValues.isEmpty()) {
      combo.setItems(selectValues);
    }
    combo.setWidthFull();
    combo.setPlaceholder("Select a value");
    if (currentValue != null && !currentValue.isEmpty()) {
      combo.setValue(currentValue);
    }
    return combo;
  }

  private static Component createMultiValuedField(
      String currentValue) {
    TextArea area = new TextArea();
    area.setWidthFull();
    area.setPlaceholder("Enter value");
    area.setTooltipText("Enter multiples one per line");
    area.setValue(currentValue != null ? currentValue : "");
    area.setMaxHeight("100px");
    return area;
  }

  /**
   * Creates a ComboBox for SEARCH type fields. The selectable
   * items are DNs returned by an LDAP search.
   *
   * @param currentValue currently selected value
   * @param searchResults list of DN strings from the LDAP search
   * @return ComboBox component
   */
  private static Component createSearchField(String currentValue,
      List<String> searchResults) {
    ComboBox<String> combo = new ComboBox<>();
    if (searchResults != null && !searchResults.isEmpty()) {
      combo.setItems(searchResults);
    }
    combo.setWidthFull();
    combo.setPlaceholder("Select a DN");
    combo.setTooltipText(
        "Choose a DN from the search results");
    combo.setClearButtonVisible(true);
    if (currentValue != null && !currentValue.isEmpty()) {
      combo.setValue(currentValue);
    }
    return combo;
  }
}
