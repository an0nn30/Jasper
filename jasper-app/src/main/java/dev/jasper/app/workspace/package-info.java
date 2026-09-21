/**
 * EDT windows, tabs, splits, panes, action/config adapters and activity events. WindowContent closes subscriptions/panes; each pane closes its session.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.commands, dev.jasper.app.config, dev.jasper.app.contributions, dev.jasper.app.history, dev.jasper.app.launch, dev.jasper.app.lifecycle, dev.jasper.app.palette, dev.jasper.app.palette.builtin, dev.jasper.app.platform, dev.jasper.app.snippets, dev.jasper.terminal.config, dev.jasper.terminal.rendering, dev.jasper.terminal.search, dev.jasper.terminal.session, dev.jasper.terminal.view.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.workspace;
