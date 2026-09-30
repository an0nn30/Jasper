/**
 * Places plugins add to Jasper's file chooser, and what the chooser returns. A plugin registers a {@link
 * dev.jasper.sdk.files.FileSource}, such as its saved hosts, with {@link dev.jasper.sdk.files.FileSources}; the chooser
 * lists the source's roots beside the local places, connects when the user opens one, and browses it through a
 * {@link dev.jasper.sdk.files.FileBrowser} on a host worker. {@code Windows.choose} returns
 * {@link dev.jasper.sdk.files.FileLocation}s, local or from a source. Depends on {@code dev.jasper.sdk} and
 * {@code dev.jasper.sdk.terminal}.
 */
package dev.jasper.sdk.files;
