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
            assertThat(panel.table().getRowHeight()).isGreaterThanOrEqualTo(28);assertThat(requested).contains(IconName.FOLDER,IconName.UPLOAD,IconName.DOWNLOAD,IconName.PREVIOUS,IconName.NEXT);
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
            var panel=new SftpPanel(dev.jasper.sdk.testing.FakeNamedIcon::new);var pages=new ArrayList<Long>();
            panel.actions(new SftpPanel.Actions(p->{},()->{},()->{},f->{},()->{},()->{},()->{},()->{},()->{},()->{},()->{},()->{},pages::add));
            for(String name:List.of("Up","Download selected","Upload files","Refresh","New folder","Delete selected","Copy paths","Previous page","Next page")) {
                var button=named(panel,name);
                assertThat(button.getParent()).as(name+" sits in a toolbar").isInstanceOf(JToolBar.class);
                assertThat(button.getText()).as(name+" is icon-only").isNullOrEmpty();
                assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo(name);
            }
            var toolbar=(JToolBar)named(panel,"Up").getParent();
            assertThat(toolbar.isFloatable()).isFalse();assertThat(toolbar.isRollover()).isTrue();assertThat(toolbar.isBorderPainted()).isFalse();
            assertThat(named(panel,"Previous page").getIcon()).isEqualTo(new dev.jasper.sdk.testing.FakeNamedIcon(IconName.PREVIOUS));
            assertThat(named(panel,"Next page").getIcon().getIconWidth()).isEqualTo(16);
            panel.showPage("prod","/srv",List.of(new FileEntry("a",FileEntry.Kind.FILE,1,1000,0644,"")),200,450,false);
            named(panel,"Next page").doClick();named(panel,"Previous page").doClick();
            assertThat(pages).containsExactly(400L,0L);
            var follow=find(panel,JCheckBox.class,box->"Follow terminal folder".equals(box.getText())).orElseThrow();
            for(var row:follow.getParent().getComponents()) assertThat(row.getAlignmentX()).as("bottom row "+row.getClass().getSimpleName()).isEqualTo(Component.LEFT_ALIGNMENT);
            assertThat(named(panel,"Next page").getIcon())
                .isEqualTo(new dev.jasper.sdk.testing.FakeNamedIcon(IconName.NEXT));
        });
    }
    static JButton named(Container root,String name) { return find(root,JButton.class,button->name.equals(button.getAccessibleContext().getAccessibleName())).orElseThrow(()->new AssertionError("no button "+name)); }
    static <T> Optional<T> find(Component component,Class<T> type,java.util.function.Predicate<T> match) {
        if(type.isInstance(component) && match.test(type.cast(component))) return Optional.of(type.cast(component));
        if(component instanceof Container container) for(var child:container.getComponents()) { var found=find(child,type,match);if(found.isPresent()) return found; }
        return Optional.empty();
    }

    @Test void filesGoToTheEditorOnlyWhenItIsPresent() throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            var panel=new SftpPanel(icon->new ImageIcon(new java.awt.image.BufferedImage(16,16,2)));var navigated=new ArrayList<String>();
            panel.actions(new SftpPanel.Actions(navigated::add,()->{},()->{},f->{},()->{},()->{},()->{},()->{},()->{},()->{},()->{},()->{},p->{}));
            var folder=new FileEntry("conf",FileEntry.Kind.DIRECTORY,0,1000,0755,"");var file=new FileEntry("notes.toml",FileEntry.Kind.FILE,6,1000,0644,"");var link=new FileEntry("alias",FileEntry.Kind.LINK,10,1000,0777,"notes.toml");
            panel.showPage("host","/srv",List.of(folder,file,link),0,3,false);
            var item=menuItem(panel.table().getComponentPopupMenu(),"Open in Editor");
            assertThat(item.isVisible()).as("hidden without the Editor").isFalse();
            enter(panel,1);assertThat(navigated).as("a file does nothing without the Editor").isEmpty();
            enter(panel,2);assertThat(navigated).as("a link navigates as before").containsExactly("alias");
            var activated=new ArrayList<String>();var opened=new int[1];
            panel.editing(new SftpPanel.Editing(entry->activated.add(entry.name()),()->opened[0]++));
            assertThat(item.isVisible()).isTrue();
            enter(panel,0);assertThat(navigated).as("a folder still navigates").containsExactly("alias","conf");
            enter(panel,1);enter(panel,2);assertThat(activated).containsExactly("notes.toml","alias");
            doubleClick(panel,1);assertThat(activated).containsExactly("notes.toml","alias","notes.toml");
            panel.table().setRowSelectionInterval(0,0);assertThat(item.isEnabled()).as("a folder alone holds no file").isFalse();
            panel.table().setRowSelectionInterval(0,2);assertThat(item.isEnabled()).isTrue();
            item.doClick();assertThat(opened[0]).isEqualTo(1);
        });
    }
    private static void enter(SftpPanel panel,int row) { panel.table().setRowSelectionInterval(row,row);panel.table().getActionMap().get("browse").actionPerformed(null); }
    private static void doubleClick(SftpPanel panel,int row) {
        panel.table().setRowSelectionInterval(row,row);var cell=panel.table().getCellRect(row,0,true);
        panel.table().dispatchEvent(new java.awt.event.MouseEvent(panel.table(),java.awt.event.MouseEvent.MOUSE_CLICKED,0,0,cell.x+2,cell.y+2,2,false,java.awt.event.MouseEvent.BUTTON1));
    }
    private static JMenuItem menuItem(JPopupMenu menu,String text) {
        return Arrays.stream(menu.getComponents()).filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast).filter(item->text.equals(item.getText())).findFirst().orElseThrow();
    }
}
