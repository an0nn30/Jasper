/**
 * Explicit opt-in native benchmark orchestration and reports. Benchmarks own and close fixtures; never part of headless check.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.application, dev.jasper.app.config, dev.jasper.app.launch, dev.jasper.app.workspace, dev.jasper.terminal.config, dev.jasper.terminal.search, dev.jasper.terminal.session, dev.jasper.terminal.view.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.benchmark;
