package com.ldapbrowser.ui.components;

import static org.assertj.core.api.Assertions.assertThat;

import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.schema.Schema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SchemaFilesTab}. */
@DisplayName("SchemaFilesTab")
class SchemaFilesTabTest {

  @Test
  @DisplayName("groups objectClass and attributeType definitions by schema file")
  void groupsDefinitionsBySchemaFile() throws Exception {
    Schema schema = new Schema(schemaEntry());

    Map<String, List<SchemaFilesTab.SchemaFileContent>> files =
        SchemaFilesTab.collectSchemaFiles(schema);

    assertThat(files).containsKeys("core.ldif", "custom.ldif");
    assertThat(files.get("core.ldif"))
        .extracting(SchemaFilesTab.SchemaFileContent::type)
        .containsExactly("attributeType", "objectClass");
    assertThat(files.get("custom.ldif"))
        .extracting(SchemaFilesTab.SchemaFileContent::name)
        .containsExactly("demoGroup");
  }

  @Test
    @DisplayName("builds schema file content as valid schema LDIF")
  void buildsSchemaFileContent() {
    List<SchemaFilesTab.SchemaFileContent> contents = List.of(
        new SchemaFilesTab.SchemaFileContent(
            "attributeType",
            "demoUid",
        "( 1.1.2 NAME 'demoUid' X-SCHEMA-FILE 'core.ldif' X-READ-ONLY 'false' )"),
        new SchemaFilesTab.SchemaFileContent(
            "objectClass",
            "demoPerson",
        "( 1.1.1 NAME 'demoPerson' X-SCHEMA-FILE 'core.ldif' X-READ-ONLY 'true' )"));

    String output = SchemaFilesTab.buildSchemaFileContent("server1", "core.ldif", contents);

        assertThat(output).contains("dn: cn=schema");
        assertThat(output).contains("objectClass: top");
        assertThat(output).contains("objectClass: ldapSubentry");
        assertThat(output).contains("objectClass: subschema");
        assertThat(output).contains("cn: schema");
        assertThat(output).contains("attributeTypes: ( 1.1.2 NAME 'demoUid' )");
        assertThat(output).contains("objectClasses: ( 1.1.1 NAME 'demoPerson' )");
        assertThat(output).doesNotContain("X-SCHEMA-FILE");
        assertThat(output).doesNotContain("X-READ-ONLY");
  }

  private Entry schemaEntry() {
    Entry entry = new Entry("cn=schema");
    entry.addAttribute("attributeTypes",
        "( 1.1.2 NAME 'demoUid' DESC 'Demo attribute' EQUALITY caseIgnoreMatch "
            + "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-SCHEMA-FILE 'core.ldif' )");
    entry.addAttribute("objectClasses",
        "( 1.1.1 NAME 'demoPerson' DESC 'Demo object class' SUP top STRUCTURAL "
            + "MUST ( demoUid ) X-SCHEMA-FILE 'core.ldif' )",
        "( 1.1.3 NAME 'demoGroup' DESC 'Demo group' SUP top STRUCTURAL "
            + "MUST ( cn ) X-SCHEMA-FILE 'custom.ldif' )");
    return entry;
  }
}