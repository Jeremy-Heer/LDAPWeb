# LDAP Browser

A comprehensive Java web application for browsing, searching, and managing LDAP directories with advanced features including schema management, access control, and bulk operations.

## Technology Stack

- **Spring Boot** 3.2.0
- **Vaadin** 24.3.0 (Web UI Framework)
- **UnboundID LDAP SDK** 7.0.3
- **Java** 17+
- **Maven** (Build Tool)

## Project Status

**Current Version:** v0.19

### Completed Features
✅ **Server Management** - Grid-based server configuration with CRUD operations  
✅ **Multi-Server Support** - Concurrent operations across multiple LDAP servers with independent browser tab state  
✅ **LDAP Search** - Advanced search with filter builder, multi-scope, and result editing  
✅ **Directory Browsing** - Interactive tree navigation with entry details and inline editing  
✅ **Schema Management** - Browse, compare, and manage LDAP schemas across servers  
✅ **Entry Creation** - Create new LDAP entries with template support and validation  
✅ **Access Control** - Global/entry-level ACI management and effective rights checking  
✅ **Bulk Operations** - Import/export, bulk search, entry generation, and group membership management  
✅ **Export Functionality** - Export LDAP data in LDIF, CSV, and JSON formats  
✅ **Schema-Aware Editing** - Color-coded attributes (required/optional/operational) based on cached schema  
✅ **Connection Management** - Pooled connections with SSL/TLS, StartTLS, and automatic retry  
✅ **Configuration Persistence** - JSON-based storage in ~/.ldapbrowser/connections.json  
✅ **Code Quality** - Google Java Style compliance (0 Checkstyle violations)

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
java -jar target/ldap-browser-0.19.jar
```

## Features

### Core Features

#### Server Management
Grid-based server configuration interface with:
- Add, edit, copy, and delete server configurations
- Connection testing with detailed error reporting
- SSL/LDAPS and StartTLS security options
- JSON persistence to `~/.ldapbrowser/connections.json`
- Multi-server selection with visual badges in navbar

#### LDAP Search
Advanced search capabilities including:
- Basic and advanced filter builders
- Multiple search scopes (base, one-level, subtree)
- Configurable return attributes
- Result pagination and sorting
- Inline entry editing from results
- "Search from here" links for browsing context

#### Directory Browsing
Interactive LDAP tree navigation:
- Hierarchical tree view with lazy loading
- Entry details panel with all attributes
- Inline editing with schema-aware validation
- Create, modify, and delete entries
- Real-time updates across browser tabs

#### Schema Management
Comprehensive schema exploration:
- **Manage Tab**: Browse object classes, attribute types, matching rules, syntaxes
- **Compare Tab**: Compare schemas across multiple servers with checksums
- Server-specific schema caching for performance
- Extended Schema Info control support
- Add/edit/delete schema elements

#### Access Control
OpenLDAP-style ACI management:
- **Global Access Control**: Manage cn=config and database-level ACIs
- **Entry Access Control**: Edit ACIs on specific entries with visual builder
- **Effective Rights**: Check permissions for any DN on any entry
- ACI parsing and visual construction

#### Bulk Operations
Mass LDAP operations support:
- **Import**: LDIF file import with validation
- **Bulk Search**: Execute searches and bulk modify results
- **Generate**: Template-based entry generation with placeholders
- **Group Memberships**: Add/remove users to/from groups in bulk
- Multi-server execution with aggregated results

#### Export
Data export in multiple formats:
- LDIF (standard LDAP format)
- CSV (spreadsheet-compatible)
- JSON (programmatic access)
- Configurable scope and attributes
- Batch processing for large datasets

### Using the Application

1. **Configure Servers**
   - Navigate to the "Server" page
   - Click "Add Server" and fill in details
   - Test connection before saving
   - Servers appear in the navbar selector

2. **Select Servers for Operations**
   - Use the multi-select combo box in the navbar
   - Selected servers appear as removable badges
   - Each browser tab maintains independent selection
   - All LDAP operations execute against selected servers

3. **Search and Browse**
   - Use "Search" for filter-based queries
   - Use "Browse" for tree-based navigation
   - Click entries to view/edit details
   - Schema-aware attribute editing with color coding

4. **Manage Schema**
   - Browse schema elements by type
   - Compare schemas across servers
   - Add or modify schema elements

5. **Bulk Operations**
   - Import LDIF files
   - Generate test entries
   - Manage group memberships
   - Export data in various formats

## Architecture

### Connection Management
- Connection pooling per server (1-10 connections)
- Automatic retry on stale connections
- SSL/TLS with trust-all certificate handling
- StartTLS post-connect processing

### State Management
- UI-scoped server selection (independent browser tabs)
- Per-server schema caching for performance
- Session-based configuration storage
- Automatic cleanup on tab close

### Multi-Server Operations
- Parallel execution across selected servers
- Result aggregation with per-server success/error tracking
- Error isolation - one server failure doesn't stop others
- Comprehensive logging via LoggingService

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
│   ├── java/com/ldapbrowser/
│   │   ├── LdapBrowserApplication.java      # Spring Boot application entry
│   │   ├── model/                           # Data models
│   │   │   ├── LdapServerConfig.java        # Server configuration
│   │   │   ├── LdapEntry.java               # LDAP entry representation
│   │   │   ├── SearchFilter.java            # Search filter model
│   │   │   ├── BrowseResult.java            # Browse results
│   │   │   └── SchemaElement.java           # Schema element wrapper
│   │   ├── service/                         # Business logic
│   │   │   ├── LdapService.java             # Core LDAP operations
│   │   │   ├── ConfigurationService.java    # Server config persistence
│   │   │   └── LoggingService.java          # Structured LDAP logging
│   │   ├── ui/
│   │   │   ├── MainLayout.java              # AppLayout with navbar/drawer
│   │   │   ├── views/                       # Page views
│   │   │   │   ├── ServerView.java          # Server management
│   │   │   │   ├── SearchView.java          # Search interface
│   │   │   │   ├── BrowseView.java          # Tree browser
│   │   │   │   ├── SchemaView.java          # Schema management
│   │   │   │   ├── Create.java              # Entry creation
│   │   │   │   ├── AccessView.java          # Access control
│   │   │   │   ├── BulkView.java            # Bulk operations
│   │   │   │   └── ExportView.java          # Export functionality
│   │   │   └── components/                  # Reusable UI components
│   │   │       ├── LdapTreeBrowser.java     # Tree navigation component
│   │   │       ├── EntryEditor.java         # Entry editing component
│   │   │       ├── AdvancedSearchBuilder.java
│   │   │       ├── SchemaManageTab.java
│   │   │       ├── SchemaCompareTab.java
│   │   │       ├── AciBuilderDialog.java
│   │   │       └── ...                      # Various specialized tabs
│   │   └── util/                            # Utilities
│   │       ├── AciParser.java               # ACI string parser
│   │       ├── SchemaCompareUtil.java       # Schema canonicalization
│   │       └── OidLookupTable.java          # OID to name mapping
│   └── resources/
│       ├── application.properties           # Main configuration
│       └── application-development.properties # Dev profile
└── test/
    └── java/                                # Unit tests
```

