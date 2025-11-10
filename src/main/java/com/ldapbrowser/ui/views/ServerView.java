package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.io.IOException;
import java.util.List;

/**
 * Server configuration view.
 * Allows managing LDAP server connection details.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Server | LDAP Browser")
public class ServerView extends VerticalLayout {

  private final ConfigurationService configService;
  private final LdapService ldapService;

  private Grid<LdapServerConfig> serverGrid;
  private Button addServerButton;
  private Button editButton;
  private Button copyButton;
  private Button deleteButton;
  private Button testButton;

  /**
   * Creates the Server view.
   *
   * @param configService configuration service
   * @param ldapService LDAP service
   */
  public ServerView(ConfigurationService configService, LdapService ldapService) {
    this.configService = configService;
    this.ldapService = ldapService;

    setSpacing(true);
    setPadding(true);
    setSizeFull();

    H2 title = new H2("Server Configuration");
    add(title);

    createActionButtons();
    createServerGrid();
    refreshServerGrid();
  }

  /**
   * Creates action buttons.
   */
  private void createActionButtons() {
    addServerButton = new Button("Add Server", event -> openServerDialog(null));
    addServerButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    editButton = new Button("Edit", event -> editSelectedServer());
    editButton.setEnabled(false);

    copyButton = new Button("Copy", event -> copySelectedServer());
    copyButton.setEnabled(false);

    deleteButton = new Button("Delete", event -> deleteSelectedServer());
    deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
    deleteButton.setEnabled(false);

    testButton = new Button("Test", event -> testSelectedServer());
    testButton.setEnabled(false);

    HorizontalLayout buttonLayout = new HorizontalLayout(
        addServerButton, editButton, copyButton, deleteButton, testButton
    );
    buttonLayout.setSpacing(true);
    buttonLayout.setAlignItems(FlexComponent.Alignment.CENTER);

    add(buttonLayout);
  }

  /**
   * Creates the server grid.
   */
  private void createServerGrid() {
    serverGrid = new Grid<>(LdapServerConfig.class, false);
    serverGrid.setHeight("600px");
    serverGrid.setWidthFull();

    serverGrid.addColumn(LdapServerConfig::getName)
        .setHeader("Name")
        .setSortable(true)
        .setResizable(true);

    serverGrid.addColumn(config -> config.getHost() + ":" + config.getPort())
        .setHeader("Host:Port")
        .setSortable(true)
        .setResizable(true);

    serverGrid.addColumn(LdapServerConfig::getBindDn)
        .setHeader("Bind DN")
        .setSortable(true)
        .setResizable(true);

    serverGrid.addColumn(this::getSecurityLabel)
        .setHeader("Security")
        .setSortable(true)
        .setResizable(true);

    serverGrid.asSingleSelect().addValueChangeListener(event -> {
      boolean hasSelection = event.getValue() != null;
      editButton.setEnabled(hasSelection);
      copyButton.setEnabled(hasSelection);
      deleteButton.setEnabled(hasSelection);
      testButton.setEnabled(hasSelection);
    });

    add(serverGrid);
  }

  /**
   * Gets the security label for a server config.
   *
   * @param config server configuration
   * @return security label
   */
  private String getSecurityLabel(LdapServerConfig config) {
    if (config.isUseSsl()) {
      return "SSL/TLS";
    } else if (config.isUseStartTls()) {
      return "StartTLS";
    } else {
      return "None";
    }
  }

  /**
   * Refreshes the server grid with latest data.
   */
  private void refreshServerGrid() {
    List<LdapServerConfig> configs = configService.loadConfigurations();
    serverGrid.setItems(configs);
  }

  /**
   * Opens the server dialog for adding or editing.
   *
   * @param config configuration to edit, or null for new
   */
  private void openServerDialog(LdapServerConfig config) {
    Dialog dialog = new Dialog();
    dialog.setWidth("600px");
    dialog.setHeaderTitle(config == null ? "Add Server" : "Edit Server");

    // Create form
    FormLayout formLayout = new FormLayout();
    formLayout.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1),
        new FormLayout.ResponsiveStep("500px", 2)
    );

    TextField nameField = new TextField("Server Name");
    nameField.setRequired(true);
    nameField.setPlaceholder("e.g., Production LDAP");

    TextField hostField = new TextField("Host");
    hostField.setRequired(true);
    hostField.setPlaceholder("e.g., ldap.example.com");

    IntegerField portField = new IntegerField("Port");
    portField.setMin(1);
    portField.setMax(65535);
    portField.setStepButtonsVisible(true);

    TextField baseDnField = new TextField("Base DN");
    baseDnField.setPlaceholder("e.g., dc=example,dc=com");

    TextField bindDnField = new TextField("Bind DN");
    bindDnField.setPlaceholder("e.g., cn=admin,dc=example,dc=com");

    PasswordField bindPasswordField = new PasswordField("Bind Password");
    bindPasswordField.setPlaceholder("Enter password");

    Checkbox useSslCheckbox = new Checkbox("Use SSL (LDAPS)");
    Checkbox useStartTlsCheckbox = new Checkbox("Use StartTLS");

    useSslCheckbox.addValueChangeListener(event -> {
      if (event.getValue()) {
        useStartTlsCheckbox.setValue(false);
      }
    });

    useStartTlsCheckbox.addValueChangeListener(event -> {
      if (event.getValue()) {
        useSslCheckbox.setValue(false);
      }
    });

    formLayout.add(nameField, hostField);
    formLayout.add(portField, baseDnField);
    formLayout.add(bindDnField, bindPasswordField);
    formLayout.add(useSslCheckbox, useStartTlsCheckbox);

    // Create binder
    Binder<LdapServerConfig> binder = new Binder<>(LdapServerConfig.class);
    binder.forField(nameField)
        .asRequired("Name is required")
        .bind(LdapServerConfig::getName, LdapServerConfig::setName);
    binder.forField(hostField)
        .asRequired("Host is required")
        .bind(LdapServerConfig::getHost, LdapServerConfig::setHost);
    binder.forField(portField)
        .asRequired("Port is required")
        .bind(LdapServerConfig::getPort, LdapServerConfig::setPort);
    binder.bind(baseDnField, LdapServerConfig::getBaseDn, LdapServerConfig::setBaseDn);
    binder.bind(bindDnField, LdapServerConfig::getBindDn, LdapServerConfig::setBindDn);
    binder.bind(bindPasswordField,
        LdapServerConfig::getBindPassword, LdapServerConfig::setBindPassword);
    binder.bind(useSslCheckbox, LdapServerConfig::isUseSsl, LdapServerConfig::setUseSsl);
    binder.bind(useStartTlsCheckbox,
        LdapServerConfig::isUseStartTls, LdapServerConfig::setUseStartTls);

    // Load existing config or create new
    LdapServerConfig editConfig = config != null ? config : new LdapServerConfig();
    binder.readBean(editConfig);

    // Create buttons
    Button saveButton = new Button("Save", event -> {
      if (binder.validate().isOk()) {
        try {
          LdapServerConfig saveConfig = new LdapServerConfig();
          binder.writeBean(saveConfig);
          configService.saveConfiguration(saveConfig);
          showSuccess("Configuration saved: " + saveConfig.getName());
          refreshServerGrid();
          refreshNavbarServerList();
          dialog.close();
        } catch (com.vaadin.flow.data.binder.ValidationException e) {
          showError("Validation error: " + e.getMessage());
        } catch (IOException e) {
          showError("Failed to save configuration: " + e.getMessage());
        }
      } else {
        showError("Please fill in all required fields");
      }
    });
    saveButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

    Button cancelButton = new Button("Cancel", event -> dialog.close());

    HorizontalLayout buttonLayout = new HorizontalLayout(saveButton, cancelButton);
    buttonLayout.setSpacing(true);

    VerticalLayout dialogLayout = new VerticalLayout(formLayout, buttonLayout);
    dialogLayout.setPadding(false);
    dialogLayout.setSpacing(true);

    dialog.add(dialogLayout);
    dialog.open();
  }

  /**
   * Edits the selected server.
   */
  private void editSelectedServer() {
    LdapServerConfig selected = serverGrid.asSingleSelect().getValue();
    if (selected != null) {
      openServerDialog(selected);
    }
  }

  /**
   * Copies the selected server.
   */
  private void copySelectedServer() {
    LdapServerConfig selected = serverGrid.asSingleSelect().getValue();
    if (selected != null) {
      LdapServerConfig copy = selected.copy();
      openServerDialog(copy);
      showSuccess("Configuration copied. Update the name and save.");
    }
  }

  /**
   * Deletes the selected server.
   */
  private void deleteSelectedServer() {
    LdapServerConfig selected = serverGrid.asSingleSelect().getValue();
    if (selected != null && selected.getName() != null) {
      Dialog confirmDialog = new Dialog();
      confirmDialog.setHeaderTitle("Confirm Delete");
      confirmDialog.add("Are you sure you want to delete server: " + selected.getName() + "?");

      Button confirmButton = new Button("Delete", event -> {
        try {
          configService.deleteConfiguration(selected.getName());
          showSuccess("Configuration deleted: " + selected.getName());
          refreshServerGrid();
          refreshNavbarServerList();
          confirmDialog.close();
        } catch (IOException e) {
          showError("Failed to delete configuration: " + e.getMessage());
        }
      });
      confirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

      Button cancelButton = new Button("Cancel", event -> confirmDialog.close());

      HorizontalLayout buttonLayout = new HorizontalLayout(confirmButton, cancelButton);
      confirmDialog.getFooter().add(buttonLayout);
      confirmDialog.open();
    }
  }

  /**
   * Tests the selected server connection.
   */
  private void testSelectedServer() {
    LdapServerConfig selected = serverGrid.asSingleSelect().getValue();
    if (selected != null) {
      try {
        boolean success = ldapService.testConnection(selected);
        if (success) {
          showSuccess("Connection successful to: " + selected.getName());
        } else {
          showError("Connection failed to: " + selected.getName());
        }
      } catch (Exception e) {
        showError("Connection test failed: " + e.getMessage());
      }
    }
  }

  /**
   * Refreshes the navbar server list.
   */
  private void refreshNavbarServerList() {
    getUI().ifPresent(ui -> {
      MainLayout layout = (MainLayout) ui.getChildren()
          .filter(component -> component instanceof MainLayout)
          .findFirst()
          .orElse(null);
      if (layout != null) {
        layout.refreshServerList();
      }
    });
  }

  /**
   * Shows a success notification.
   *
   * @param message message to display
   */
  private void showSuccess(String message) {
    Notification notification = Notification.show(message, 3000, Notification.Position.TOP_CENTER);
    notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
  }

  /**
   * Shows an error notification.
   *
   * @param message message to display
   */
  private void showError(String message) {
    Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
    notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
  }
}
