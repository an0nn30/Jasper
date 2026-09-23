package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.sdk.ui.IconName;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class SftpPanelTest {
    @Test void cacheKeepsHundredThousandEntriesOffTheUiAndPagesDirectoriesFirst(@TempDir Path root) throws Exception {
        Path file;
        try(var cache=new DirectoryCache(root)) {
            file=cache.path();var batch=new ArrayList<FileEntry>();
            for(int i=0;i<100_000;i++) { batch.add(new FileEntry("file-"+i,FileEntry.Kind.FILE,i,1000,0644,""));if(batch.size()==256) { cache.append(batch);batch.clear(); } }
            if(!batch.isEmpty()) cache.append(batch);
            cache.append(List.of(new FileEntry("z-directory",FileEntry.Kind.DIRECTORY,0,1000,0755,"")));
            assertThat(cache.count()).isEqualTo(100_001);assertThat(cache.page(0,200).getFirst().name()).isEqualTo("z-directory");assertThat(cache.page(100_000,200)).hasSize(1);
            assertThatThrownBy(()->cache.page(0,100_000)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(file).doesNotExist();
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void rowsUseHostIconsAndTreatHtmlAsLiteralAtLargerFont(boolean retro) throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            var requested=new ArrayList<IconName>();
            var panel=new SftpPanel(name->{requested.add(name);return new dev.jasper.sdk.testing.FakeNamedIcon(name,retro);});
            panel.table().setFont(new Font(Font.DIALOG,Font.PLAIN,24));panel.table().updateRowHeight();
            panel.showPage("host","/home",List.of(new FileEntry("<html>literal",FileEntry.Kind.DIRECTORY,0,1000,0755,"")),0,1,true);
            var renderer=(JLabel)panel.table().prepareRenderer(panel.table().getCellRenderer(0,0),0,0);
            assertThat(((dev.jasper.sdk.testing.FakeNamedIcon)renderer.getIcon()).retro()).isEqualTo(retro);
            assertThat(renderer.getText()).isEqualTo("<html>literal");assertThat(renderer.getClientProperty("html.disable")).isEqualTo(true);
            assertThat(panel.table().getRowHeight()).isGreaterThanOrEqualTo(28);assertThat(requested).contains(IconName.FOLDER,IconName.UPLOAD,IconName.DOWNLOAD);
            assertThat(panel.selection()).hasSize(1);
        });
    }
}
