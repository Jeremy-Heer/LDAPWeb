package com.ldapbrowser.model;

/**
 * Wrapper class to associate a schema element with its source server.
 *
 * @param <T> the type of schema element
 */
public class SchemaElement<T> {
  
  private final T element;
  private final String serverName;
  private final LdapServerConfig serverConfig;

  /**
   * Creates a schema element wrapper.
   *
   * @param element the schema element
   * @param serverName the name of the server it came from
   */
  public SchemaElement(T element, String serverName) {
    this.element = element;
    this.serverName = serverName;
    this.serverConfig = null;
  }

  /**
   * Creates a schema element wrapper with server config.
   *
   * @param element the schema element
   * @param serverName the name of the server it came from
   * @param serverConfig the server configuration
   */
  public SchemaElement(T element, String serverName, LdapServerConfig serverConfig) {
    this.element = element;
    this.serverName = serverName;
    this.serverConfig = serverConfig;
  }

  /**
   * Gets the schema element.
   *
   * @return the schema element
   */
  public T getElement() {
    return element;
  }

  /**
   * Gets the server name.
   *
   * @return the server name
   */
  public String getServerName() {
    return serverName;
  }

  /**
   * Gets the server configuration.
   *
   * @return the server configuration
   */
  public LdapServerConfig getServerConfig() {
    return serverConfig;
  }
}
