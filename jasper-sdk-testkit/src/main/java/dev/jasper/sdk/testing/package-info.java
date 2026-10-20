/**
 * A headless, single-threaded stand-in for Jasper's plugin runtime, for plugin unit tests. The thread
 * that calls {@link dev.jasper.sdk.testing.FakePluginHost} plays the UI thread: events wait in a queue
 * until {@code flush()}, and background tasks wait until {@code runBackground()}. Kit views are the exception:
 * {@link dev.jasper.sdk.testing.FakeKit} and {@link dev.jasper.sdk.testing.FakeKitView} need the real Swing event
 * dispatch thread, so run a test of a plugin that builds views inside {@code SwingUtilities.invokeAndWait}.
 */
package dev.jasper.sdk.testing;
