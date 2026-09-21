/**
 * Restarting Jasper to apply a plugin change: planning the replacement's command line from this process's own,
 * a seam over the resident process, and the "Restart normally" conversation that never starts a
 * handoff-capable replacement while a resident still holds the endpoint. No owner: values and one
 * state machine whose worker threads are daemons.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.restart;
