package com.ldapbrowser.ui.dialogs;

import com.ldapbrowser.model.LdapEntry;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.components.LdapTreeBrowser;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Reusable dialog for browsing and selecting LDAP DNs.
 * Standardizes the DN selection experience across the application.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * new DnBrowserDialog(ldapService)
 *     .withServerConfigs(serverConfigs)
 *     .onDnSelected(dn -> targetField.setValue(dn))
 *     .open();
 * }</pre>
 */
public class DnBrowserDialog extends Dialog {

  private final LdapTreeBrowser treeBrowser;
  private Consumer<String> onDnSelected;
  private Predicate<LdapEntry> validationPredicate;
  private String validationErrorMessage = "Please select a valid entry";

  /**
   * Creates a DN browser dialog with default title.
   *
   * @param ldapService the LDAP service
   */
  public DnBrowserDialog(LdapService ldapService) {
    this(ldapService, "Select DN from Directory");
  }

  /**
   * Creates a DN browser dialog with custom title.
   *
   * @param ldapService the LDAP service
   * @param title the dialog title
   */
  public DnBrowserDialog(LdapService ldapService, String title) {
    setHeaderTitle(title);
    setWidth("800px");
    setHeight("600px");
    setModal(true);
    setCloseOnOutsideClick(false);
    setDraggable(true);
    setResizable(true);

    // Create tree browser
    treeBrowser = new LdapTreeBrowser(ldapService);
    treeBrowser.setSizeFull();

    // Setup layout
    VerticalLayout content = new VerticalLayout(treeBrowser);
    content.setSizeFull();
    content.setPadding(false);
    content.setSpacing(false);

    add(content);

    // Setup footer buttons
    setupFooter();

    // Add selection listener for double-click behavior
    treeBrowser.addSelectionListener(event -> {
      // Allow double-click to select and close
      if (event.isFromClient()) {
        handleSelection();
      }
    });
  }

  /**
   * Sets the server configurations and loads the tree.
   * Automatically refreshes the tree when the dialog opens.
   *
   * @param serverConfigs the list of server configurations
   * @return this dialog for method chaining
   */
  public DnBrowserDialog withServerConfigs(List<LdapServerConfig> serverConfigs) {
    if (serverConfigs == null || serverConfigs.isEmpty()) {
      NotificationHelper.showError("No server configurations provided");
      return this;
    }

    try {
      treeBrowser.setServerConfigs(serverConfigs);
      treeBrowser.loadServers(); // Auto-refresh on open
    } catch (Exception ex) {
      NotificationHelper.showError("Failed to load LDAP tree: " + ex.getMessage());
    }

    return this;
  }

  /**
   * Sets a single server configuration and loads the tree.
   * Automatically refreshes the tree when the dialog opens.
   *
   * @param serverConfig the server configuration
   * @return this dialog for method chaining
   */
  public DnBrowserDialog withServerConfig(LdapServerConfig serverConfig) {
    if (serverConfig == null) {
      NotificationHelper.showError("No server configuration provided");
      return this;
    }

    try {
      treeBrowser.setServerConfig(serverConfig);
      treeBrowser.loadServers(); // Auto-refresh on open
    } catch (Exception ex) {
      NotificationHelper.showError("Failed to load LDAP tree: " + ex.getMessage());
    }

    return this;
  }

  /**
   * Sets the callback to invoke when a DN is selected.
   *
   * @param callback the callback accepting the selected DN
   * @return this dialog for method chaining
   */
  public DnBrowserDialog onDnSelected(Consumer<String> callback) {
    this.onDnSelected = callback;
    return this;
  }

  /**
   * Sets a validation predicate for selected entries.
   *
   * @param predicate the validation predicate
   * @param errorMessage the error message to show on validation failure
   * @return this dialog for method chaining
   */
  public DnBrowserDialog withValidation(Predicate<LdapEntry> predicate, String errorMessage) {
    this.validationPredicate = predicate;
    this.validationErrorMessage = errorMessage;
    return this;
  }

  private void setupFooter() {
    Button selectButton = new Button("Select", e -> handleSelectClick());
    selectButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    Button cancelButton = new Button("Cancel", e -> close());

    getFooter().add(cancelButton, selectButton);
  }

  private void handleSelection() {
    // Auto-close on valid selection via tree interaction
    LdapEntry selectedEntry = treeBrowser.getSelectedEntry();
    if (selectedEntry != null && isValidEntry(selectedEntry)) {
      String dn = selectedEntry.getDn();
      if (isValidDn(dn)) {
        if (onDnSelected != null) {
          onDnSelected.accept(dn);
        }
        close();
      }
    }
  }

  private void handleSelectClick() {
    LdapEntry selectedEntry = treeBrowser.getSelectedEntry();

    if (selectedEntry == null) {
      NotificationHelper.showError("Please select an entry from the tree");
      return;
    }

    if (!isValidEntry(selectedEntry)) {
      NotificationHelper.showError(validationErrorMessage);
      return;
    }

    String dn = selectedEntry.getDn();
    if (isValidDn(dn)) {
      if (onDnSelected != null) {
        onDnSelected.accept(dn);
      }
      close();
    } else {
      NotificationHelper.showError("Invalid DN selected");
    }
  }

  private boolean isValidEntry(LdapEntry entry) {
    if (validationPredicate == null) {
      return true;
    }
    return validationPredicate.test(entry);
  }

  private boolean isValidDn(String dn) {
    return dn != null
        && !dn.isEmpty()
        && !dn.startsWith("_placeholder_")
        && !dn.startsWith("_pagination_")
        && !dn.startsWith("SERVER:");
  }
}
