/**
 * Project support for other plugins: fixed POSIX {@code sh} scripts run on exec channels (marker detection,
 * {@code git ls-files}, {@code find}), an SFTP walk fallback, and {@code ProjectsService},
 * which publishes {@code dev.jasper.remote.api.RemoteProjects}. Scripts arrive on stdin of {@code sh -s}, so the user's
 * own shell parses only that command; every path and pattern in a script is single-quoted. Never writes into a
 * user's shell. May depend on: {@code client}, {@code hosts}, {@code sftp}, {@code chooser} (for its source id),
 * {@code api} and the SDK.
 */
package dev.jasper.remote.projects;
