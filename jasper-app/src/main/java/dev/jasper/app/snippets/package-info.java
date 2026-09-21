/**
 * Immutable snippet values, bounded persistence and EDT store over workers. Application closes the store; caller closes subscriptions.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.commands, dev.jasper.app.lifecycle, dev.jasper.app.persistence.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.snippets;
