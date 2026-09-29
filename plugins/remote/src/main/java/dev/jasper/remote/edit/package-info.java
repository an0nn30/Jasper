/**
 * Editing remote files in the Editor: the write contract over a file endpoint, one queued SFTP channel per
 * connection identity, the documents the Editor opens, and Remote's answer to Open in Editor…. May depend on:
 * {@code sftp}, {@code client}, the SDK and {@code dev.jasper.editor.api}. Only
 * {@link dev.jasper.remote.edit.RemoteDocument}, {@link dev.jasper.remote.edit.RemoteEditing} and
 * {@link dev.jasper.remote.edit.RemoteOpening} name Editor types; they load only when the Editor is installed.
 */
package dev.jasper.remote.edit;
