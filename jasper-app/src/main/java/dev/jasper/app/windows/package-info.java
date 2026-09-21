/**
 * Application-built auxiliary windows and dialogs with consistent chrome. {@code AuxiliarySurface} is
 * the headless core; {@code NativeShells} is the only class here that creates frames and dialogs.
 * The application owns {@code AuxiliaryWindows} and closes it at shutdown; creators close their own surfaces.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.lifecycle, dev.jasper.app.persistence, dev.jasper.app.platform.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.windows;
