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
   - Server link in Drawer
   - Purpose is to manage LDAP server connection details
   - Server dropdown in Navbar is updated when new servers are saved.
   - Configuration of LDAP servers connection details
   - Saved into a local file connections.json
   - Test, save, and copy actions

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
    - TBD

1. **Access**
    - TBD

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