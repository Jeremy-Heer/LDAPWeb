# LDAP Browser - Requirements Document

This document outlines the functional and technical requirements for building the LDAP Browser application.

## Application Overview

LDAP Browser is a comprehensive Java web application for browsing, searching, and managing LDAP directorys.

## Key Features

1. **Stack**
   - Vaadin 24.3.0
   - Spring Boot 3.2.0
   - UnboundID LDAP SDK 7.0.3
   - Java 17+

1. **Layout**
    - Navbar
        - Vaadin Navbar displayed at the top of each window
        - A multi select server Combo Box drop down allowing the user to select multiple LDAP servers.
        - Selected LDAP servers are then displayed in the Navbar in a horizontal row as badges.
        - Selected LDAP servers are then used when performing LDAP request

    - Drawer
        - Vaadin Drawer displayed at the left of each window.
        - Toggles to display or hide
        - Links: Server, Search, Browse, Schema, Access, Bulk, Import/Export

1. **Server**
   - Purpose is to manage LDAP server connection details
   - Servers tab selected from drawer
   - Displays buttons - Add Server, Edit, Copy, Delete, Test
   - List of existing servers displayed in grid under buttons
   - Grid columns
        - Name
        - Host:Port
        - Bind DN
        - Security (indicates if tls/ssl is used)
   - Selected server can then be edited, copied, deleted, or connection tested.
   - Add Server displays dialog fields to add
        - Name
        - host
        - port
        - Bind DN
        - Password
        - Use SSL Checkbox
        - Use StartTLS checkbox
        - Base DN
   - when existing server is selected, same dialog in edit mode
   - Server dropdown in Navbar is updated with Server updates
   - Saved into a local file connections.json

1. **Search**
   - Search link in Drawer
   - Purpose is to perform ldap searches and display their results
   - A browse button to launch the "Browse Selector" shared component to select a DN for the search base field
   - Search Base can also be manually updated
   - Filter field for manual ldap search entry
   - Search Button to perform the search
   - When the search is requested from the LDAP backend, its performed on each selected LDAP server.
   - Filter Builder acordion
       - when selected will expand and dislay a search builder
       - allows building complex ldap searches graphacilly
       - resulting filter is populated into the Filter field
   - Search results are displayed at the bottom
   - Results can be selected to display shared Entry Editor pane to the right


1. **Browse**
    - Browse link in Drawer
    - The "Tree Browser " is displayed on the left using selected ldap servers for its data.
    - Selected Entries from the Tree Browser  are displayed in the shared Entry Editor to the right.
    - 

1. **Schema**
    - Full CRUD Schema management
    - 2 levels of tabs
    - top level tabs "Manage" and "Compare"
    - Manage tab - full schema browse and edit. sub tabs for object class, attributes, matching rules, matching rule use, and syntax.
        - A grid displayes items for each of the above catagories with a search and refresh button.
        - Selected items are displayed in a Schema Details side pane
        - Schema Details side pane allows editing the item.
    - Compare tab - Compares the schema for the selected servers
        - a search field to find items in the grid
        - a checkbox to ignore extensions. when selected this will ignore vendor specific extensiosn suchs as X-SCHEMA-FILE
        - a refresh button
        - sub tabs Object Class, Attribute Types, Matching Rules, Matching Rule Use, Syntaxes
        - when a subtab is selectected, those items are listed in a grid. For example when object class is selected, the object classes from each of 
            the selected servers is listed in the grid
            - Columns Name followed by a column for each server name, ending with a Status column
            - The name column would contain the name of the itme (name of the object class for example)
            - Each server name column would contain an 8 character hash of the schema extension for the server
            - Status column has Equal or Unequal values indicating if the hash for each server is equal or not
            - Each grid item is selectable to display the details for the selected item under the grid
                - for example when a object class row is selected, each property for the object class is displayed for each of the servers

1. **Access**
    - 3 sub tabs
        - Global Access Control
            - Reads the Ping Directory "cn=Access Control Handler,cn=config" / "ds-cfg-global-aci" for each selected ldap server
            - a grid display each server name, aci Name value with a search field to filter the grid display
            - a preview pane to the right to display details for selected aci entries
        - Entry Accesss Control
            - performs a subtree search wtih base of server default base for each selected ldap server "(aci=*)" returning each aci value
            - results are displayed in a grid with Name, Entry DN, and Resources columns
            - A search to filter grid
            - A preview pane that displays aci details to the right
                - aci details with edit and delete icons
                - edit icon opens aci editor pop up
                    - contains targetn entry with dn selector helper popup
                    - ACI value with Build ACI icon that launches ACI Builder
                        - aci builder a a UI to select each aci element
        - Effective Rights
            - Check effective access rights for entries using "Get Effective Rights Request Control"
            - Search base with DN selector popup
            - search scoope
            - search filter
            - attributes
            - Effective Rights for field with a DN selector popup
            - search size limit with defaut value of 100
            - search button to perform the search for each selected ldap server
            - search results displayed in a grid below form
                - columns Server name, Entry DN, Attribute Rights, Entry Rights

1. **Bulk**
    - TBD

1. **Import/Export**
    - TBD

1. **Shared Components**

    - "Entry Editor"
        - Displayed when an LDAP entry is selected either by search or browse
        - DN displayed in Title with copy options
        - Add attribute, save changes, test login, refresh ,delete entry buttons
        - Show operational attributes checkbo
        - attributes displayed in a grid color coded.
        - each attribute/value row has actions icons including edit, copy, delete

    - "Tree Browser"
        - A hierachical LDAP Tree-based DN selector used throughout application.
        - Expandable/collapsible tree nodes with lazy loading
        - Real-time directory tree updates
        - Displays a list of selected ldap servers and a tree selector under each.to choose and populate DN fields
        - Top level of the Tree is the selected ldap server names.
        - ldap server names are expanded to display the "Root DSE"
        - "Root DSE" is expanded to show the naming contexts obtained from the "namingContets" attribute value of the "Root DSE"
        - Each naming context is expanded to drill down into the LDAP tree structure.
        - A maximum of 100 entries are displayed. When more than 100 entries are in the LDAP container, a pageing feature is used to page forward and backwards.
```
first.example.ldap/
|--ROOT DSE
   |----dc=example,dc=com/
      |----ou=people
      |----ou=groups

second.example.ldap/
|---ROOT DSE
    |----dc=company,dc=net
      |----ou=admins
      |----ou=developers
```

## Core Services

### LdapService
Primary service for LDAP operations:
- Connection management and pooling
- Authentication and bind operations
- Search operations with pagination support
- Entry CRUD operations (Create, Read, Update, Delete)
- Schema management operations
- SSL/TLS and StartTLS support