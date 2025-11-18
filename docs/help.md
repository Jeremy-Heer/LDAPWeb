# LDAP Browser Help

Welcome to LDAP Browser! This application helps you manage, browse, and search LDAP directories. Select one or more servers from the navbar dropdown to get started.

## Server

The Server view manages LDAP server connection configurations.

### Features:
- Add new LDAP server connections with host, port, and credentials
- Edit existing server configurations
- Copy server configurations for quick setup
- Delete server configurations
- Test connections to verify server accessibility
- SSL/TLS support with certificate validation
- Connection pooling for improved performance

### How to Use:
- Click 'Add Server' to create a new server configuration
- Fill in the server details (name, host, port, bind DN, password)
- Enable SSL/TLS if your server requires secure connections
- Click 'Test Connection' to verify the configuration
- Save the configuration to make it available in the server selector

## Search

The Search view allows you to query LDAP directories using flexible search criteria.

### Features:
- Quick search with base DN and filter
- Advanced search builder for complex queries
- Search scope selection (base, one level, subtree)
- Multi-server search across selected servers
- View and edit search results
- Export search results
- Delete entries directly from results

### How to Use:
- Select one or more servers from the navbar
- Enter a base DN (starting point for the search)
- Enter an LDAP filter (e.g., '(objectClass=person)' or '(uid=jdoe)')
- Select the search scope
- Click 'Search' to execute the query
- Click on a result entry to view or edit its attributes

## Browse

The Browse view provides a tree-based navigation interface for exploring LDAP directories.

### Features:
- Tree view of LDAP directory structure
- Expand and collapse nodes to navigate
- View entry attributes in detail panel
- Edit entry attributes
- Delete entries with confirmation
- Multi-server browsing with server identification
- Restore previously browsed locations via URL parameters

### How to Use:
- Select one or more servers from the navbar
- Click on nodes in the tree to expand them
- Select an entry to view its attributes on the right panel
- Click 'Edit' to modify attributes
- Click 'Delete' to remove an entry (requires confirmation)

## Schema

The Schema view displays LDAP schema information including object classes, attribute types, matching rules, and syntaxes.

### Features:
- View object classes and their attributes
- Explore attribute types and their properties
- Browse matching rules for attribute comparison
- View supported LDAP syntaxes
- Filter schema elements by name or properties
- Multi-server schema inspection with server column
- Detailed information panel for selected elements

### How to Use:
- Select one or more servers from the navbar
- The schema is automatically loaded when you access the view
- Switch between tabs to view different schema element types
- Use the filter field to search for specific schema elements
- Click on a schema element to view its detailed properties

## Create

The Create view enables you to create new LDAP entries with custom attributes.

### Features:
- Create new LDAP entries interactively
- Specify parent DN and RDN for new entries
- Browse directory to select parent DN
- Add multiple attributes with values
- Support for multi-valued attributes
- Validation of DN structure
- Clear form for quick reset

### How to Use:
- Select a server from the navbar
- Enter the RDN (e.g., 'cn=John Doe')
- Enter or browse to select the parent DN
- Click 'Add Row' to add attribute-value pairs
- Fill in attribute names and values
- Click 'Create Entry' to save the new entry
- Use 'Clear' to reset the form

## Access

The Access view manages LDAP access control lists (ACLs) and permissions.

### Features:
- View global access control policies
- Manage entry-specific access controls
- Check effective rights for users
- Understand permission inheritance
- Multi-tabbed interface for different access control aspects

### How to Use:
- Select one or more servers from the navbar
- Switch between tabs to view different access control types
- 'Global Access Control': View directory-wide ACL policies
- 'Entry Access Control': Manage ACLs for specific entries
- 'Effective Rights': Check what permissions a user has on entries

## Bulk

The Bulk view allows you to perform operations on multiple LDAP entries at once.

### Features:
- Bulk modifications across multiple entries
- Batch attribute updates
- Mass deletion with safeguards
- Apply changes to search result sets
- Progress tracking for bulk operations
- Error reporting for failed operations

### How to Use:
- Select a server from the navbar
- Define the scope of entries to modify (via search or DN list)
- Specify the operation type (add, modify, delete attributes)
- Define the attribute changes to apply
- Review and confirm the bulk operation
- Monitor progress and review results

## Export

The Export view enables you to export LDAP data in various formats.

### Features:
- Export entries to LDIF format
- Export to CSV for spreadsheet applications
- Export to JSON for data processing
- Select specific attributes to export
- Filter entries with search criteria
- Download exported files directly
- Preview export before downloading

### How to Use:
- Select a server from the navbar
- Specify the base DN for export
- Choose the export format (LDIF, CSV, JSON)
- Optionally select specific attributes to include
- Apply search filters if needed
- Click 'Export' to generate the file
- Download the exported file

## Settings

The Settings view provides application configuration and security management options.

### Features:
- Manage application preferences
- Configure truststore for SSL/TLS connections
- Import and manage SSL certificates
- View certificate details
- Delete certificates from truststore
- Future: Keystore management for client certificates
- Future: Encryption settings for sensitive data

### How to Use:
- Navigate to the Settings view from the drawer
- 'General': Configure application-wide preferences
- 'Truststore': Manage trusted server certificates for SSL/TLS
- Upload .crt or .pem certificate files to trust new servers
- View certificate details by selecting entries in the grid
- Delete certificates that are no longer needed
