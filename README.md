# LDAP Browser

A comprehensive Java web application for browsing, searching, and managing LDAP directories.

## Technology Stack

- **Spring Boot** 3.2.0
- **Vaadin** 24.3.0 (Web UI Framework)
- **UnboundID LDAP SDK** 7.0.3
- **Java** 17+
- **Maven** (Build Tool)

## Project Status

**Current Version:** v0.2.1-SNAPSHOT

### Completed Features (v0.2.1)
✅ Initial UI layout with Navbar and Drawer navigation  
✅ Server configuration management with full CRUD operations  
✅ LDAP connection testing with SSL/TLS support  
✅ JSON-based configuration persistence (~/.ldapbrowser/connections.json)  
✅ Enhanced multi-server selection with ComboBox and visual badges  
✅ Connection pooling for efficient LDAP operations  
✅ Google Java Style compliance (0 Checkstyle violations)

### Planned Features
- LDAP search functionality with filter builder
- Directory browsing with tree navigator
- Entry details view with attribute editing
- Schema exploration
- Access control management
- Bulk operations
- Import/Export capabilities

## Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.6 or higher

### Build and Run

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd LDAPWeb
   ```

2. **Compile the project**
   ```bash
   mvn clean compile
   ```

3. **Run in development mode**
   ```bash
   ./dev-reload.sh
   ```
   Or:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=development
   ```

4. **Access the application**
   - Open your browser to: http://localhost:8080
   - The application will automatically reload on code changes

### Production Build

Create a production-ready JAR:
```bash
mvn clean package -Pproduction -DskipTests
```

Run the production JAR:
```bash
java -jar target/ldap-browser-0.2.1-SNAPSHOT.jar
```

## Features

### Server Configuration
Configure and manage LDAP server connections with the following capabilities:

- **Server Details**: Name, host, port configuration
- **Authentication**: Bind DN and password support
- **Security**: SSL/LDAPS and StartTLS support
- **Connection Testing**: Validate settings before saving
- **Persistence**: Configurations saved to `~/.ldapbrowser/connections.json`
- **Multi-Server Support**: Select multiple servers for operations

### Using the Application

1. **Configure a Server**
   - Navigate to the "Server" page (default)
   - Fill in server details (name, host, port)
   - Optionally configure Base DN and bind credentials
   - Enable SSL or StartTLS if required
   - Click "Test Connection" to validate settings
   - Click "Save" to persist the configuration

2. **Select Servers**
   - Use the multi-select combo box in the navbar
   - Select one or more configured servers
   - Selected servers appear as badges with remove buttons
   - Click X on any badge to deselect a server
   - Selected servers will be used for LDAP operations

3. **Manage Configurations**
   - **Load**: Select from the dropdown to edit existing servers
   - **Copy**: Duplicate a configuration to create a new one
   - **Delete**: Remove unwanted configurations

## Configuration File

Server configurations are stored in:
```
~/.ldapbrowser/connections.json
```

Example configuration:
```json
[
  {
    "name": "Production LDAP",
    "host": "ldap.example.com",
    "port": 389,
    "baseDn": "dc=example,dc=com",
    "bindDn": "cn=admin,dc=example,dc=com",
    "bindPassword": "password",
    "useSsl": false,
    "useStartTls": true
  }
]
```

## Development

### Code Quality

Run Checkstyle validation:
```bash
mvn checkstyle:check
```

### Testing

Run unit tests:
```bash
mvn test
```

### Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/ldapbrowser/
│   │       ├── LdapBrowserApplication.java  # Application entry point
│   │       └── ui/
│   │           ├── MainLayout.java          # Main layout with navbar/drawer
│   │           └── views/                   # View components
│   └── resources/
│       ├── application.properties           # Main configuration
│       └── application-development.properties
└── test/
    └── java/                                # Unit tests
```

## Navigation

The application includes the following pages:

- **Server** ✅ - Configure LDAP server connections (COMPLETED)
- **Search** 🚧 - Perform LDAP searches and view results (Under construction)
- **Browse** 🚧 - Navigate LDAP directory tree (Under construction)
- **Schema** 🚧 - Explore LDAP schema information (Under construction)
- **Access** 🚧 - Manage access control and permissions (Under construction)
- **Bulk** 🚧 - Perform bulk LDAP operations (Under construction)
- **Import/Export** 🚧 - Import and export LDAP data (Under construction)

## Contributing

This project follows Google Java Style conventions. Please ensure your code passes Checkstyle validation before committing:

```bash
mvn checkstyle:check
```

## License

[Add license information here]

## Documentation

See the `docs/` directory for additional documentation:
- [Requirements](docs/requirements.md) - Detailed feature requirements
- [Changelog](docs/changelog.md) - Version history and changes

---

**Note:** This is version 0.2.1 with functional server configuration and enhanced UI. Search and other features are under construction.
