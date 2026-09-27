package dev.jasper.editor.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTextArea;
import javax.swing.TransferHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class FileDropTest {
    @TempDir Path folder;

    private static Transferable files(File... files) {
        return new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.javaFileListFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.javaFileListFlavor.equals(flavor); }
            @Override public Object getTransferData(DataFlavor flavor) { return List.of(files); }
        };
    }

    @Test void droppedFoldersAreIgnoredFilesOpenAndTextGoesToTheDelegate() throws Exception {
        Path file = Files.writeString(folder.resolve("a.txt"), "a");
        Path directory = Files.createDirectory(folder.resolve("d"));
        List<List<Path>> opened = new ArrayList<>();
        var target = new JTextArea();
        var drop = new FileDrop(target.getTransferHandler(), opened::add);
        assertThat(drop.importData(new TransferHandler.TransferSupport(target, files(file.toFile(), directory.toFile())))).isTrue();
        assertThat(opened).containsExactly(List.of(file));
        assertThat(drop.importData(new TransferHandler.TransferSupport(target, files(directory.toFile())))).isFalse();
        assertThat(opened).hasSize(1);
        assertThat(drop.importData(new TransferHandler.TransferSupport(target, new StringSelection("typed")))).isTrue();
        assertThat(target.getText()).isEqualTo("typed");
        assertThat(new FileDrop(null, opened::add).importData(new TransferHandler.TransferSupport(target, new StringSelection("x")))).isFalse();
    }
}
