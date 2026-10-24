/**
 * Pre-AWT arguments, the home's instance lock and the handoff, and EDT composition. StartupResources owns rollback until explicit transfer; the owner's Instance keeps its lock for the life of the process.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.application, dev.jasper.app.commandline, dev.jasper.app.config, dev.jasper.app.history, dev.jasper.app.launch, dev.jasper.app.platform, dev.jasper.app.plugins, dev.jasper.app.presentation, dev.jasper.app.residency, dev.jasper.app.restart, dev.jasper.app.snippets, dev.jasper.app.splash, dev.jasper.app.startup, dev.jasper.app.workspace.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.bootstrap;
