/**
 * Editing remote files in the Editor: the write contract over a file endpoint, one queued SFTP channel per
 * connection identity, the documents the Editor opens, and Remote's resolver for files picked in Jasper's chooser. May depend on:
 * {@code sftp}, {@code client}, the SDK and {@code dev.jasper.editor.api}. Only
 * {@link dev.jasper.remote.edit.RemoteDocument}, {@code SavedRemoteDocument} and
 * {@link dev.jasper.remote.edit.RemoteEditing} name Editor types; they
 * load only when the Editor is installed.
 */
package dev.jasper.remote.edit;
