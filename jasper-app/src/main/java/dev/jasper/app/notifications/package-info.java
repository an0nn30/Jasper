/**
 * EDT notification center (groups, the in-memory history, read and expired state, delivery routing) and its
 * values, the incident store with its captures and clipboard reports, plus terminal and activity notice production
 * for Buddy, active producer identities, attention and visibility policy. The application owns the center and the
 * incidents and closes notifiers before the companion.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.lifecycle, dev.jasper.buddy.notice, dev.jasper.buddy.view.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.notifications;
