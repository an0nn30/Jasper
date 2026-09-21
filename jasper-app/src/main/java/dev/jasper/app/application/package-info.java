/**
 * EDT application composition and feature lifetimes; launch coordinator synchronizes admission and shutdown waits off EDT. JasperApplication closes children.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.config, dev.jasper.app.contributions, dev.jasper.app.history, dev.jasper.app.launch, dev.jasper.app.lifecycle, dev.jasper.app.notifications, dev.jasper.app.persistence, dev.jasper.app.platform, dev.jasper.app.pluginmanager, dev.jasper.app.plugins, dev.jasper.app.restart, dev.jasper.app.snippets, dev.jasper.app.windows, dev.jasper.app.workspace, dev.jasper.buddy.config, dev.jasper.buddy.view, dev.jasper.terminal.config, dev.jasper.terminal.rendering, dev.jasper.terminal.session.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.application;
