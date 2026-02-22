package com.ldapbrowser.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ldapbrowser.util.OidLookupTable.OidCategory;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link OidLookupTable}. */
@DisplayName("OidLookupTable")
class OidLookupTableTest {

  // -----------------------------------------------------------
  // Well-known OIDs used across tests
  // -----------------------------------------------------------
  /** Simple Paged Results Control (RFC 2696). */
  private static final String PAGED_RESULTS_OID = "1.2.840.113556.1.4.319";

  /** StartTLS extended operation (RFC 4511). */
  private static final String STARTTLS_OID = "1.3.6.1.4.1.1466.20037";

  /** cn (Common Name) attribute type OID. */
  private static final String CN_OID = "2.5.4.3";

  /** top object class OID. */
  private static final String TOP_OID = "2.5.6.0";

  /** Completely unknown OID — not in any table. */
  private static final String UNKNOWN_OID = "9.9.9.9.9.9999";

  @Nested
  @DisplayName("utility class contract")
  class UtilityClass {

    @Test
    @DisplayName("cannot be instantiated — constructor throws UnsupportedOperationException")
    void constructorThrows() throws Exception {
      var ctor = OidLookupTable.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      assertThatThrownBy(ctor::newInstance)
          .cause()
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("control OID lookups")
  class ControlLookups {

    @Test
    @DisplayName("known control OID returns non-null description")
    void knownControlOid() {
      assertThat(OidLookupTable.getControlDescription(PAGED_RESULTS_OID))
          .isNotNull()
          .isNotEmpty();
    }

    @Test
    @DisplayName("known control OID description contains expected text")
    void knownControlOidContent() {
      assertThat(OidLookupTable.getControlDescription(PAGED_RESULTS_OID))
          .containsIgnoringCase("Paged");
    }

    @Test
    @DisplayName("unknown control OID returns null")
    void unknownControlOid() {
      assertThat(OidLookupTable.getControlDescription(UNKNOWN_OID)).isNull();
    }

    @Test
    @DisplayName("getAllControls returns non-empty defensive copy")
    void getAllControlsNonEmpty() {
      Map<String, String> controls = OidLookupTable.getAllControls();
      assertThat(controls).isNotEmpty().containsKey(PAGED_RESULTS_OID);
    }

    @Test
    @DisplayName("getAllControls returns a copy — mutations do not affect the table")
    void getAllControlsDefensiveCopy() {
      OidLookupTable.getAllControls().clear();
      assertThat(OidLookupTable.getControlDescription(PAGED_RESULTS_OID)).isNotNull();
    }
  }

  @Nested
  @DisplayName("extended operation OID lookups")
  class ExtendedOperationLookups {

    @Test
    @DisplayName("known extended operation OID returns non-null description")
    void knownExtOpOid() {
      assertThat(OidLookupTable.getExtendedOperationDescription(STARTTLS_OID))
          .isNotNull()
          .isNotEmpty();
    }

    @Test
    @DisplayName("known extended operation description contains expected text")
    void knownExtOpOidContent() {
      assertThat(OidLookupTable.getExtendedOperationDescription(STARTTLS_OID))
          .containsIgnoringCase("TLS");
    }

    @Test
    @DisplayName("unknown extended operation OID returns null")
    void unknownExtOpOid() {
      assertThat(OidLookupTable.getExtendedOperationDescription(UNKNOWN_OID)).isNull();
    }

    @Test
    @DisplayName("getAllExtendedOperations returns non-empty defensive copy")
    void getAllExtendedOperationsNonEmpty() {
      Map<String, String> ops = OidLookupTable.getAllExtendedOperations();
      assertThat(ops).isNotEmpty().containsKey(STARTTLS_OID);
    }
  }

  @Nested
  @DisplayName("attribute type OID lookups")
  class AttributeTypeLookups {

    @Test
    @DisplayName("cn attribute type OID returns non-null description")
    void cnAttributeOid() {
      assertThat(OidLookupTable.getAttributeTypeDescription(CN_OID))
          .isNotNull()
          .containsIgnoringCase("cn");
    }

    @Test
    @DisplayName("unknown attribute type OID returns null")
    void unknownAttributeOid() {
      assertThat(OidLookupTable.getAttributeTypeDescription(UNKNOWN_OID)).isNull();
    }
  }

  @Nested
  @DisplayName("object class OID lookups")
  class ObjectClassLookups {

    @Test
    @DisplayName("top object class OID returns non-null description")
    void topObjectClassOid() {
      assertThat(OidLookupTable.getObjectClassDescription(TOP_OID))
          .isNotNull()
          .isNotEmpty();
    }

    @Test
    @DisplayName("unknown object class OID returns null")
    void unknownObjectClassOid() {
      assertThat(OidLookupTable.getObjectClassDescription(UNKNOWN_OID)).isNull();
    }
  }

  @Nested
  @DisplayName("getAnyDescription cross-category lookup")
  class AnyDescriptionLookups {

    @Test
    @DisplayName("known control OID via getAnyDescription has [Control] prefix")
    void knownControlViaAny() {
      String result = OidLookupTable.getAnyDescription(PAGED_RESULTS_OID);
      assertThat(result).startsWith("[Control]");
    }

    @Test
    @DisplayName("known extended op OID via getAnyDescription has [Extended Op] prefix")
    void knownExtOpViaAny() {
      String result = OidLookupTable.getAnyDescription(STARTTLS_OID);
      assertThat(result).startsWith("[Extended Op]");
    }

    @Test
    @DisplayName("known attribute OID via getAnyDescription has [Attribute] prefix")
    void knownAttributeViaAny() {
      String result = OidLookupTable.getAnyDescription(CN_OID);
      assertThat(result).startsWith("[Attribute]");
    }

    @Test
    @DisplayName("known object class OID via getAnyDescription has [Object Class] prefix")
    void knownObjectClassViaAny() {
      String result = OidLookupTable.getAnyDescription(TOP_OID);
      assertThat(result).startsWith("[Object Class]");
    }

    @Test
    @DisplayName("unknown OID via getAnyDescription returns the OID itself")
    void unknownViaAny() {
      assertThat(OidLookupTable.getAnyDescription(UNKNOWN_OID)).isEqualTo(UNKNOWN_OID);
    }
  }

  @Nested
  @DisplayName("formatOidWithDescription")
  class FormatOidWithDescription {

    @Test
    @DisplayName("known control OID is formatted as 'OID - description'")
    void knownControlFormatted() {
      String result = OidLookupTable.formatOidWithDescription(PAGED_RESULTS_OID, OidCategory.CONTROL);
      assertThat(result)
          .startsWith(PAGED_RESULTS_OID)
          .contains(" - ");
    }

    @Test
    @DisplayName("unknown OID with specific category returns just the OID")
    void unknownOidWithCategoryReturnsOid() {
      String result = OidLookupTable.formatOidWithDescription(UNKNOWN_OID, OidCategory.CONTROL);
      assertThat(result).isEqualTo(UNKNOWN_OID);
    }

    @Test
    @DisplayName("OidCategory.ANY delegates to formatOidWithAnyDescription")
    void anyCategoryDelegates() {
      String viaAny = OidLookupTable.formatOidWithDescription(PAGED_RESULTS_OID, OidCategory.ANY);
      String direct = OidLookupTable.formatOidWithAnyDescription(PAGED_RESULTS_OID);
      assertThat(viaAny).isEqualTo(direct);
    }
  }

  @Nested
  @DisplayName("formatOidWithAnyDescription")
  class FormatOidWithAnyDescription {

    @Test
    @DisplayName("known OID produces 'OID - [Category] description' string")
    void knownOidFullFormat() {
      String result = OidLookupTable.formatOidWithAnyDescription(PAGED_RESULTS_OID);
      assertThat(result)
          .startsWith(PAGED_RESULTS_OID + " - [Control]");
    }

    @Test
    @DisplayName("unknown OID returns just the OID unchanged")
    void unknownOidUnchanged() {
      assertThat(OidLookupTable.formatOidWithAnyDescription(UNKNOWN_OID)).isEqualTo(UNKNOWN_OID);
    }
  }
}
