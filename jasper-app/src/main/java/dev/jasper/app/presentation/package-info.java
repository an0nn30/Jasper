/**
 * The presentation a process runs with, chosen once before Swing (spec 4 and 9): {@link dev.jasper.app.presentation.KitParser}
 * reads a provider's kit file into the jasper-ui kit and a {@link dev.jasper.app.presentation.ChromeSpec},
 * {@code PresentationFacts} gives each provider's digest and first problem,
 * {@code AttemptStore} keeps the attempt record, and
 * {@code Presentation} hands each consumer its projection. Pure values, bounded reads and
 * locked read-modify-writes; no Swing. ChromeSpec is referenced only in this package.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.config, dev.jasper.app.settings, dev.jasper.app.persistence,
 * dev.jasper.app.dock, dev.jasper.ui.kit, dev.jasper.ui.metrics, dev.jasper.ui.theme.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.presentation;
