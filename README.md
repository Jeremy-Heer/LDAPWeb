# LDAP Browser

A comprehensive Java web application for browsing, searching, and managing LDAP directories with advanced features including schema management, access control, bulk operations, and multi-mode authentication.

## Technology Stack

- **Spring Boot** 4.0.0
- **Vaadin** 25.0.3 (Web UI Framework)
- **Spring Security** 7.0 (Authentication & Authorization)
- **UnboundID LDAP SDK** 7.0.3
- **Java** 21+
- **Maven** (Build Tool)

## Project Status

**Current Version:** v0.65

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
✅ **Authentication** - Three modes: none (open), local (form login with JSON user store), OAuth2/OIDC
✅ **Role-Based Access Control** - ADMIN and VIEWER roles with per-view authorization
✅ **Password Encryption** - AES-GCM encryption with `ENC:` prefix and keystore-backed key management
✅ **Security Hardening** - Localhost binding, POSIX file permissions, defensive copies, `@PreDestroy` cleanup
✅ **Code Quality** - Google Java Style compliance (0 Checkstyle violations), 278 unit tests

## Quick Start

### Prerequisites
- Java 21 or higher
- Maven 3.6 or higher

### Build and Run

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd LDAPWeb
   ```

2. **Compile and test**
   ```bash
   mvn clean compile
   mvn test
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
   - Open your browser to: http://localhost:8081
   - The default auth mode is `none` — no login required

### Production Build

Create a production-ready JAR:
```bash
mvn clean package -Pproduction -DskipTests
```

Run the production JAR:
```bash
java -jar target/ldap-browser-0.65.jar
```

## Authentication

The application supports three authentication modes controlled by the
`ldapbrowser.auth.mode` property. The default mode is `none`.

### Mode: `none` (default)

No authentication — all routes are open to any user. Best for local
single-user deployments.

```bash
# Default — just run the JAR
java -jar target/ldap-browser-0.65.jar
```

No login screen is shown. All views are accessible.

### Mode: `local` (form login)

Form-based login backed by a JSON user store at
`~/.ldapbrowser/users.json`. Passwords are hashed with BCrypt.

```bash
java -jar target/ldap-browser-0.65.jar \
  --spring.profiles.active=local-auth
```

On first run, a default **admin** account is created with a random
password printed to the console:

```
============================================
  Initial admin account created
  Username : admin
  Password : <random>
  Change this password after first login!
============================================
```

No manual user setup is required. The admin user has full access to
all views. Additional users can be managed through the Settings view.

### Mode: `oauth` (OpenID Connect)

Delegates authentication to any OIDC-compliant identity provider
(Keycloak, Entra ID, Okta, Auth0, etc.).

```bash
java -jar target/ldap-browser-0.65.jar \
  --spring.profiles.active=oauth
```

You must configure your provider's client credentials. Edit
`application-oauth.properties` or pass them as command-line flags:

```properties
spring.security.oauth2.client.registration.oidc.client-id=your-client-id
spring.security.oauth2.client.registration.oidc.client-secret=your-secret
spring.security.oauth2.client.provider.oidc.issuer-uri=https://idp.example.com/realms/myrealm
```

#### Example: Google Sign-In

To use Google for authentication, you first need to create OAuth 2.0 credentials in the Google Cloud Console.

