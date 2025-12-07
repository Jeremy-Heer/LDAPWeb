package com.ldapbrowser.ui.views;

import com.ldapbrowser.exception.CertificateValidationException;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.ConfigurationService;
import com.ldapbrowser.service.LdapService;
import com.ldapbrowser.service.TruststoreService;
import com.ldapbrowser.ui.MainLayout;
import com.ldapbrowser.ui.dialogs.TlsCertificateDialog;
import com.ldapbrowser.ui.utils.NotificationHelper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
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
  private final TruststoreService truststoreService;

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
   * @param truststoreService truststore service
   */
  public ServerView(ConfigurationService configService, LdapService ldapService,
      TruststoreService truststoreService) {
    this.configService = configService;
    this.ldapService = ldapService;
    this.truststoreService = truststoreService;

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

    TextField baseDnField = new TextField("Default Base");
    baseDnField.setPlaceholder("e.g., dc=example,dc=com");

    TextField bindDnField = new TextField("Bind DN");
    bindDnField.setPlaceholder("e.g., cn=admin,dc=example,dc=com");

    PasswordField bindPasswordField = new PasswordField("Bind Password");
    bindPasswordField.setPlaceholder("Enter password");

    Checkbox useSslCheckbox = new Checkbox("Use SSL (LDAPS)");
    Checkbox useStartTlsCheckbox = new Checkbox("Use StartTLS");
    Checkbox validateCertificateCheckbox = new Checkbox(
        "Validate Certificate (recommended for production)"
    );

    // Update validate certificate checkbox state based on SSL/StartTLS
    Runnable updateValidateCertState = () -> {
      boolean hasSecurity = useSslCheckbox.getValue() || useStartTlsCheckbox.getValue();
      validateCertificateCheckbox.setEnabled(hasSecurity);
      if (!hasSecurity) {
        validateCertificateCheckbox.setValue(false);
      }
    };

    useSslCheckbox.addValueChangeListener(event -> {
      if (event.getValue()) {
        useStartTlsCheckbox.setValue(false);
      }
      updateValidateCertState.run();
    });

    useStartTlsCheckbox.addValueChangeListener(event -> {
      if (event.getValue()) {
        useSslCheckbox.setValue(false);
      }
      updateValidateCertState.run();
    });

    // Create View Certificate button
    Button viewCertButton = new Button("View Certificate");
    viewCertButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    viewCertButton.setEnabled(false);
    
    // Enable view cert button when SSL/StartTLS is selected
    Runnable updateViewCertState = () -> {
      boolean hasSecure = useSslCheckbox.getValue() || useStartTlsCheckbox.getValue();
      viewCertButton.setEnabled(hasSecure);
    };
    
    useSslCheckbox.addValueChangeListener(event -> updateViewCertState.run());
    useStartTlsCheckbox.addValueChangeListener(event -> updateViewCertState.run());
    
    viewCertButton.addClickListener(event -> {
      if (hostField.getValue() == null || hostField.getValue().trim().isEmpty()) {
        NotificationHelper.showError("Please enter a host before viewing certificate");
        return;
      }
      if (portField.getValue() == null) {
        NotificationHelper.showError("Please enter a port before viewing certificate");
        return;
      }
      
      // Create temporary config for certificate retrieval
      LdapServerConfig tempConfig = new LdapServerConfig();
      // Use server name if available, otherwise use "Temporary"
      String serverName = nameField.getValue();
      tempConfig.setName(
          (serverName != null && !serverName.trim().isEmpty()) ? serverName : "Temporary"
      );
      tempConfig.setHost(hostField.getValue());
      tempConfig.setPort(portField.getValue());
      tempConfig.setUseSsl(useSslCheckbox.getValue());
      tempConfig.setUseStartTls(useStartTlsCheckbox.getValue());
      
      // Retrieve and display certificate
      viewServerCertificate(tempConfig);
    });

    // Create browse button for base DN
    Button baseDnBrowseButton = new Button(
        com.vaadin.flow.component.icon.VaadinIcon.FOLDER_OPEN.create());
    baseDnBrowseButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
    baseDnBrowseButton.setTooltipText("Select DN from Directory");

    HorizontalLayout baseDnLayout = new HorizontalLayout();
    baseDnLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
    baseDnLayout.setSpacing(false);
    baseDnLayout.getStyle().set("gap", "var(--lumo-space-xs)");
    baseDnLayout.add(baseDnField, baseDnBrowseButton);
    baseDnLayout.expand(baseDnField);

    // Add fields to form
    formLayout.add(nameField, hostField);
    formLayout.add(portField);
    formLayout.add(baseDnLayout, 2);
    formLayout.add(bindDnField, bindPasswordField);
    formLayout.add(useSslCheckbox, useStartTlsCheckbox);
    formLayout.add(validateCertificateCheckbox, viewCertButton);

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
    binder.bind(validateCertificateCheckbox,
        LdapServerConfig::isValidateCertificate, LdapServerConfig::setValidateCertificate);

    // Load existing config or create new
    LdapServerConfig editConfig = config != null ? config : new LdapServerConfig();
    binder.readBean(editConfig);
    
    // Update validate certificate checkbox state after loading config
    updateValidateCertState.run();

    // Setup browse button click handler (now that all fields are in scope)
    baseDnBrowseButton.addClickListener(e -> {
      // Only show browse dialog if at least one field is filled to connect
      if (hostField.getValue() != null && !hostField.getValue().trim().isEmpty()) {
        String host = hostField.getValue();
        int port = portField.getValue() != null ? portField.getValue() : 389;
        String bindDn = bindDnField.getValue();
        String bindPassword = bindPasswordField.getValue();
        boolean useSsl = useSslCheckbox.getValue();
        boolean useStartTls = useStartTlsCheckbox.getValue();
        
        showBaseDnBrowseDialog(baseDnField, host, port, bindDn, bindPassword, useSsl, useStartTls);
      } else {
        NotificationHelper.showError("Please enter a host before browsing for DN");
      }
    });

    // Create buttons
    Button saveButton = new Button("Save", event -> {
      if (binder.validate().isOk()) {
        try {
          LdapServerConfig saveConfig = new LdapServerConfig();
          binder.writeBean(saveConfig);
          configService.saveConfiguration(saveConfig);
          NotificationHelper.showSuccess("Configuration saved: " + saveConfig.getName());
          refreshServerGrid();
          refreshNavbarServerList();
          dialog.close();
        } catch (com.vaadin.flow.data.binder.ValidationException e) {
          NotificationHelper.showError("Validation error: " + e.getMessage());
        } catch (IOException e) {
          NotificationHelper.showError("Failed to save configuration: " + e.getMessage());
        }
      } else {
        NotificationHelper.showError("Please fill in all required fields");
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
      NotificationHelper.showSuccess("Configuration copied. Update the name and save.");
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
          NotificationHelper.showSuccess("Configuration deleted: " + selected.getName());
          refreshServerGrid();
          refreshNavbarServerList();
          confirmDialog.close();
        } catch (IOException e) {
          NotificationHelper.showError("Failed to delete configuration: " + e.getMessage());
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
          NotificationHelper.showSuccess("Connection successful to: " + selected.getName());
        } else {
          NotificationHelper.showError("Connection failed to: " + selected.getName());
        }
      } catch (CertificateValidationException e) {
        // Certificate validation failed - show TLS dialog to allow user to import
        handleCertificateValidationFailure(selected, e);
      } catch (Exception e) {
        NotificationHelper.showError("Connection test failed: " + e.getMessage());
      }
    }
  }

  /**
   * Handles certificate validation failure by showing TLS certificate dialog.
   *
   * @param config the server configuration
   * @param exception the certificate validation exception
   */
  private void handleCertificateValidationFailure(LdapServerConfig config,
      CertificateValidationException exception) {
    
    java.security.cert.X509Certificate serverCert = exception.getServerCertificate();
    
    if (serverCert == null) {
      NotificationHelper.showError(
          "Certificate validation failed, but certificate details are not available");
      return;
    }

    // Create and show TLS certificate dialog
    TlsCertificateDialog dialog = new TlsCertificateDialog(
        serverCert,
        config,
        truststoreService,
        false, // Certificate is not trusted (validation failed)
        imported -> {
          if (imported) {
            // Certificate was imported, clear the failure and retry connection
            ldapService.clearCertificateFailure(config.getName());
            NotificationHelper.showSuccess("Certificate imported. Try connecting again.");
          }
        }
    );

    dialog.open();
  }

  /**
   * Shows the DN browser dialog for selecting base DN.
   *
   * @param targetField the field to populate with selected DN
   * @param host the LDAP server host
   * @param port the LDAP server port
   * @param bindDn the bind DN for authentication
   * @param bindPassword the bind password
   * @param useSsl whether to use SSL
   * @param useStartTls whether to use StartTLS
   */
  private void showBaseDnBrowseDialog(TextField targetField, String host, int port,
      String bindDn, String bindPassword, boolean useSsl, boolean useStartTls) {
    // Create a temporary server config for browsing
    LdapServerConfig tempConfig = new LdapServerConfig(
        "Temporary",
        host,
        port,
        "", // baseDn
        bindDn != null ? bindDn : "",
        bindPassword != null ? bindPassword : "",
        useSsl,
        useStartTls
    );
    // Don't validate certificate for temporary browse connection
    tempConfig.setValidateCertificate(false);

    new com.ldapbrowser.ui.dialogs.DnBrowserDialog(ldapService, truststoreService)
        .withServerConfigs(java.util.Collections.singletonList(tempConfig))
        .onDnSelected(dn -> targetField.setValue(dn))
        .open();
  }

  /**
   * Views the server certificate for a given configuration.
   *
   * @param config the server configuration
   */
  private void viewServerCertificate(LdapServerConfig config) {
    try {
      // Retrieve the server certificate
      java.security.cert.X509Certificate cert = ldapService.retrieveServerCertificate(config);
      
      if (cert == null) {
        NotificationHelper.showError("Failed to retrieve server certificate");
        return;
      }
      
      // Determine if certificate is trusted
      boolean isTrusted = isCertificateTrusted(cert);
      
      // Show the certificate dialog
      TlsCertificateDialog certDialog = new TlsCertificateDialog(
          cert,
          config,
          truststoreService,
          isTrusted,
          imported -> {
            if (imported) {
              NotificationHelper.showSuccess("Certificate imported successfully");
            }
          }
      );
      certDialog.open();
    } catch (Exception e) {
      NotificationHelper.showError("Error retrieving certificate: " + e.getMessage());
    }
  }

  /**
   * Checks if a certificate is already trusted in the truststore.
   *
   * @param cert the certificate to check
   * @return true if the certificate is in the truststore
   */
  private boolean isCertificateTrusted(java.security.cert.X509Certificate cert) {
    try {
      List<String> aliases = truststoreService.listCertificates();
      for (String alias : aliases) {
        java.security.cert.Certificate trustedCert = truststoreService.getCertificate(alias);
        if (trustedCert != null && trustedCert.equals(cert)) {
          return true;
        }
      }
    } catch (Exception e) {
      // If we can't check, assume untrusted
      return false;
    }
    return false;
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

}
