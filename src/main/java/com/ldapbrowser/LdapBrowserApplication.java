package com.ldapbrowser;

import com.vaadin.flow.component.page.AppShellConfigurator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LDAP Browser Application entry point.
 * A comprehensive Java web application for browsing, searching, and managing LDAP directories.
 */
@SpringBootApplication
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
