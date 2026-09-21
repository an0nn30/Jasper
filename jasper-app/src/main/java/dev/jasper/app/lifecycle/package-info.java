/**
 * Owner-thread-confined once-only cancellation. The subscriber closes the handle; no background worker or global state.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.lifecycle;
