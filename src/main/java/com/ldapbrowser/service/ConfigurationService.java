package com.ldapbrowser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ldapbrowser.model.LdapServerConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for managing LDAP server configurations.
 * Handles loading and saving configurations to connections.json file.
 */
@Service
public class ConfigurationService {

  private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);
  private static final String CONFIG_FILE = "connections.json";
  private final ObjectMapper objectMapper;
  private final Path configPath;

  /**
   * Constructor initializes the configuration service.
   */
  public ConfigurationService() {
    this.objectMapper = new ObjectMapper();
    this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    this.configPath = Paths.get(System.getProperty("user.home"), ".ldapbrowser", CONFIG_FILE);
    ensureConfigDirectoryExists();
  }

  /**
   * Ensures the configuration directory exists.
   */
  private void ensureConfigDirectoryExists() {
    try {
      Path configDir = configPath.getParent();
      if (!Files.exists(configDir)) {
        Files.createDirectories(configDir);
        logger.info("Created configuration directory: {}", configDir);
      }
    } catch (IOException e) {
      logger.error("Failed to create configuration directory", e);
    }
  }

  /**
   * Loads all server configurations from file.
   *
   * @return list of server configurations
   */
  public List<LdapServerConfig> loadConfigurations() {
    try {
      File configFile = configPath.toFile();
      if (!configFile.exists()) {
        logger.info("Configuration file not found, returning empty list");
        return new ArrayList<>();
      }

      LdapServerConfig[] configs = objectMapper.readValue(configFile, LdapServerConfig[].class);
      logger.info("Loaded {} server configurations", configs.length);
      return new ArrayList<>(Arrays.asList(configs));
    } catch (IOException e) {
      logger.error("Failed to load configurations", e);
      return new ArrayList<>();
    }
  }

  /**
   * Saves server configurations to file.
   *
   * @param configurations list of configurations to save
   * @throws IOException if save fails
   */
  public void saveConfigurations(List<LdapServerConfig> configurations) throws IOException {
    try {
      objectMapper.writeValue(configPath.toFile(), configurations);
      logger.info("Saved {} server configurations to {}", configurations.size(), configPath);
    } catch (IOException e) {
      logger.error("Failed to save configurations", e);
      throw e;
    }
  }

  /**
   * Saves a single server configuration.
   *
   * @param config configuration to save
   * @throws IOException if save fails
   */
  public void saveConfiguration(LdapServerConfig config) throws IOException {
    List<LdapServerConfig> configs = loadConfigurations();

    // Update existing or add new
    Optional<LdapServerConfig> existing = configs.stream()
        .filter(c -> c.getName().equals(config.getName()))
        .findFirst();

    if (existing.isPresent()) {
      int index = configs.indexOf(existing.get());
      configs.set(index, config);
      logger.info("Updated configuration: {}", config.getName());
    } else {
      configs.add(config);
      logger.info("Added new configuration: {}", config.getName());
    }

    saveConfigurations(configs);
  }

  /**
   * Deletes a server configuration by name.
   *
   * @param name name of configuration to delete
   * @throws IOException if save fails
   */
  public void deleteConfiguration(String name) throws IOException {
    List<LdapServerConfig> configs = loadConfigurations();
    configs.removeIf(c -> c.getName().equals(name));
    saveConfigurations(configs);
    logger.info("Deleted configuration: {}", name);
  }

  /**
   * Gets a configuration by name.
   *
   * @param name configuration name
   * @return optional configuration
   */
  public Optional<LdapServerConfig> getConfiguration(String name) {
    return loadConfigurations().stream()
        .filter(c -> c.getName().equals(name))
        .findFirst();
  }

  /**
   * Checks if a configuration with the given name exists.
   *
   * @param name configuration name
   * @return true if exists
   */
  public boolean configurationExists(String name) {
    return getConfiguration(name).isPresent();
  }

  /**
   * Gets the configuration file path.
   *
   * @return configuration file path
   */
  public Path getConfigPath() {
    return configPath;
  }
}