## Navigation

The application includes the following pages:

- **Server** ✅ - Grid-based server configuration management with CRUD operations
- **Search** ✅ - Advanced LDAP search with filter builder and result editing
- **Browse** ✅ - Interactive tree-based directory navigation with inline editing
- **Schema** ✅ - Schema browsing, comparison, and management across servers
- **Create** ✅ - Create new LDAP entries with templates and validation
- **Access** ✅ - ACI management (global, entry-level, effective rights)
- **Bulk** ✅ - Bulk operations (import, search, generate, group memberships)
- **Export** ✅ - Export data in LDIF, CSV, and JSON formats

## Key Features Summary

### Technical Highlights
- **74 Java source files** across models, services, UI components, and views
- **Connection pooling** with 1-10 connections per server
- **Schema caching** for improved performance
- **Multi-server operations** with parallel execution and result aggregation
- **UI-scoped state** allowing independent browser tabs
- **Schema-aware editing** with color-coded attributes
- **Advanced search** with filter builder and multiple scopes
- **Tree navigation** with lazy loading and real-time updates
- **Bulk operations** supporting import, export, search, and group management
- **ACI management** with visual builder and effective rights checking

### Integration Points
- UnboundID LDAP SDK for all LDAP protocol operations
- Vaadin 24.3.0 for reactive web UI
- Spring Boot 3.2.0 for dependency injection and application framework
- JSON-based configuration persistence
- Structured logging for all LDAP operations

## Contributing

This project follows Google Java Style conventions. Please ensure your code passes Checkstyle validation before committing:

```bash
mvn checkstyle:check
```

The project maintains **0 Checkstyle violations** - please keep it that way!

## License

[Add license information here]

## Documentation

See the `docs/` directory for detailed documentation:
- [Changelog](docs/changelog.md) - Complete version history (v0.1 through v0.20)
- [Requirements](docs/requirements.md) - Original feature requirements
- [Implementation Summaries](docs/) - Detailed notes for each version

---

**Current Status:** Version 0.19 - Full-featured LDAP browser with all major components implemented and functional. Active development continues with connection retry and recovery improvements.
