/**
 * A headless, single-threaded stand-in for Jasper's plugin runtime, for plugin unit tests. The thread
 * that calls {@link dev.jasper.sdk.testing.FakePluginHost} plays the UI thread: events wait in a queue
 * until {@code flush()}, and background tasks wait until {@code runBackground()}.
 */
package dev.jasper.sdk.testing;
