package dev.jasper.editor.ui;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Optional;

/** Vim's {@code "+} (the clipboard) and {@code "*} (the selection where there is one, else the clipboard). */
final class SystemClipboard {
    private SystemClipboard() { }

    /**
     * @param register {@code '+'} or {@code '*'}
     * @return its text, or empty when it holds none or cannot be read (another application has it, or headless)
     */
    static Optional<String> read(char register) {
        try {
            Clipboard clipboard = clipboard(register);
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return Optional.empty();
            return Optional.of((String) clipboard.getData(DataFlavor.stringFlavor));
        } catch (HeadlessException | IllegalStateException | UnsupportedFlavorException | IOException unavailable) {
            return Optional.empty();
        }
    }

    /**
     * @param register {@code '+'} or {@code '*'}
     * @param text written to it; dropped when it cannot be written
     */
    static void write(char register, String text) {
        try { clipboard(register).setContents(new StringSelection(text), null); }
        catch (HeadlessException | IllegalStateException unavailable) { }
    }

    private static Clipboard clipboard(char register) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Clipboard selection = register == '*' ? toolkit.getSystemSelection() : null;
        return selection != null ? selection : toolkit.getSystemClipboard();
    }
}
