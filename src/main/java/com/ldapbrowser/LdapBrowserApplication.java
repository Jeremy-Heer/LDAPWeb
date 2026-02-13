package com.ldapbrowser;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LDAP Browser Application entry point.
 * A comprehensive Java web application for browsing, searching, and managing LDAP directories.
 */
@SpringBootApplication
@Push(value = PushMode.AUTOMATIC, transport = Transport.WEBSOCKET_XHR)
@StyleSheet(Lumo.STYLESHEET)
public class LdapBrowserApplication implements AppShellConfigurator {

  /**
   * Main application entry point.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(LdapBrowserApplication.class, args);
  }
}