1.  **Google Cloud Console Setup**
    1.  Go to the [Google Cloud Console](https://console.cloud.google.com/).
    2.  Create a new project or select an existing one.
    3.  Navigate to **APIs & Services > Credentials**.
    4.  Click **Create Credentials** and select **OAuth client ID**.
    5.  Choose **Web application** as the application type.
    6.  Under **Authorized redirect URIs**, add `http://localhost:8081/login/oauth2/code/oidc`. This is the default callback URL Spring Security uses.
    7.  Click **Create**. Copy the generated **Client ID** and **Client Secret**.

2.  **Application Configuration**
    Run the application with the `oauth` profile and your Google credentials. The issuer URI for Google is `https://accounts.google.com`.

    ```bash
    java -jar target/ldap-browser-0.65.jar \
      --spring.profiles.active=oauth \
      --spring.security.oauth2.client.registration.oidc.client-id=YOUR_GOOGLE_CLIENT_ID \
      --spring.security.oauth2.client.registration.oidc.client-secret=YOUR_GOOGLE_CLIENT_SECRET \
      --spring.security.oauth2.client.provider.oidc.issuer-uri=https://accounts.google.com
    ```

    Alternatively, add these properties to `src/main/resources/application-oauth.properties`.

#### Role Mapping

OIDC tokens are mapped to application roles using these properties:

| Property | Default | Description |
|----------|---------|-------------|
| `ldapbrowser.oauth.role-claim` | `roles` | Token claim containing role names |
| `ldapbrowser.oauth.admin-role` | `ldap-admin` | Claim value mapped to ADMIN |
| `ldapbrowser.oauth.viewer-role` | `ldap-viewer` | Claim value mapped to VIEWER |
| `ldapbrowser.oauth.default-role` | `VIEWER` | Fallback when no matching claim is found |

Nested claims are supported (e.g., `realm_access.roles` for Keycloak).

### Roles and View Access

When authentication is enabled (`local` or `oauth`), views are
protected by role:

| View | Required Role |
|------|---------------|
| Server | ADMIN |
| Settings | ADMIN |
| Create | ADMIN |
| Access | ADMIN |
| Bulk | ADMIN |
| Browse | ADMIN or VIEWER |
| Search | ADMIN or VIEWER |
| Schema | ADMIN or VIEWER |
| Export | ADMIN or VIEWER |
| Help | Everyone |

In `none` mode all role annotations are ignored and every view is
accessible.

### Network Binding

By default the server binds to `127.0.0.1` (localhost only). To allow
remote access, override the bind address:

```bash
java -jar target/ldap-browser-0.65.jar --server.address=0.0.0.0
```

For hosted deployments, place the application behind a reverse proxy
(nginx, Caddy, etc.) that handles TLS termination.

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
- SSL/TLS with custom truststore support
- StartTLS post-connect processing
- `@PreDestroy` cleanup closes all pools on shutdown

### Security
- Three authentication modes: none, local (BCrypt), OAuth2/OIDC
- Role-based view authorization (ADMIN / VIEWER)
- AES-GCM password encryption with `ENC:` prefix
- PKCS#12 keystore for encryption key storage
- POSIX file permissions on sensitive files
- Localhost-only binding by default

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
│   │   ├── LdapBrowserApplication.java      # Spring Boot entry point
│   │   ├── config/                          # Security & auth config
│   │   │   ├── SecurityConfiguration.java   # Multi-mode security filter chain
│   │   │   └── OidcRoleMappingConfig.java   # OIDC claim → role mapping
│   │   ├── model/                           # Data models
│   │   │   ├── LdapServerConfig.java        # Server configuration
│   │   │   ├── LdapEntry.java               # LDAP entry representation
│   │   │   ├── SearchFilter.java            # Search filter model
│   │   │   ├── BrowseResult.java            # Browse results
│   │   │   └── SchemaElement.java           # Schema element wrapper
│   │   ├── service/                         # Business logic
│   │   │   ├── LdapService.java             # Core LDAP operations
│   │   │   ├── ConfigurationService.java    # Server config persistence
│   │   │   ├── EncryptionService.java       # AES-GCM password encryption
│   │   │   ├── KeystoreService.java         # PKCS#12 key management
│   │   │   ├── TruststoreService.java       # Certificate trust store
│   │   │   ├── UserService.java             # Local user store (BCrypt)
│   │   │   └── LoggingService.java          # Structured LDAP logging
│   │   ├── ui/
│   │   │   ├── MainLayout.java              # AppLayout with user menu
│   │   │   ├── views/                       # Page views (role-annotated)
│   │   │   │   ├── LoginView.java           # Login page (local/oauth)
│   │   │   │   ├── ServerView.java          # Server management
│   │   │   │   ├── SearchView.java          # Search interface
│   │   │   │   ├── BrowseView.java          # Tree browser
│   │   │   │   ├── SchemaView.java          # Schema management
│   │   │   │   ├── CreateView.java          # Entry creation
│   │   │   │   ├── AccessView.java          # Access control
│   │   │   │   ├── BulkView.java            # Bulk operations
│   │   │   │   ├── ExportView.java          # Export functionality
│   │   │   │   ├── SettingsView.java        # App settings & users
│   │   │   │   └── HelpView.java            # Help documentation
│   │   │   └── components/                  # Reusable UI components
│   │   └── util/                            # Utilities
│   │       ├── AciParser.java               # ACI string parser
│   │       ├── LdifGenerator.java           # LDIF export utility
│   │       └── OidLookupTable.java          # OID to name mapping
│   └── resources/
│       ├── application.properties           # Main configuration
│       ├── application-development.properties # Dev profile
│       ├── application-local-auth.properties  # Local auth profile
│       └── application-oauth.properties       # OAuth/OIDC profile
└── test/
    └── java/                                # 278 unit tests (15 files)
```

## Navigation

The application includes the following pages:

- **Server** ✅ - Grid-based server configuration management with CRUD operations (ADMIN)
- **Search** ✅ - Advanced LDAP search with filter builder and result editing (ADMIN, VIEWER)
- **Browse** ✅ - Interactive tree-based directory navigation with inline editing (ADMIN, VIEWER)
- **Schema** ✅ - Schema browsing, comparison, and management across servers (ADMIN, VIEWER)
- **Create** ✅ - Create new LDAP entries with templates and validation (ADMIN)
- **Access** ✅ - ACI management (global, entry-level, effective rights) (ADMIN)
- **Bulk** ✅ - Bulk operations (import, search, generate, group memberships) (ADMIN)
- **Export** ✅ - Export data in LDIF, CSV, and JSON formats (ADMIN, VIEWER)
- **Settings** ✅ - Application settings and user management (ADMIN)
- **Help** ✅ - Online help documentation (Everyone)

## Key Features Summary

### Technical Highlights
- **59 Java source files** plus **15 test files** (278 tests)
- **Three authentication modes** — none, local, OAuth2/OIDC
- **Role-based access control** — ADMIN and VIEWER roles
- **AES-GCM password encryption** with PKCS#12 keystore
- **Connection pooling** with 1-10 connections per server
- **Automatic retry** on stale or timed-out connections
- **Schema caching** for improved performance
- **Multi-server operations** with parallel execution and result aggregation
- **UI-scoped state** allowing independent browser tabs
- **Schema-aware editing** with color-coded attributes
- **Tree navigation** with lazy loading and real-time updates
- **Bulk operations** supporting import, export, search, and group management
- **ACI management** with visual builder and effective rights checking
- **Google Java Style** enforced via Checkstyle (0 violations)

### Integration Points
- UnboundID LDAP SDK 7.0.3 for all LDAP protocol operations
- Vaadin 25.0.3 for reactive web UI
- Spring Boot 4.0.0 / Spring Security 7.0 for DI and security
- JSON-based configuration and user persistence
- Structured logging for all LDAP operations

## Build Validation

```bash
mvn compile            # Verify compilation
mvn test               # Run 278 unit tests
mvn checkstyle:check   # Check style (0 violations)
mvn clean package -Pproduction -DskipTests  # Production JAR
```

## Contributing

This project follows Google Java Style conventions. Please ensure your code passes Checkstyle validation before committing:

```bash
mvn checkstyle:check
```

The project maintains **0 Checkstyle violations** — please keep it that way!

## License

[Add license information here]

## Documentation

See the `docs/` directory for detailed documentation:
- [Changelog](docs/changelog.md) - Complete version history
- [Requirements](docs/requirements.md) - Original feature requirements
- [Help](docs/help.md) - User-facing help documentation

---

**Current Status:** Version 0.65 — Full-featured LDAP browser with multi-mode authentication, role-based access control, encrypted credential storage, and comprehensive security hardening.
