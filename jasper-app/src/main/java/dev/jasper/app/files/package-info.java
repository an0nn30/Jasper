/**
 * The application's file chooser: blocking local file access, a headless model and its Swing panel. Whoever shows
 * the panel owns its dialog and worker; the model runs I/O on that worker and applies results on the UI thread.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.persistence, dev.jasper.app.platform.
 * Controls are jasper-ui kit descriptors (dev.jasper.ui.model, dev.jasper.ui.swing) mounted in a layout-only shell with
 * the chrome KitContext its host passes in.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.files;
