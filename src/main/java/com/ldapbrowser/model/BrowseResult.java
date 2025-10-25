package com.ldapbrowser.model;

import java.util.List;

/**
 * Result of browsing LDAP entries with pagination support.
 */
public class BrowseResult {
  
  private final List<LdapEntry> entries;
  private final boolean hasMore;
  private final int totalReturned;
  private final int currentPage;
  private final int pageSize;
  private final boolean hasNextPage;
  private final boolean hasPrevPage;
  
  /**
   * Creates a browse result (legacy constructor for backward compatibility).
   *
   * @param entries the entries returned
   * @param hasMore whether more entries are available
   * @param totalReturned total number of entries returned
   */
  public BrowseResult(List<LdapEntry> entries, boolean hasMore, int totalReturned) {
    this(entries, hasMore, totalReturned, 0, entries.size(), false, false);
  }
  
  /**
   * Creates a browse result with full pagination information.
   *
   * @param entries the entries returned
   * @param hasMore whether more entries are available (legacy)
   * @param totalReturned total number of entries returned
   * @param currentPage the current page number (0-indexed)
   * @param pageSize the page size
   * @param hasNextPage whether there is a next page
   * @param hasPrevPage whether there is a previous page
   */
  public BrowseResult(List<LdapEntry> entries, boolean hasMore, int totalReturned,
                      int currentPage, int pageSize, boolean hasNextPage, boolean hasPrevPage) {
    this.entries = entries;
    this.hasMore = hasMore;
    this.totalReturned = totalReturned;
    this.currentPage = currentPage;
    this.pageSize = pageSize;
    this.hasNextPage = hasNextPage;
    this.hasPrevPage = hasPrevPage;
  }
  
  /**
   * Gets the entries.
   *
   * @return list of entries
   */
  public List<LdapEntry> getEntries() {
    return entries;
  }
  
  /**
   * Checks if more entries are available.
   *
   * @return true if more entries exist
   */
  public boolean hasMore() {
    return hasMore;
  }
  
  /**
   * Gets the total number of entries returned.
   *
   * @return total entries
   */
  public int getTotalReturned() {
    return totalReturned;
  }
  
  /**
   * Gets the current page number (0-indexed).
   *
   * @return current page
   */
  public int getCurrentPage() {
    return currentPage;
  }
  
  /**
   * Gets the page size.
   *
   * @return page size
   */
  public int getPageSize() {
    return pageSize;
  }
  
  /**
   * Checks if there is a next page available.
   *
   * @return true if next page exists
   */
  public boolean hasNextPage() {
    return hasNextPage;
  }
  
  /**
   * Checks if there is a previous page available.
   *
   * @return true if previous page exists
   */
  public boolean hasPrevPage() {
    return hasPrevPage;
  }
}
