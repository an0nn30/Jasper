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
    @Test void rowsUseHostIconsAndTreatHtmlAsLiteralAtLargerFont() throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            var requested=new ArrayList<IconName>();
            var panel=new SftpPanel(name->{requested.add(name);return new dev.jasper.sdk.testing.FakeNamedIcon(name);});
            panel.table().setFont(new Font(Font.DIALOG,Font.PLAIN,24));panel.table().updateRowHeight();
            panel.showPage("host","/home",List.of(new FileEntry("<html>literal",FileEntry.Kind.DIRECTORY,0,1000,0755,"")),0,1,true);
            var renderer=(JLabel)panel.table().prepareRenderer(panel.table().getCellRenderer(0,0),0,0);
            assertThat(renderer.getText()).isEqualTo("<html>literal");assertThat(renderer.getClientProperty("html.disable")).isEqualTo(true);
            assertThat(panel.table().getRowHeight()).isGreaterThanOrEqualTo(28);assertThat(requested).contains(IconName.FOLDER,IconName.UPLOAD,IconName.DOWNLOAD);
            assertThat(panel.selection()).hasSize(1);
        });
    }
    @Test void deletionResultSurvivesAutomaticListingRefresh() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(()-> {
            var panel=new SftpPanel(icon->new javax.swing.ImageIcon(new java.awt.image.BufferedImage(16,16,2)));
            panel.operationStatus(false,"Cancelled; 9 items deleted; 1 error: /data/denied: Permission denied");
            panel.busy(true,"Loading folder…");panel.showPage("host","/data",java.util.List.of(),0,0,false);
            assertThat(panel.operationMessage()).contains("Cancelled", "9 items deleted", "/data/denied", "Permission denied");
        });
    }

    @Test void toolbarsAreFlatIconOnlyAndTheBottomRowsAlignLeft() throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            var panel=new SftpPanel(icon->new ImageIcon(new java.awt.image.BufferedImage(16,16,2)));var pages=new ArrayList<Long>();
            panel.actions(new SftpPanel.Actions(p->{},()->{},()->{},f->{},()->{},()->{},()->{},()->{},()->{},()->{},()->{},()->{},pages::add));
            for(String name:List.of("Up","Download selected","Upload files","Refresh","New folder","Delete selected","Copy paths","Previous page","Next page")) {
                var button=named(panel,name);
                assertThat(button.getParent()).as(name+" sits in a toolbar").isInstanceOf(JToolBar.class);
                assertThat(button.getText()).as(name+" is icon-only").isNullOrEmpty();
                assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo(name);
            }
            var toolbar=(JToolBar)named(panel,"Up").getParent();
            assertThat(toolbar.isFloatable()).isFalse();assertThat(toolbar.isRollover()).isTrue();assertThat(toolbar.isBorderPainted()).isFalse();
            assertThat(named(panel,"Previous page").getIcon()).isInstanceOf(ChevronIcon.class);
            assertThat(named(panel,"Next page").getIcon().getIconWidth()).isEqualTo(16);
            panel.showPage("prod","/srv",List.of(new FileEntry("a",FileEntry.Kind.FILE,1,1000,0644,"")),200,450,false);
            named(panel,"Next page").doClick();named(panel,"Previous page").doClick();
            assertThat(pages).containsExactly(400L,0L);
            var follow=find(panel,JCheckBox.class,box->"Follow terminal folder".equals(box.getText())).orElseThrow();
            for(var row:follow.getParent().getComponents()) assertThat(row.getAlignmentX()).as("bottom row "+row.getClass().getSimpleName()).isEqualTo(Component.LEFT_ALIGNMENT);
            var image=new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
            named(panel,"Next page").getIcon().paintIcon(named(panel,"Next page"),graphics,0,0);graphics.dispose();
            boolean painted=false;for(int x=0;x<16;x++) for(int y=0;y<16;y++) painted|=(image.getRGB(x,y)>>>24)!=0;
            assertThat(painted).as("the chevron draws something").isTrue();
        });
    }
    static JButton named(Container root,String name) { return find(root,JButton.class,button->name.equals(button.getAccessibleContext().getAccessibleName())).orElseThrow(()->new AssertionError("no button "+name)); }
    static <T> Optional<T> find(Component component,Class<T> type,java.util.function.Predicate<T> match) {
        if(type.isInstance(component) && match.test(type.cast(component))) return Optional.of(type.cast(component));
        if(component instanceof Container container) for(var child:container.getComponents()) { var found=find(child,type,match);if(found.isPresent()) return found; }
        return Optional.empty();
    }
}
