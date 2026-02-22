package com.ldapbrowser.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import com.ldapbrowser.model.LdapServerConfig;
import com.ldapbrowser.service.EncryptionService.EncryptionException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for managing LDAP server configurations.
 * Handles loading and saving configurations to connections.json file.
 * Automatically encrypts/decrypts passwords based on encryption settings.
 */
@Service
public class ConfigurationService {

  private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);
  private static final String CONFIG_FILE = "connections.json";
  private final ObjectMapper objectMapper;
  private final Path configPath;
  private final Path settingsDir;
  private final EncryptionService encryptionService;

  /**
   * Constructor initializes the configuration service.
   *
   * @param encryptionService encryption service for password handling
   * @param settingsDirValue resolved path of the application settings directory
   *     (injected from {@code ldapbrowser.settings.dir} property)
   */
  public ConfigurationService(EncryptionService encryptionService,
      @Value("${ldapbrowser.settings.dir}") String settingsDirValue) {
    this.encryptionService = encryptionService;
    this.objectMapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();
    this.settingsDir = Path.of(settingsDirValue);
    this.configPath = settingsDir.resolve(CONFIG_FILE);
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
   * Automatically decrypts passwords if encryption is enabled.
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

      // Decrypt passwords
      List<LdapServerConfig> configList = new ArrayList<>(Arrays.asList(configs));
      for (LdapServerConfig config : configList) {
        decryptPassword(config);
      }

      return configList;
    } catch (JacksonException e) {
      logger.error("Failed to load configurations", e);
      return new ArrayList<>();
    }
  }

  /**
   * Saves server configurations to file.
   * Automatically encrypts passwords if encryption is enabled.
   *
   * @param configurations list of configurations to save
   * @throws IOException if save fails
   */
  public void saveConfigurations(List<LdapServerConfig> configurations) throws IOException {
    try {
      // Create copies and encrypt passwords
      List<LdapServerConfig> toSave = new ArrayList<>();
      for (LdapServerConfig config : configurations) {
        LdapServerConfig copy = config.copy();
        copy.setName(config.getName()); // Restore original name (copy adds " (Copy)")
        encryptPassword(copy);
        toSave.add(copy);
      }

      objectMapper.writeValue(configPath.toFile(), toSave);
      logger.info("Saved {} server configurations to {}", toSave.size(), configPath);
    } catch (Exception e) {
      logger.error("Failed to save configurations", e);
      throw new IOException(e);
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

  /**
   * Gets the settings directory path.
   *
   * @return settings directory path
   */
  public Path getSettingsDir() {
    return settingsDir;
  }

  /**
   * Encrypts the password in a configuration object.
   *
   * @param config configuration to encrypt password for
   */
  private void encryptPassword(LdapServerConfig config) {
    if (config.getBindPassword() == null || config.getBindPassword().isEmpty()) {
      return;
    }

    if (!encryptionService.isEncryptionEnabled()) {
      return;
    }

    // Check if already encrypted
    if (encryptionService.isPasswordEncrypted(config.getBindPassword())) {
      return;
    }

    try {
      String encrypted = encryptionService.encryptPassword(config.getBindPassword());
      config.setBindPassword(encrypted);
      logger.debug("Encrypted password for server: {}", config.getName());
    } catch (EncryptionException e) {
      logger.error("Failed to encrypt password for server: {}", config.getName(), e);
    }
  }

  /**
   * Decrypts the password in a configuration object.
   *
   * @param config configuration to decrypt password for
   */
  private void decryptPassword(LdapServerConfig config) {
    if (config.getBindPassword() == null || config.getBindPassword().isEmpty()) {
      return;
    }

    if (!encryptionService.isEncryptionEnabled()) {
      return;
    }

    // Check if encrypted
    if (!encryptionService.isPasswordEncrypted(config.getBindPassword())) {
      // Cleartext password, encrypt it for future saves (migration)
      logger.info("Found cleartext password for server: {}, will encrypt on next save", 
          config.getName());
      return;
    }

    try {
      String decrypted = encryptionService.decryptPassword(config.getBindPassword());
      config.setBindPassword(decrypted);
      logger.debug("Decrypted password for server: {}", config.getName());
    } catch (EncryptionException e) {
      logger.error("Failed to decrypt password for server: {}", config.getName(), e);
    }
  }

  /**
   * Migrates all passwords between encrypted and cleartext formats.
   *
   * @param toEncrypted true to encrypt, false to decrypt
   * @throws IOException if migration fails
   */
  public void migratePasswords(boolean toEncrypted) throws IOException {
    List<LdapServerConfig> configs = loadConfigurations();
    
    for (LdapServerConfig config : configs) {
      if (config.getBindPassword() == null || config.getBindPassword().isEmpty()) {
        continue;
      }

      try {
        if (toEncrypted) {
          // Encrypt cleartext passwords
          if (!encryptionService.isPasswordEncrypted(config.getBindPassword())) {
            String encrypted = encryptionService.encryptPassword(config.getBindPassword());
            config.setBindPassword(encrypted);
            logger.info("Encrypted password for server: {}", config.getName());
          }
        } else {
          // Decrypt encrypted passwords
          if (encryptionService.isPasswordEncrypted(config.getBindPassword())) {
            String decrypted = encryptionService.decryptPassword(config.getBindPassword());
            config.setBindPassword(decrypted);
            logger.info("Decrypted password for server: {}", config.getName());
          }
        }
      } catch (EncryptionException e) {
        logger.error("Failed to migrate password for server: {}", config.getName(), e);
        throw new IOException("Password migration failed for server: " + config.getName(), e);
      }
    }

    // Save without additional encryption/decryption
    objectMapper.writeValue(configPath.toFile(), configs);
    logger.info("Password migration completed for {} servers", configs.size());
  }
}

