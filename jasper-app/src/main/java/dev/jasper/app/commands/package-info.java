/**
 * EDT action registry, pure command ranking/metadata and the palette's command recents. Registry owns listeners
 * until registration or registry close; the application owns the recents' flush at shutdown.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.lifecycle, dev.jasper.app.persistence.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.commands;
