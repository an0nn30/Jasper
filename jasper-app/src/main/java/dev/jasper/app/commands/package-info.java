/**
 * EDT action registry and pure command ranking/metadata. Registry owns listeners until registration or registry close.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.lifecycle.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.commands;
