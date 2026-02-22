package com.ldapbrowser.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.ldapbrowser.model.SearchFilter.Operator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SearchFilter}.
 * Covers toLdapFilter() output for every operator, composite
 * filter groups (AND/OR/NOT), edge cases, and the Operator enum.
 */
class SearchFilterTest {

  // ---------------------------------------------------------------
  // Simple filter — one operator per test
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Simple filters (leaf nodes)")
  class SimpleFilters {

    @Test
    @DisplayName("EQUAL produces (attr=value)")
    void equalFilter() {
      SearchFilter f = new SearchFilter(Operator.EQUAL, "cn", "admin");
      assertThat(f.toLdapFilter()).isEqualTo("(cn=admin)");
    }

    @Test
    @DisplayName("APPROX produces (attr~=value)")
    void approxFilter() {
      SearchFilter f = new SearchFilter(Operator.APPROX, "sn", "Smith");
      assertThat(f.toLdapFilter()).isEqualTo("(sn~=Smith)");
    }

    @Test
    @DisplayName("GREATER_OR_EQUAL produces (attr>=value)")
    void greaterOrEqualFilter() {
      SearchFilter f =
          new SearchFilter(Operator.GREATER_OR_EQUAL, "uidNumber", "1000");
      assertThat(f.toLdapFilter()).isEqualTo("(uidNumber>=1000)");
    }

    @Test
    @DisplayName("LESS_OR_EQUAL produces (attr<=value)")
    void lessOrEqualFilter() {
      SearchFilter f =
          new SearchFilter(Operator.LESS_OR_EQUAL, "uidNumber", "5000");
      assertThat(f.toLdapFilter()).isEqualTo("(uidNumber<=5000)");
    }

    @Test
    @DisplayName("PRESENT produces (attr=*)")
    void presentFilter() {
      SearchFilter f = new SearchFilter(Operator.PRESENT, "mail", null);
      assertThat(f.toLdapFilter()).isEqualTo("(mail=*)");
    }

    @Test
    @DisplayName("SUBSTRING produces (attr=*value*)")
    void substringFilter() {
      SearchFilter f =
          new SearchFilter(Operator.SUBSTRING, "cn", "admin");
      assertThat(f.toLdapFilter()).isEqualTo("(cn=*admin*)");
    }
  }

  // ---------------------------------------------------------------
  // Composite filters — AND, OR, NOT
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Composite filters (AND / OR / NOT)")
  class CompositeFilters {

    @Test
    @DisplayName("AND with two children")
    void andFilter() {
      SearchFilter and = new SearchFilter(Operator.AND);
      and.addChild(new SearchFilter(Operator.EQUAL, "cn", "admin"));
      and.addChild(
          new SearchFilter(Operator.EQUAL, "objectClass", "person"));

      assertThat(and.toLdapFilter())
          .isEqualTo("(&(cn=admin)(objectClass=person))");
    }

    @Test
    @DisplayName("OR with two children")
    void orFilter() {
      SearchFilter or = new SearchFilter(Operator.OR);
      or.addChild(new SearchFilter(Operator.EQUAL, "cn", "admin"));
      or.addChild(new SearchFilter(Operator.EQUAL, "cn", "root"));

      assertThat(or.toLdapFilter())
          .isEqualTo("(|(cn=admin)(cn=root))");
    }

    @Test
    @DisplayName("NOT with single child")
    void notFilter() {
      SearchFilter not = new SearchFilter(Operator.NOT);
      not.addChild(
          new SearchFilter(Operator.EQUAL, "objectClass", "computer"));

      assertThat(not.toLdapFilter())
          .isEqualTo("(!(objectClass=computer))");
    }

    @Test
    @DisplayName("AND with three children")
    void andThreeChildren() {
      SearchFilter and = new SearchFilter(Operator.AND);
      and.addChild(new SearchFilter(Operator.EQUAL, "cn", "admin"));
      and.addChild(new SearchFilter(Operator.PRESENT, "mail", null));
      and.addChild(
          new SearchFilter(Operator.GREATER_OR_EQUAL, "uidNumber", "100"));

      assertThat(and.toLdapFilter())
          .isEqualTo("(&(cn=admin)(mail=*)(uidNumber>=100))");
    }

