/**
 * The Vim engine (spec docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md): keys, modes, motions, text
 * objects, operators, Visual mode, search, marks, registers, undo steps and {@code .}, on an RSyntaxTextArea, with no
 * Swing UI of its own beyond the highlights it draws on the text. UI thread only. May depend on:
 * {@code dev.jasper.editor.vim.regex}, RSyntaxTextArea, the JDK.
 */
package dev.jasper.editor.vim;
