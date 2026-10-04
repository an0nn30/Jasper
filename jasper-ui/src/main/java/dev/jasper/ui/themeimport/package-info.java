/**
 * Bounded, offline native/IntelliJ/Alacritty theme conversion and catalog-source validation.
 * Allowed outgoing Jasper dependencies: dev.jasper.ui.theme and dev.jasper.ui.themeio.
 * Uses parser libraries and pinned FlatLaf color data/platform helpers, never Swing components.
 * FlatLafThemeData is the public data-only runtime adapter reused by the Swing installer.
 * Owns bounded read-only source/archive access; callers own discovery and filesystem publication,
 * locking and migration. Bundled parent data retains source provenance and licenses.
 */
package dev.jasper.ui.themeimport;
