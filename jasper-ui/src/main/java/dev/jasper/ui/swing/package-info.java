/**
 * Swing kit rendering, appearance binding and FlatLaf installation.
 * Allowed outgoing Jasper dependencies: dev.jasper.ui.model, dev.jasper.ui.theme, dev.jasper.ui.metrics dev.jasper.ui.icons and the data-only dev.jasper.ui.themeimport.FlatLafThemeData adapter.
 * Views and bindings are EDT-confined; their owners close subscriptions and registered content lifetimes.
 * Views own wipeable secret documents and reveal state. Secret callback arrays are temporary and erased;
 * asynchronous consumers must clone/transfer needed values to an owner that erases its own copy.
 * Switches display descriptor confirmation, while user input reports node-addressed intent.
 */
package dev.jasper.ui.swing;