    @Test
    @DisplayName("nested AND inside OR")
    void nestedAndInsideOr() {
      SearchFilter inner = new SearchFilter(Operator.AND);
      inner.addChild(new SearchFilter(Operator.EQUAL, "cn", "admin"));
      inner.addChild(new SearchFilter(Operator.PRESENT, "mail", null));

      SearchFilter or = new SearchFilter(Operator.OR);
      or.addChild(inner);
      or.addChild(new SearchFilter(Operator.EQUAL, "cn", "root"));

      assertThat(or.toLdapFilter())
          .isEqualTo("(|(&(cn=admin)(mail=*))(cn=root))");
    }

    @Test
    @DisplayName("NOT wrapping AND")
    void notWrappingAnd() {
      SearchFilter and = new SearchFilter(Operator.AND);
      and.addChild(
          new SearchFilter(Operator.EQUAL, "objectClass", "person"));
      and.addChild(new SearchFilter(Operator.EQUAL, "cn", "test"));

      SearchFilter not = new SearchFilter(Operator.NOT);
      not.addChild(and);

      assertThat(not.toLdapFilter())
          .isEqualTo("(!(&(objectClass=person)(cn=test)))");
    }

    @Test
    @DisplayName("composite with no children falls through to simple path")
    void emptyComposite() {
      // When a composite has no children, children.isEmpty() is true
      // so toLdapFilter() follows the simple-filter branch.
      // attribute and value are null in this case.
      SearchFilter and = new SearchFilter(Operator.AND);
      String result = and.toLdapFilter();
      assertThat(result).isNotNull();
      // Operator AND has symbol "&", simple path: (attr + symbol + value)
      assertThat(result).isEqualTo("(null&null)");
    }
  }

  // ---------------------------------------------------------------
  // Edge cases — special characters, empty/null values
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    @DisplayName("value with spaces")
    void valueWithSpaces() {
      SearchFilter f =
          new SearchFilter(Operator.EQUAL, "cn", "John Doe");
      assertThat(f.toLdapFilter()).isEqualTo("(cn=John Doe)");
    }

    @Test
    @DisplayName("value with parentheses (escaped — injection safe)")
    void valueWithParentheses() {
      SearchFilter f =
          new SearchFilter(Operator.EQUAL, "description", "test(value)");
      // '(' → \28, ')' → \29
      assertThat(f.toLdapFilter())
          .isEqualTo("(description=test\\28value\\29)");
    }

    @Test
    @DisplayName("value with asterisk (escaped — injection safe)")
    void valueWithAsterisk() {
      SearchFilter f =
          new SearchFilter(Operator.EQUAL, "cn", "admin*");
      // '*' → \2a
      assertThat(f.toLdapFilter()).isEqualTo("(cn=admin\\2a)");
    }

    @Test
    @DisplayName("value with backslash (escaped — injection safe)")
    void valueWithBackslash() {
      SearchFilter f =
          new SearchFilter(Operator.EQUAL, "cn", "admin\\test");
      // '\\' → \5c
      assertThat(f.toLdapFilter()).isEqualTo("(cn=admin\\5ctest)");
    }

    @Test
    @DisplayName("empty attribute name")
    void emptyAttribute() {
      SearchFilter f = new SearchFilter(Operator.EQUAL, "", "value");
      assertThat(f.toLdapFilter()).isEqualTo("(=value)");
    }

    @Test
    @DisplayName("empty value")
    void emptyValue() {
      SearchFilter f = new SearchFilter(Operator.EQUAL, "cn", "");
      assertThat(f.toLdapFilter()).isEqualTo("(cn=)");
    }

    @Test
    @DisplayName("null value with non-PRESENT operator")
    void nullValue() {
      SearchFilter f = new SearchFilter(Operator.EQUAL, "cn", null);
      assertThat(f.toLdapFilter()).isEqualTo("(cn=null)");
    }

