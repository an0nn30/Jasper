/**
 * Swing kit rendering, appearance binding and FlatLaf installation.
 * Allowed outgoing Jasper dependencies: dev.jasper.ui.model, dev.jasper.ui.theme, dev.jasper.ui.metrics and dev.jasper.ui.icons.
 * Views and bindings are EDT-confined; their owners close subscriptions and registered content lifetimes.
 * Views own wipeable secret documents and reveal state. Secret callback arrays are temporary and erased;
 * asynchronous consumers must clone/transfer needed values to an owner that erases its own copy.
 * Switches display descriptor confirmation, while user input reports node-addressed intent.
 * Containers own semantic layout; forms render content only and consume app-supplied logical available
 * height. The application owns native chrome, window/scrim lifetime and cancellation cleanup. Popup
 * registrations are disposed on dismissal/close; form keyboard cancellation is an explicit action key.
 */
package dev.jasper.ui.swing;
