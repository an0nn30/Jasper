/**
 * Theme parsing and serialization, with source-located diagnostics.
 * Allowed outgoing Jasper dependency: dev.jasper.ui.theme; TOML is an implementation dependency.
 * Parsing is synchronous; callers own file access and the resulting immutable documents.
 */
package dev.jasper.ui.themeio;