    @Test
    @DisplayName("unicode characters in value (UTF-8 hex encoded)")
    void unicodeValue() {
      SearchFilter f =
          new SearchFilter(Operator.EQUAL, "cn", "\u00e9l\u00e8ve");
      // Filter.encodeValue() encodes non-ASCII as UTF-8 hex escape sequences
      // é = U+00E9 → UTF-8 0xC3 0xA9 → \c3\a9
      // è = U+00E8 → UTF-8 0xC3 0xA8 → \c3\a8
      assertThat(f.toLdapFilter()).isEqualTo("(cn=\\c3\\a9l\\c3\\a8ve)");
    }

    @Test
    @DisplayName("substring with empty value")
    void substringEmpty() {
      SearchFilter f = new SearchFilter(Operator.SUBSTRING, "cn", "");
      assertThat(f.toLdapFilter()).isEqualTo("(cn=**)");
    }
  }

  // ---------------------------------------------------------------
  // Getters / setters / mutation
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Getters and setters")
  class GettersSetters {

    @Test
    @DisplayName("get/set operator")
    void operatorAccessors() {
      SearchFilter f = new SearchFilter(Operator.EQUAL, "cn", "test");
      assertThat(f.getOperator()).isEqualTo(Operator.EQUAL);
      f.setOperator(Operator.APPROX);
      assertThat(f.getOperator()).isEqualTo(Operator.APPROX);
    }

    @Test
    @DisplayName("get/set attribute")
    void attributeAccessors() {
      SearchFilter f = new SearchFilter(Operator.EQUAL, "cn", "test");
      assertThat(f.getAttribute()).isEqualTo("cn");
      f.setAttribute("sn");
      assertThat(f.getAttribute()).isEqualTo("sn");
    }

    @Test
    @DisplayName("get/set value")
    void valueAccessors() {
      SearchFilter f = new SearchFilter(Operator.EQUAL, "cn", "test");
      assertThat(f.getValue()).isEqualTo("test");
      f.setValue("updated");
      assertThat(f.getValue()).isEqualTo("updated");
    }

    @Test
    @DisplayName("children list starts empty for simple filter")
    void childrenEmptyForSimple() {
      SearchFilter f = new SearchFilter(Operator.EQUAL, "cn", "test");
      assertThat(f.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("addChild appends to children list")
    void addChild() {
      SearchFilter parent = new SearchFilter(Operator.AND);
      SearchFilter child =
          new SearchFilter(Operator.EQUAL, "cn", "test");
      parent.addChild(child);

      assertThat(parent.getChildren()).hasSize(1);
      assertThat(parent.getChildren().get(0)).isSameAs(child);
    }
  }

  // ---------------------------------------------------------------
  // Operator enum
  // ---------------------------------------------------------------

  @Nested
  @DisplayName("Operator enum")
  class OperatorEnum {

    @Test
    @DisplayName("all operators have non-null LDAP symbols")
    void allSymbolsPresent() {
      for (Operator op : Operator.values()) {
        assertThat(op.getLdapSymbol()).isNotNull().isNotEmpty();
      }
    }

    @Test
    @DisplayName("all operators have non-null display names")
    void allDisplayNamesPresent() {
      for (Operator op : Operator.values()) {
        assertThat(op.getDisplayName()).isNotNull().isNotEmpty();
      }
    }

    @Test
    @DisplayName("operator count matches expected")
    void operatorCount() {
      assertThat(Operator.values()).hasSize(9);
    }

    @Test
    @DisplayName("LDAP symbols are correct")
    void ldapSymbols() {
      assertThat(Operator.AND.getLdapSymbol()).isEqualTo("&");
      assertThat(Operator.OR.getLdapSymbol()).isEqualTo("|");
      assertThat(Operator.NOT.getLdapSymbol()).isEqualTo("!");
      assertThat(Operator.EQUAL.getLdapSymbol()).isEqualTo("=");
      assertThat(Operator.APPROX.getLdapSymbol()).isEqualTo("~=");
      assertThat(Operator.GREATER_OR_EQUAL.getLdapSymbol())
          .isEqualTo(">=");
      assertThat(Operator.LESS_OR_EQUAL.getLdapSymbol()).isEqualTo("<=");
      assertThat(Operator.PRESENT.getLdapSymbol()).isEqualTo("=*");
      assertThat(Operator.SUBSTRING.getLdapSymbol()).isEqualTo("=*");
    }
  }
}
