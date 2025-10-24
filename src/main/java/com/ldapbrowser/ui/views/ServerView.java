package com.ldapbrowser.ui.views;

import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.ui.MainLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
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
  private final Binder<LdapServerConfig> binder = new Binder<>(LdapServerConfig.class);

  private ComboBox<String> serverSelector;
  private TextField nameField;
  private TextField hostField;
  private IntegerField portField;
  private TextField baseDnField;
  private TextField bindDnField;
  private PasswordField bindPasswordField;
  private Checkbox useSslCheckbox;
  private Checkbox useStartTlsCheckbox;

  private Button testButton;
  private Button saveButton;
  private Button copyButton;
  private Button deleteButton;

  private LdapServerConfig currentConfig;

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
    setMaxWidth("900px");

    H2 title = new H2("Server Configuration");
    add(title);

    createServerSelector();
    createFormLayout();
    createActionButtons();

    loadNewConfiguration();
  }

  /**
   * Creates the server selector dropdown.
   */
  private void createServerSelector() {
    serverSelector = new ComboBox<>("Existing Servers");
    serverSelector.setPlaceholder("Select a server or create new");
    serverSelector.setWidth("100%");
    serverSelector.setClearButtonVisible(true);
    refreshServerList();

    serverSelector.addValueChangeListener(event -> {
      String serverName = event.getValue();
      if (serverName != null) {
        loadConfiguration(serverName);
      } else {
        loadNewConfiguration();
      }
    });

    add(serverSelector);
  }

  /**
   * Creates the form layout with all fields.
   */
  private void createFormLayout() {
    FormLayout formLayout = new FormLayout();
    formLayout.setResponsiveSteps(
        new FormLayout.ResponsiveStep("0", 1),
        new FormLayout.ResponsiveStep("500px", 2)
    );

    // Name field
    nameField = new TextField("Server Name");
    nameField.setRequired(true);
    nameField.setPlaceholder("e.g., Production LDAP");

    // Host field
    hostField = new TextField("Host");
    hostField.setRequired(true);
    hostField.setPlaceholder("e.g., ldap.example.com");

    // Port field
    portField = new IntegerField("Port");
    portField.setValue(389);
    portField.setMin(1);
    portField.setMax(65535);
    portField.setStepButtonsVisible(true);

    // Base DN field
    baseDnField = new TextField("Base DN");
    baseDnField.setPlaceholder("e.g., dc=example,dc=com");

    // Bind DN field
    bindDnField = new TextField("Bind DN");
    bindDnField.setPlaceholder("e.g., cn=admin,dc=example,dc=com");

    // Bind Password field
    bindPasswordField = new PasswordField("Bind Password");
    bindPasswordField.setPlaceholder("Enter password");

    // SSL checkbox
    useSslCheckbox = new Checkbox("Use SSL (LDAPS)");
    useSslCheckbox.addValueChangeListener(event -> {
      if (event.getValue()) {
        portField.setValue(636);
        useStartTlsCheckbox.setValue(false);
      } else {
        portField.setValue(389);
      }
    });

    // StartTLS checkbox
    useStartTlsCheckbox = new Checkbox("Use StartTLS");
    useStartTlsCheckbox.addValueChangeListener(event -> {
      if (event.getValue()) {
        useSslCheckbox.setValue(false);
      }
    });

    // Add fields to form
    formLayout.add(nameField, hostField);
    formLayout.add(portField, baseDnField);
    formLayout.add(bindDnField, bindPasswordField);
    formLayout.add(useSslCheckbox, useStartTlsCheckbox);

    // Set up data binding
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

    add(formLayout);
  }

  /**
   * Creates action buttons.
   */
  private void createActionButtons() {
    testButton = new Button("Test Connection", event -> testConnection());
    testButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    saveButton = new Button("Save", event -> saveConfiguration());
    saveButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

    copyButton = new Button("Copy", event -> copyConfiguration());

    deleteButton = new Button("Delete", event -> deleteConfiguration());
    deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);

    HorizontalLayout buttonLayout = new HorizontalLayout(
        testButton, saveButton, copyButton, deleteButton
    );
    buttonLayout.setSpacing(true);

    add(buttonLayout);
  }

  /**
   * Tests the connection to the LDAP server.
   */
  private void testConnection() {
    if (!binder.validate().isOk()) {
      showError("Please fill in all required fields");
      return;
    }

    try {
      LdapServerConfig config = new LdapServerConfig();
      binder.writeBean(config);

      boolean success = ldapService.testConnection(config);
      if (success) {
        showSuccess("Connection successful!");
      } else {
        showError("Connection failed. Check your settings.");
      }
    } catch (com.vaadin.flow.data.binder.ValidationException e) {
      showError("Validation error: " + e.getMessage());
    } catch (Exception e) {
      showError("Connection test failed: " + e.getMessage());
    }
  }

  /**
   * Saves the server configuration.
   */
  private void saveConfiguration() {
    if (!binder.validate().isOk()) {
      showError("Please fill in all required fields");
      return;
    }

    try {
      LdapServerConfig config = new LdapServerConfig();
      binder.writeBean(config);

      configService.saveConfiguration(config);
      showSuccess("Configuration saved: " + config.getName());
      refreshServerList();
      currentConfig = config;
      serverSelector.setValue(config.getName());

      // Refresh the navbar server list
      getUI().ifPresent(ui -> {
        if (ui.getInternals().getActiveRouterTargetsChain().get(0)
            .getElement().getComponent().isPresent()) {
          MainLayout layout = (MainLayout) ui.getChildren()
              .filter(component -> component instanceof MainLayout)
              .findFirst()
              .orElse(null);
          if (layout != null) {
            layout.refreshServerList();
          }
        }
      });
    } catch (com.vaadin.flow.data.binder.ValidationException e) {
      showError("Validation error: " + e.getMessage());
    } catch (IOException e) {
      showError("Failed to save configuration: " + e.getMessage());
    }
  }

  /**
   * Copies the current configuration.
   */
  private void copyConfiguration() {
    if (currentConfig != null) {
      LdapServerConfig copy = currentConfig.copy();
      binder.readBean(copy);
      currentConfig = null;
      serverSelector.clear();
      showSuccess("Configuration copied. Update the name and save.");
    }
  }

  /**
   * Deletes the current configuration.
   */
  private void deleteConfiguration() {
    if (currentConfig != null && currentConfig.getName() != null) {
      try {
        configService.deleteConfiguration(currentConfig.getName());
        showSuccess("Configuration deleted: " + currentConfig.getName());
        refreshServerList();
        loadNewConfiguration();
      } catch (IOException e) {
        showError("Failed to delete configuration: " + e.getMessage());
      }
    }
  }

  /**
   * Loads a configuration by name.
   *
   * @param name server name
   */
  private void loadConfiguration(String name) {
    configService.getConfiguration(name).ifPresentOrElse(config -> {
      currentConfig = config;
      binder.readBean(config);
      deleteButton.setEnabled(true);
      copyButton.setEnabled(true);
    }, this::loadNewConfiguration);
  }

  /**
   * Loads a new empty configuration.
   */
  private void loadNewConfiguration() {
    currentConfig = new LdapServerConfig();
    binder.readBean(currentConfig);
    serverSelector.clear();
    deleteButton.setEnabled(false);
    copyButton.setEnabled(false);
  }

  /**
   * Refreshes the server selector list.
   */
  private void refreshServerList() {
    List<LdapServerConfig> configs = configService.loadConfigurations();
    serverSelector.setItems(configs.stream().map(LdapServerConfig::getName).toList());
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
