/**
 * Pure parsing/snapshots plus EDT indexes backed by workers. Application owns indexes and command-history flush at shutdown.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.commands, dev.jasper.app.lifecycle, dev.jasper.app.persistence.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.history;
