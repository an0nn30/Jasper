/**
 * Pre-AWT argument/handoff flow and EDT composition. StartupResources owns rollback until explicit transfer.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.application, dev.jasper.app.config, dev.jasper.app.history, dev.jasper.app.launch, dev.jasper.app.platform, dev.jasper.app.residency, dev.jasper.app.restart, dev.jasper.app.snippets, dev.jasper.app.workspace.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.bootstrap;
