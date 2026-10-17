/**
 * Process-wide start-up timing: first-write-wins milestones anchored to the operating system's process start, phase
 * durations, per-window traces, one log line once start-up settles and an optional JSON file for the start-up
 * benchmark, and the steps a branded look's splash shows. Every type except StartupSteps (EDT only) is safe from any
 * thread; nothing here blocks the EDT.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.startup;
