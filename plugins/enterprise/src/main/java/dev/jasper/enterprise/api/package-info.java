/**
 * The contract other plugins compile against: {@link dev.jasper.enterprise.api.Workstation}, a presence-only service
 * published while the Enterprise look runs (the equivalent of IntelliJ's optional dependency on its Classic UI plugin).
 * A consumer declares {@code { id = "dev.jasper.enterprise", version = ">=0.1", optional = true }}, finds the service and
 * composes its classic layout; without it, its default one. Exported by the plugin descriptor; nothing else is.
 */
package dev.jasper.enterprise.api;
