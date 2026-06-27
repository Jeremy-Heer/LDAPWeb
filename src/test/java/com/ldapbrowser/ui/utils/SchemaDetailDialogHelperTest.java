package com.ldapbrowser.ui.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SchemaDetailDialogHelper}. */
@DisplayName("SchemaDetailDialogHelper")
class SchemaDetailDialogHelperTest {

  @Test
  @DisplayName("extracts X-SCHEMA-FILE from extension map")
  void extractsSchemaFileFromExtensions() {
    Map<String, String[]> extensions = new HashMap<>();
    extensions.put("X-SCHEMA-FILE", new String[] {"core.ldif"});

    assertThat(SchemaDetailDialogHelper.getSchemaFileFromExtensions(extensions))
        .isEqualTo("core.ldif");
  }

  @Test
  @DisplayName("returns raw definition via toString fallback")
  void returnsRawDefinitionViaToStringFallback() {
    Object schemaElement = new Object() {
      @Override
      public String toString() {
        return "( 1.2.3 NAME 'sample' )";
      }
    };

    assertThat(SchemaDetailDialogHelper.getRawDefinitionString(schemaElement))
        .isEqualTo("( 1.2.3 NAME 'sample' )");
  }
}