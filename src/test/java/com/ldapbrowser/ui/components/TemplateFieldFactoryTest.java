package com.ldapbrowser.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import com.ldapbrowser.model.EntryTemplate.FieldType;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TemplateFieldFactory}.
 */
@DisplayName("TemplateFieldFactory")
class TemplateFieldFactoryTest {

  @Nested
  @DisplayName("createField")
  class CreateField {

    @Test
    @DisplayName("TEXT produces TextField")
    void textField() {
      Component c = TemplateFieldFactory.createField(
          FieldType.TEXT, "hello", null);
      assertThat(c).isInstanceOf(TextField.class);
      assertThat(((TextField) c).getValue()).isEqualTo("hello");
    }

    @Test
    @DisplayName("null type produces TextField")
    void nullType() {
      Component c = TemplateFieldFactory.createField(
          null, "val", null);
      assertThat(c).isInstanceOf(TextField.class);
    }

    @Test
    @DisplayName("PASSWORD produces PasswordField")
    void passwordField() {
      Component c = TemplateFieldFactory.createField(
          FieldType.PASSWORD, "secret", null);
      assertThat(c).isInstanceOf(PasswordField.class);
      assertThat(((PasswordField) c).getValue()).isEqualTo("secret");
    }

    @Test
    @DisplayName("MULTI_VALUED_TEXT produces TextArea")
    void multiValuedText() {
      Component c = TemplateFieldFactory.createField(
          FieldType.MULTI_VALUED_TEXT, "line", null);
      assertThat(c).isInstanceOf(TextArea.class);
      assertThat(((TextArea) c).getValue()).isEqualTo("line");
    }

    @Test
    @DisplayName("BOOLEAN produces ComboBox with TRUE/FALSE")
    @SuppressWarnings("unchecked")
    void booleanField() {
      Component c = TemplateFieldFactory.createField(
          FieldType.BOOLEAN, "TRUE", null);
      assertThat(c).isInstanceOf(ComboBox.class);
      ComboBox<String> combo = (ComboBox<String>) c;
      assertThat(combo.getValue()).isEqualTo("TRUE");
    }

    @Test
    @DisplayName("SELECT_LIST produces ComboBox with values")
    @SuppressWarnings("unchecked")
    void selectList() {
      List<String> opts = List.of("a", "b", "c");
      Component c = TemplateFieldFactory.createField(
          FieldType.SELECT_LIST, "b", opts);
      assertThat(c).isInstanceOf(ComboBox.class);
      ComboBox<String> combo = (ComboBox<String>) c;
      assertThat(combo.getValue()).isEqualTo("b");
    }
  }

  @Nested
  @DisplayName("getValue")
  class GetValue {

    @Test
    @DisplayName("extracts TextField value")
    void textFieldValue() {
      TextField tf = new TextField();
      tf.setValue("hello");
      assertThat(TemplateFieldFactory.getValue(tf)).isEqualTo("hello");
    }

    @Test
    @DisplayName("extracts PasswordField value")
    void passwordFieldValue() {
      PasswordField pf = new PasswordField();
      pf.setValue("secret");
      assertThat(TemplateFieldFactory.getValue(pf)).isEqualTo("secret");
    }

    @Test
    @DisplayName("extracts TextArea value")
    void textAreaValue() {
      TextArea ta = new TextArea();
      ta.setValue("multi");
      assertThat(TemplateFieldFactory.getValue(ta)).isEqualTo("multi");
    }

    @Test
    @DisplayName("returns empty for unknown component")
    void unknownComponent() {
      Component c = new com.vaadin.flow.component.html.Span("x");
      assertThat(TemplateFieldFactory.getValue(c)).isEmpty();
    }
  }

  @Nested
  @DisplayName("maskIfPassword")
  class MaskIfPassword {

    @Test
    @DisplayName("masks PASSWORD values")
    void masksPassword() {
      String result = TemplateFieldFactory.maskIfPassword(
          "secret", FieldType.PASSWORD);
      assertThat(result).isEqualTo("••••••••");
    }

    @Test
    @DisplayName("does not mask TEXT values")
    void noMaskText() {
      String result = TemplateFieldFactory.maskIfPassword(
          "hello", FieldType.TEXT);
      assertThat(result).isEqualTo("hello");
    }

    @Test
    @DisplayName("does not mask when type is null")
    void noMaskNull() {
      String result = TemplateFieldFactory.maskIfPassword(
          "hello", null);
      assertThat(result).isEqualTo("hello");
    }
  }
}
