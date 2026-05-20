package com.ldapbrowser.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.RootDSE;
import com.unboundid.ldap.sdk.SearchResultEntry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LdapRootDseHelper")
class LdapRootDseHelperTest {

  private final LdapRootDseHelper helper = new LdapRootDseHelper();

  @Test
  @DisplayName("extracts naming contexts and private naming contexts")
  void extractsContextLists() {
    RootDSE rootDse = rootDse(
        new Attribute("namingContexts", "dc=example,dc=com", "o=internal"),
        new Attribute("ds-private-naming-contexts", "o=private")
    );

    assertThat(helper.getNamingContexts(rootDse)).containsExactly("dc=example,dc=com", "o=internal");
    assertThat(helper.getPrivateNamingContexts(rootDse)).containsExactly("o=private");
  }

  @Test
  @DisplayName("returns empty lists when root DSE is null")
  void nullRootDseReturnsEmptyLists() {
    assertThat(helper.getNamingContexts(null)).isEqualTo(List.of());
    assertThat(helper.getPrivateNamingContexts(null)).isEqualTo(List.of());
  }

  @Test
  @DisplayName("schema subentry DN uses preferred attribute order")
  void schemaDnOrder() {
    RootDSE preferred = rootDse(
        new Attribute("subschemaSubentry", "cn=subschema"),
        new Attribute("schemaNamingContext", "cn=naming"),
        new Attribute("schemaSubentry", "cn=schemaSubentry")
    );
    RootDSE fallback = rootDse(new Attribute("schemaNamingContext", "cn=naming"));
    RootDSE defaultRoot = rootDse();

    assertThat(helper.getSchemaSubentryDn(preferred)).isEqualTo("cn=subschema");
    assertThat(helper.getSchemaSubentryDn(fallback)).isEqualTo("cn=naming");
    assertThat(helper.getSchemaSubentryDn(defaultRoot)).isEqualTo("cn=schema");
    assertThat(helper.getSchemaSubentryDn(null)).isEqualTo("cn=schema");
  }

  @Test
  @DisplayName("detects control support by OID")
  void detectsControlSupport() {
    RootDSE rootDse = rootDse(
        new Attribute("supportedControl", "1.2.3.4", "2.16.840.1")
    );

    assertThat(helper.isControlSupported(rootDse, "1.2.3.4")).isTrue();
    assertThat(helper.isControlSupported(rootDse, "9.9.9")).isFalse();
    assertThat(helper.isControlSupported(null, "1.2.3.4")).isFalse();
  }

  @Test
  @DisplayName("detects schema modification capability by supported feature OID")
  void detectsSchemaModificationSupport() {
    RootDSE supported = rootDse(
        new Attribute("supportedFeatures", "1.3.6.1.4.1.4203.1.5.1")
    );
    RootDSE unsupported = rootDse(new Attribute("supportedFeatures", "1.2.3.4"));

    assertThat(helper.supportsSchemaModification(supported)).isTrue();
    assertThat(helper.supportsSchemaModification(unsupported)).isFalse();
    assertThat(helper.supportsSchemaModification(null)).isFalse();
  }

  private static RootDSE rootDse(Attribute... attributes) {
    SearchResultEntry entry = new SearchResultEntry("", attributes);
    return new RootDSE(entry);
  }
}
