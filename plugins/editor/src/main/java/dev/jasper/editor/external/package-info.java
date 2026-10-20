/**
 * Editors besides Jasper's own: the editors Jasper knows, finding them on this machine, the {@code EDITOR} values and
 * command lines that run them, and the default editor Jasper opens files with and names in its shells'
 * {@code EDITOR}. Detection runs on the background executor, except {@code findSaved}'s bounded search for the saved
 * editor, which runs on the UI thread at start; the default editor's {@code edit}, {@code variables} and listeners run
 * on the UI thread. May depend on: the JDK and the SDK.
 */
package dev.jasper.editor.external;
