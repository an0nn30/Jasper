package dev.jasper.editor.ui;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.TransferHandler;

/** Opens regular files dropped from the desktop and ignores folders; everything else goes to the component's own handler. */
public final class FileDrop extends TransferHandler {
    private final TransferHandler delegate;
    private final Consumer<List<Path>> open;

    /**
     * @param delegate the component's own handler (text drag and drop, copy and paste), or null for none
     * @param open receives the dropped regular files
     */
    public FileDrop(TransferHandler delegate, Consumer<List<Path>> open) {
        this.delegate = delegate;
        this.open = Objects.requireNonNull(open);
    }

    @Override public boolean canImport(TransferSupport support) {
        if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return true;
        return delegate != null && delegate.canImport(support);
    }

    @Override public boolean importData(TransferSupport support) {
        if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return delegate != null && delegate.importData(support);
        try {
            List<?> dropped = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            List<Path> files = dropped.stream().filter(File.class::isInstance).map(file -> ((File) file).toPath())
                .filter(Files::isRegularFile).toList();
            if (files.isEmpty()) return false;
            open.accept(files);
            return true;
        } catch (UnsupportedFlavorException | IOException unreadable) {
            return false;
        }
    }

    @Override public int getSourceActions(JComponent component) { return delegate == null ? NONE : delegate.getSourceActions(component); }

    @Override public void exportAsDrag(JComponent component, InputEvent event, int action) {
        if (delegate != null) delegate.exportAsDrag(component, event, action);
    }

    @Override public void exportToClipboard(JComponent component, Clipboard clipboard, int action) {
        if (delegate != null) delegate.exportToClipboard(component, clipboard, action);
    }

    @Override public Icon getVisualRepresentation(Transferable transferable) {
        return delegate == null ? null : delegate.getVisualRepresentation(transferable);
    }
}
