package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.ui.transfers.TransferRows;
import dev.jasper.remote.ui.transfers.TransferStrip;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Column;
import dev.jasper.sdk.ui.ColumnStateFile;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PanelHeader;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

/**
 * The SFTP explorer as kit descriptors on the testkit fake (Plan 2 Task 8c). Each baseline test keeps its behaviour:
 * what the old Swing table, toolbar, path field, popup and follow checkbox did is asserted through the nodes the kit
 * renders and the events its gestures report. Geometry the kit now owns (row height, the size column at a narrow width,
 * the path field) is asserted in the real host by {@code StagedRemoteSftpTest}.
 */
class SftpPanelTest {
    static final FileEntry FOLDER=new FileEntry("conf",FileEntry.Kind.DIRECTORY,0,1000,0755,""),
        FILE=new FileEntry("notes.toml",FileEntry.Kind.FILE,6,1000,0644,""),LINK=new FileEntry("alias",FileEntry.Kind.LINK,10,1000,0777,"notes.toml");

    final List<String> commands=new ArrayList<>();
    final List<Long> pages=new ArrayList<>();
    final List<DirectoryCache.Order> orders=new ArrayList<>();
    final List<Boolean> followed=new ArrayList<>();

    SftpPanel.Actions actions() {
        return new SftpPanel.Actions(path->commands.add("navigate "+path),()->commands.add("up"),()->commands.add("refresh"),followed::add,
            ()->commands.add("upload"),()->commands.add("upload folder"),()->commands.add("download"),()->commands.add("new folder"),
            ()->commands.add("delete"),()->commands.add("copy paths"),()->commands.add("copy to host"),()->commands.add("cancel"),(offset,order)-> { pages.add(offset);orders.add(order); },
            shown->commands.add("hidden "+shown));
    }

    static SftpPanel panel() { return new SftpPanel(new FakeKit("dev.jasper.remote")); }

    @Test void cacheKeepsHundredThousandEntriesOffTheUiAndPagesByName(@TempDir Path root) throws Exception {
        Path file;
        try(var cache=new DirectoryCache(root)) {
            file=cache.path();var batch=new ArrayList<FileEntry>();
            for(int i=0;i<100_000;i++) { batch.add(new FileEntry("file-"+i,FileEntry.Kind.FILE,i,1000,0644,""));if(batch.size()==256) { cache.append(batch);batch.clear(); } }
            if(!batch.isEmpty()) cache.append(batch);
            cache.append(List.of(new FileEntry("z-directory",FileEntry.Kind.DIRECTORY,0,1000,0755,"")));
            assertThat(cache.count()).isEqualTo(100_001);assertThat(cache.page(0,200).getFirst().name()).isEqualTo("file-0");assertThat(cache.page(100_000,200).getFirst().name()).as("folders sort with the files").isEqualTo("z-directory");assertThat(cache.page(100_000,200)).hasSize(1);
            assertThatThrownBy(()->cache.page(0,100_000)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(file).doesNotExist();
    }

    /** The toolbar's hidden-items toggle asks for the other choice and is pressed while hidden items show. */
    @Test void theHiddenItemsToggleAsksForTheOtherChoiceAndShowsTheCurrentOne() throws Exception {
        edt(()-> {
            var panel=panel();panel.actions(actions());
            panel.showPage("host","/home",List.of(FOLDER,FILE),0,2,false);
            assertThat(hiddenToggle(panel).tooltip()).isEqualTo("Show hidden items");
            assertThat(hiddenToggle(panel).selected()).contains(false);
            panel.handle(new UiEvent.Action(SftpPanel.HIDDEN));
            assertThat(commands).containsExactly("hidden true");
            panel.showHidden(true);
            assertThat(hiddenToggle(panel).selected()).contains(true);
            panel.handle(new UiEvent.Action(SftpPanel.HIDDEN));
            assertThat(commands).containsExactly("hidden true","hidden false");
        });
    }

    static Nodes.Button hiddenToggle(SftpPanel panel) {
        return node(panel.view(),SftpPanel.HIDDEN,Nodes.Button.class);
    }

    /** Hidden items (names starting with a dot) leave the pages and the counts unless shown, folders-only included. */
    @Test void cachePagesAndCountsLeaveHiddenItemsOutUnlessShown(@TempDir Path root) throws Exception {
        try(var cache=new DirectoryCache(root)) {
            cache.append(List.of(new FileEntry(".config",FileEntry.Kind.DIRECTORY,0,1000,0755,""),new FileEntry(".env",FileEntry.Kind.FILE,1,1000,0644,""),
                new FileEntry("docs",FileEntry.Kind.DIRECTORY,0,1000,0755,""),new FileEntry("notes.toml",FileEntry.Kind.FILE,6,1000,0644,"")));
            assertThat(cache.page(0,200,false,true)).extracting(FileEntry::name).containsExactly(".config",".env","docs","notes.toml");
            assertThat(cache.page(0,200,false,false)).extracting(FileEntry::name).containsExactly("docs","notes.toml");
            assertThat(cache.page(0,200,true,false)).extracting(FileEntry::name).containsExactly("docs");
            assertThat(cache.page(0,200,true,true)).extracting(FileEntry::name).containsExactly(".config","docs");
            assertThat(List.of(cache.count(false,true),cache.count(false,false),cache.count(true,false),cache.count(true,true))).containsExactly(4L,2L,1L,2L);
            assertThat(cache.page(1,200,false,false)).extracting(FileEntry::name).as("paging counts only what shows").containsExactly("notes.toml");
        }
    }

    /** Was rowsUseHostIconsAndTreatHtmlAsLiteralAtLargerFont: glyphs are the kit's, text is literal (kit Text), row height is the kit's. */
    @Test void rowsUseKitGlyphsAndKeepNamesLiteral() throws Exception {
        edt(()-> {
            var panel=panel();
            panel.showPage("host","/home",List.of(new FileEntry("<html>literal",FileEntry.Kind.DIRECTORY,0,1000,0755,""),FILE,LINK),0,3,true);
            var table=SftpTestAccess.table(panel.view());
            assertThat(table.rows()).extracting(row->row.cells().get("name")).containsExactly("<html>literal","notes.toml","alias");
            assertThat(table.rows()).extracting(row->row.icon().orElseThrow()).containsExactly("folder","file","link");
            assertThat(all(panel.view()).stream().filter(Nodes.Button.class::isInstance).map(node->((Nodes.Button)node).icon()).toList())
                .contains("back","forward","refresh");
            assertThat(node(panel.view(),SftpPanel.UPLOAD,Nodes.MenuButton.class).icon()).isEqualTo("upload");
            assertThat(panel.selection()).hasSize(1);
            assertThat(fake(panel.view()).focused()).as("a listing shown with focus focuses the table").contains(SftpPanel.FILES);
        });
    }

    @Test void deletionResultSurvivesAutomaticListingRefresh() throws Exception {
        edt(()-> {
            var panel=panel();
            panel.operationStatus(false,"Cancelled; 9 items deleted; 1 error: /data/denied: Permission denied");
            panel.busy(true,"Loading folder…");panel.showPage("host","/data",List.of(),0,0,false);
            assertThat(panel.operationMessage()).contains("Cancelled","9 items deleted","/data/denied","Permission denied");
            assertThat(texts(panel.view())).as("shown, not only kept").contains("Cancelled; 9 items deleted; 1 error: /data/denied: Permission denied");
        });
    }

    @Test void compactBrowserKeepsSizeColumnAndPagesOnlyWhenNeeded() throws Exception {
        edt(()-> {
            var panel=panel();panel.actions(actions());
            panel.showPage("amp","/home/ubuntu",List.of(
                new FileEntry("Documents",FileEntry.Kind.DIRECTORY,0,0,0755,""),
                new FileEntry(".bashrc",FileEntry.Kind.FILE,3700,0,0644,""),
                new FileEntry(".profile",FileEntry.Kind.FILE,1200,0,0644,""),
                new FileEntry(".bash_logout",FileEntry.Kind.FILE,840,0,0644,"")),0,4,false);
            var table=SftpTestAccess.table(panel.view());
            assertThat(table.columns()).extracting(column->column.title()).containsExactly("Name","Size");
            assertThat(table.columns().get(1).numeric()).as("sizes align to the trailing edge").isTrue();
            assertThat(table.rows()).extracting(row->row.cells().get("size")).containsExactly("—","3.7 KB","1.2 KB","840 B");
            assertThat(node(panel.view(),SftpPanel.PAGING,Nodes.Stack.class).state().visible()).isFalse();
            panel.showPage("amp","/home/ubuntu",List.of(new FileEntry("a",FileEntry.Kind.FILE,1,0,0644,"")),200,450,false);
            assertThat(node(panel.view(),SftpPanel.PAGING,Nodes.Stack.class).state().visible()).isTrue();
            assertThat(node(panel.view(),SftpPanel.PAGE,Nodes.Text.class).text()).isEqualTo("201–201 of 450");
            click(panel.view(),SftpPanel.NEXT);click(panel.view(),SftpPanel.PREVIOUS);
            assertThat(pages).containsExactly(400L,0L);
            for(String id:List.of(SftpPanel.BACK,SftpPanel.FORWARD,SftpPanel.REFRESH)) {
                var button=button(panel.view(),id);
                assertThat(button.label()).as("icon only").isEmpty();
                assertThat(button.kind()).isEqualTo(Nodes.ButtonKind.GHOST);
            }
            assertThat(button(panel.view(),SftpPanel.BACK).tooltip()).isEqualTo("Back");
            var upload=node(panel.view(),SftpPanel.UPLOAD,Nodes.MenuButton.class);
            assertThat(upload.label()).isEmpty();assertThat(upload.tooltip()).isEqualTo("Upload files");
            var labels=List.of("Up","New folder…","Upload folder…","Download selected…","Copy to host…","Copy paths","Delete selected…");
            assertThat(SftpTestAccess.menuLabels(panel)).containsExactlyElementsOf(labels);
            assertThat(SftpTestAccess.row(panel.view(),"a").contextActions().orElseThrow()).filteredOn(item->!item.separator())
                .extracting(MenuItem::label).containsExactlyElementsOf(labels);
            assertThat(SftpTestAccess.table(panel.view()).contextActions()).as("outside every row: the same menu on the selection (fix round 1, I1)")
                .filteredOn(item->!item.separator()).extracting(MenuItem::label).containsExactlyElementsOf(labels);
            assertThat(SftpTestAccess.table(panel.view()).contextActions()).filteredOn(item->!item.separator())
                .extracting(MenuItem::action).allMatch(action->action.startsWith(SftpPanel.MENU));
        });
    }

    @Test void headerAndNavigationTrackLoadedStateWithoutChangingTheEditablePath() throws Exception {
        edt(()-> {
            var panel=panel();
            var headers=new ArrayList<PanelHeader>();
            panel.onHeaderChanged(headers::add);panel.navigation(()->commands.add("back"),()->commands.add("forward"));
            panel.actions(actions());
            panel.busy(true,"Connecting…");assertThat(headers.getLast().active()).isFalse();
            panel.showPage("amp","/home/ubuntu",List.of(),0,0,false);panel.navigationAvailable(true,true);
            assertThat(headers.getLast().active()).isTrue();assertThat(headers.getLast().detail()).isEqualTo("amp");
            assertThat(texts(panel.view())).as("the host is the header's detail, not a label of the panel").doesNotContain("amp");
            click(panel.view(),SftpPanel.BACK);click(panel.view(),SftpPanel.FORWARD);
            var path=node(panel.view(),SftpPanel.PATH,Nodes.Breadcrumbs.class);
            assertThat(path.editing()).isFalse();
            press(panel.view(),path.editAction());
            path=node(panel.view(),SftpPanel.PATH,Nodes.Breadcrumbs.class);
            assertThat(path.editing()).isTrue();assertThat(path.value()).as("the field holds the folder shown").isEqualTo("/home/ubuntu");
            assertThat(fake(panel.view()).focused()).contains(SftpPanel.PATH);
            press(panel.view(),path.submitAction());
            assertThat(commands).containsExactly("back","forward","navigate /home/ubuntu");
            assertThat(panel.directory()).isEqualTo("/home/ubuntu");
            panel.busy(true,"Loading folder…");click(panel.view(),SftpPanel.BACK);
            assertThat(commands).hasSize(3);
            assertThat(button(panel.view(),SftpPanel.BACK).state().enabled()).isFalse();
            assertThat(node(panel.view(),SftpPanel.UPLOAD,Nodes.MenuButton.class).state().enabled()).isFalse();
            panel.clear("Closed");assertThat(headers.getLast().active()).isFalse();assertThat(headers.getLast().detail()).isEmpty();
            assertThat(button(panel.view(),SftpPanel.BACK).state().enabled()).isFalse();
        });
    }

    @Test void disconnectedStateHidesBrowserAndRestoresItAfterLoading() throws Exception {
        edt(()-> {
            var panel=panel();
            var empty=node(panel.view(),SftpPanel.EMPTY,Nodes.Empty.class);
            assertThat(empty.title()).isEqualTo("Not Connected");
            var choose=node(panel.view(),SftpPanel.CHOOSE,Nodes.MenuButton.class);
            assertThat(choose.label()).as("the label first; a menu-only button draws its chevron after it").isEqualTo("Choose Session");
            assertThat(choose.icon()).isEmpty();
            assertThat(choose.action()).as("menu-only").isEmpty();
            assertThat(choose.items()).as("no saved host yet").singleElement().satisfies(item->assertThat(item.enabled()).isFalse());
            assertThat(find(panel.view(),SftpPanel.FILES)).isEmpty();
            assertThat(node(panel.view(),SftpPanel.NAVIGATION,Nodes.Stack.class).state().visible()).isFalse();
            assertThat(node(panel.view(),SftpPanel.PAGING,Nodes.Stack.class).state().visible()).isFalse();
            panel.showPage("prod","/data",List.of(new FileEntry("a",FileEntry.Kind.FILE,1,0,0644,"")),0,1,false);
            assertThat(find(panel.view(),SftpPanel.CHOOSE)).isEmpty();
            assertThat(find(panel.view(),SftpPanel.FILES)).isPresent();
            assertThat(node(panel.view(),SftpPanel.NAVIGATION,Nodes.Stack.class).state().visible()).isTrue();
            panel.clear("SSH session closed");
            assertThat(find(panel.view(),SftpPanel.CHOOSE)).isPresent();
            assertThat(panel.selection()).isEmpty();
            assertThat(find(panel.view(),SftpPanel.FILES)).isEmpty();
            assertThat(texts(panel.view())).contains("SSH session closed");
        });
    }

    @Test void connectingAndErrorsRemainVisibleWithTransfersAndFollowControl() throws Exception {
        edt(()-> {
            var panel=panel();
            var strip=new TransferStrip(new TransferStrip.Actions((id,action)->{},id->{},id->{}));panel.transfers(strip);
            strip.rows(List.of(new TransferRows.Row(UUID.randomUUID(),"↑","Active transfers","tip","Queued",OptionalDouble.empty(),false,Optional.empty(),false,false)));
            panel.busy(true,"Connecting…");
            assertThat(button(panel.view(),SftpPanel.CANCEL).state().visible()).isTrue();
            assertThat(texts(panel.view())).contains("Connecting…","Transfers");
            assertThat(find(panel.view(),TransferStrip.ROWS)).as("the transfers show in the sidebar while it connects").isPresent();
            assertThat(node(panel.view(),SftpPanel.CHOOSE,Nodes.MenuButton.class).state().enabled()).isFalse();
            panel.error("Connection denied");
            assertThat(node(panel.view(),SftpPanel.CHOOSE,Nodes.MenuButton.class).state().enabled()).isTrue();
            assertThat(button(panel.view(),SftpPanel.CANCEL).state().visible()).isFalse();
            assertThat(texts(panel.view())).contains("Connection denied");
            var follow=node(panel.view(),SftpPanel.FOLLOW,Nodes.Toggle.class);
            assertThat(follow.label()).isEqualTo("Follow terminal folder");
            assertThat(follow.tooltip()).isEqualTo("Keep the SFTP browser in the active SSH terminal's folder");
            panel.actions(actions());
            emit(panel.view(),new UiEvent.Toggled(SftpPanel.FOLLOW,!follow.checked()));assertThat(followed).containsExactly(false);
            assertThat(node(panel.view(),SftpPanel.FOLLOW,Nodes.Toggle.class).checked()).isFalse();
            panel.following(true);assertThat(node(panel.view(),SftpPanel.FOLLOW,Nodes.Toggle.class).checked()).isTrue();
            panel.enableBrowsing(false);
            assertThat(node(panel.view(),SftpPanel.FOLLOW,Nodes.Toggle.class).state().enabled()).isFalse();
            emit(panel.view(),new UiEvent.Toggled(SftpPanel.FOLLOW,false));assertThat(followed).containsExactly(false);
        });
    }

    @Test void destinationModeRetainsItsOwnHostPickerAndBrowser() throws Exception {
        edt(()-> {
            int[] renders={0};
            var panel=SftpPanel.destination(()->renders[0]++,id->{});
            var body=panel.destinationBody();
            assertThat(find(body,SftpPanel.FILES)).isPresent();
            assertThat(find(body,SftpPanel.CHOOSE)).isEmpty();
            assertThat(find(body,SftpPanel.FOLLOW)).isEmpty();
            assertThat(find(body,SftpPanel.UPLOAD)).as("no upload in a destination").isEmpty();
            assertThat(find(body,SftpPanel.NEW_FOLDER)).isPresent();
            assertThat(((Nodes.Stack)find(body,SftpPanel.NAVIGATION).orElseThrow()).state().visible()).as("shown before any listing").isTrue();
            panel.showPage("prod","/srv",List.of(FOLDER),0,1,false);
            assertThat(renders[0]).as("the owner renders again").isPositive();
            var table=(Nodes.Table)find(panel.destinationBody(),SftpPanel.FILES).orElseThrow();
            assertThat(table.selectionMode()).isEqualTo(Nodes.SelectionMode.SINGLE);
            assertThat(table.contextActions()).isEmpty();
            assertThat(table.rows().getFirst().contextActions()).as("no menu on a destination row").contains(List.of());
            assertThatThrownBy(panel::view).isInstanceOf(IllegalStateException.class);
        });
    }

    @Test void filesGoToTheEditorOnlyWhenItIsPresent() throws Exception {
        edt(()-> {
            var panel=panel();panel.actions(actions());
            panel.showPage("host","/srv",List.of(FOLDER,FILE,LINK),0,3,false);
            assertThat(SftpTestAccess.menuLabels(panel)).as("hidden without the Editor").doesNotContain("Open in Editor");
            enter(panel,"notes.toml");assertThat(commands).as("a file does nothing without the Editor").isEmpty();
            enter(panel,"alias");assertThat(commands).as("a link navigates as before").containsExactly("navigate alias");
            var activated=new ArrayList<String>();var opened=new int[1];
            panel.editing(new SftpPanel.Editing(entry->activated.add(entry.name()),()->opened[0]++));
            assertThat(SftpTestAccess.menuLabels(panel)).contains("Open in Editor");
            enter(panel,"conf");assertThat(commands).as("a folder still navigates").containsExactly("navigate alias","navigate conf");
            enter(panel,"notes.toml");enter(panel,"alias");assertThat(activated).containsExactly("notes.toml","alias");
            // A double click reports the same activate action as Enter on the row it selected.
            emit(panel.view(),new UiEvent.Selected(SftpPanel.FILES,Set.of(SftpPanel.ROW+"notes.toml")));
            press(panel.view(),SftpTestAccess.table(panel.view()).activateAction());
            assertThat(activated).containsExactly("notes.toml","alias","notes.toml");
            emit(panel.view(),new UiEvent.Selected(SftpPanel.FILES,Set.of(SftpPanel.ROW+"conf")));
            assertThat(openInEditor(panel.view()).enabled()).as("a folder alone holds no file").isFalse();
            emit(panel.view(),new UiEvent.Selected(SftpPanel.FILES,Set.of(SftpPanel.ROW+"conf",SftpPanel.ROW+"notes.toml",SftpPanel.ROW+"alias")));
            assertThat(openInEditor(panel.view()).enabled()).isTrue();
            press(panel.view(),openInEditor(panel.view()).action());assertThat(opened[0]).isEqualTo(1);
            emit(panel.view(),new UiEvent.Selected(SftpPanel.FILES,Set.of(SftpPanel.ROW+"conf",SftpPanel.ROW+"notes.toml")));
            press(panel.view(),SftpTestAccess.table(panel.view()).activateAction());
            assertThat(activated).as("Enter with several rows selected does nothing").hasSize(3);
            assertThat(commands).hasSize(2);
        });
    }

    /** Controller carry (Task 8): row-owned menus replace select-on-right-click; a context press never selects. */
    @Test void aRowsMenuActsOnTheSelectionWhenTheRowIsSelectedAndOnTheRowAloneOtherwise() throws Exception {
        edt(()-> {
            var panel=panel();var seen=new ArrayList<List<String>>();
            panel.actions(new SftpPanel.Actions(p->{},()->{},()->{},f->{},()->{},()->{},()->seen.add(names(panel.selection())),()->{},
                ()->seen.add(names(panel.selection())),()->seen.add(names(panel.selection())),()->{},()->{},(p,o)->{},shown->{}));
            panel.showPage("host","/srv",List.of(FOLDER,FILE,LINK),0,3,false);
            emit(panel.view(),new UiEvent.Selected(SftpPanel.FILES,Set.of(SftpPanel.ROW+"conf",SftpPanel.ROW+"notes.toml")));
            pressRowItem(panel.view(),"notes.toml","Download selected…");
            pressRowItem(panel.view(),"alias","Delete selected…");
            pressRowItem(panel.view(),"conf","Copy paths");
            assertThat(seen).containsExactly(List.of("conf","notes.toml"),List.of("alias"),List.of("conf","notes.toml"));
            assertThat(names(panel.selection())).as("a row's menu never changes the selection").containsExactly("conf","notes.toml");
            assertThat(SftpTestAccess.table(panel.view()).selectedIds()).containsExactlyInAnyOrder(SftpPanel.ROW+"conf",SftpPanel.ROW+"notes.toml");
            assertThat(SftpTestAccess.row(panel.view(),"alias").contextActions().orElseThrow()).filteredOn(item->item.label().equals("Delete selected…"))
                .extracting(MenuItem::action).containsExactly("row:delete:"+SftpPanel.ROW+"alias");
            panel.busy(true,"Loading folder…");
            pressRowItem(panel.view(),"alias","Delete selected…");
            assertThat(seen).as("a stale menu acts on nothing while loading").hasSize(3);
        });
    }

    /** Fix round 1 (I1): in an empty folder the table's shared menu still offers the folder's actions. */
    @Test void anEmptyFolderStillOffersTheFoldersMenuOnTheTable() throws Exception {
        edt(()-> {
            var panel=panel();panel.actions(actions());
            panel.showPage("host","/srv/empty",List.of(),0,0,false);
            var menu=SftpTestAccess.table(panel.view()).contextActions();
            assertThat(menu).filteredOn(item->!item.separator()&&item.enabled()).extracting(MenuItem::label).containsExactly("Up","New folder…","Upload folder…");
            press(panel.view(),SftpTestAccess.item(menu,"New folder…").action());
            press(panel.view(),SftpTestAccess.item(menu,"Up").action());
            press(panel.view(),SftpTestAccess.item(menu,"Delete selected…").action());
            assertThat(commands).as("disabled items do nothing even if pressed").containsExactly("new folder","up");
        });
    }

    @Test void aNewListingOrPageSelectsItsFirstRowAndSelectionFollowsTheUser() throws Exception {
        edt(()-> {
            var panel=panel();panel.actions(actions());
            panel.showPage("host","/srv",List.of(FOLDER,FILE,LINK),0,3,false);
            assertThat(names(panel.selection())).containsExactly("conf");
            emit(panel.view(),new UiEvent.Selected(SftpPanel.FILES,Set.of(SftpPanel.ROW+"alias",SftpPanel.ROW+"missing")));
            assertThat(names(panel.selection())).as("only listed rows").containsExactly("alias");
            panel.showPage("host","/srv",List.of(FOLDER,FILE,LINK),0,3,false);
            assertThat(names(panel.selection())).as("a refreshed listing selects its first row").containsExactly("conf");
            panel.showPage("host","/srv",List.of(LINK),200,450,false);
            assertThat(names(panel.selection())).as("a new page selects its first row").containsExactly("alias");
            panel.showPage("host","/srv/empty",List.of(),0,0,false);
            assertThat(panel.selection()).isEmpty();
            assertThat(SftpTestAccess.table(panel.view()).selectionMode()).isEqualTo(Nodes.SelectionMode.MULTIPLE);
        });
    }

    /** Ruling G9: a crumb opens its folder (a strict addition); the free area edits the path; Escape or leaving cancels. */
    @Test void crumbsOpenTheirFolderAndTheFreeAreaEditsThePath() throws Exception {
        edt(()-> {
            var panel=panel();panel.actions(actions());
            panel.showPage("host","/srv/app/logs",List.of(FILE),0,1,false);
            var path=node(panel.view(),SftpPanel.PATH,Nodes.Breadcrumbs.class);
            assertThat(path.crumbs()).extracting(crumb->crumb.label()).containsExactly("/","srv","app","logs");
            assertThat(path.label()).isEqualTo("Remote directory");
            emit(panel.view(),new UiEvent.Selected(SftpPanel.PATH,Set.of(path.crumbs().get(2).id())));
            emit(panel.view(),new UiEvent.Selected(SftpPanel.PATH,Set.of(path.crumbs().getFirst().id())));
            assertThat(commands).containsExactly("navigate /srv/app","navigate /");
            press(panel.view(),path.editAction());
            emit(panel.view(),new UiEvent.Edited(SftpPanel.PATH,"/tmp"));
            press(panel.view(),path.cancelAction());
            assertThat(node(panel.view(),SftpPanel.PATH,Nodes.Breadcrumbs.class).editing()).isFalse();
            assertThat(commands).as("cancelling navigates nowhere").hasSize(2);
            press(panel.view(),path.editAction());
            emit(panel.view(),new UiEvent.Edited(SftpPanel.PATH,"../other"));
            press(panel.view(),path.submitAction());
            assertThat(commands).containsExactly("navigate /srv/app","navigate /","navigate ../other");
            panel.busy(true,"Loading folder…");
            assertThat(node(panel.view(),SftpPanel.PATH,Nodes.Breadcrumbs.class).state().enabled()).isFalse();
            emit(panel.view(),new UiEvent.Selected(SftpPanel.PATH,Set.of(path.crumbs().getFirst().id())));
            assertThat(commands).as("busy: crumbs are inert").hasSize(3);
        });
    }

    /** Ruling G6: the panel shortcuts keep the old bindings' guards. */
    @Test void theShortcutsKeepTheOldGuards() throws Exception {
        edt(()-> {
            var panel=panel();panel.actions(actions());panel.navigation(()->commands.add("back"),()->commands.add("forward"));
            panel.back();panel.forward();panel.parent();
            assertThat(commands).as("nothing to go back or up to").isEmpty();
            panel.showPage("host","/srv",List.of(FILE),0,1,false);panel.navigationAvailable(true,false);
            panel.back();panel.forward();panel.parent();
            assertThat(commands).containsExactly("back","up");
            panel.busy(true,"Loading folder…");panel.back();panel.parent();
            assertThat(commands).as("never while loading").hasSize(2);
            panel.busy(false,"");panel.enableBrowsing(false);panel.back();
            assertThat(commands).hasSize(2);
        });
    }

    @Test void aClosedPanelIgnoresLateListingsStatusAndTransfers() throws Exception {
        edt(()-> {
            var kit=new RecordingKit("dev.jasper.remote");
            var panel=new SftpPanel(kit);var headers=new ArrayList<PanelHeader>();panel.onHeaderChanged(headers::add);
            var strip=new TransferStrip(new TransferStrip.Actions((id,action)->{},id->{},id->{}));panel.transfers(strip);
            panel.close();int published=headers.size();
            panel.showPage("host","/srv",List.of(FILE),0,1,true);panel.busy(true,"x");panel.error("late");panel.clear("late");panel.operationStatus(false,"late");
            panel.navigationAvailable(true,true);panel.following(false);
            strip.rows(List.of(new TransferRows.Row(UUID.randomUUID(),"↑","late","tip","Queued",OptionalDouble.empty(),false,Optional.empty(),false,false)));
            assertThat(kit.lateCalls).isEmpty();
            assertThat(headers).hasSize(published);
            assertThat(panel.handle(new UiEvent.Action(SftpPanel.REFRESH))).isFalse();
        });
    }

    static SftpColumns columns(Path file) { return new SftpColumns(new ColumnStateFile(file),failure-> { throw new AssertionError(failure); }); }
    static Column column(SftpPanel panel,String id) { return SftpTestAccess.table(panel.view()).columns().stream().filter(c->c.id().equals(id)).findFirst().orElseThrow(); }

    /** A header click sorts the whole folder: the column shows the direction and page 0 is asked for in that order. */
    @Test void sortClickMarksTheColumnAndAsksForPageZeroInThatOrder(@TempDir Path root) throws Exception {
        edt(()-> {
            var panel=panel();panel.columns(columns(root.resolve("columns.properties")));panel.actions(actions());
            panel.showPage("host","/srv",List.of(FOLDER,FILE,LINK),200,450,false);
            assertThat(column(panel,"name").sorted()).as("Name ascending until the user picks a column").contains(true);
            emit(panel.view(),new UiEvent.Sorted(SftpPanel.FILES,"size"));
            assertThat(column(panel,"size").sorted()).contains(true);assertThat(column(panel,"name").sorted()).isEmpty();
            assertThat(pages).containsExactly(0L);assertThat(orders).containsExactly(new DirectoryCache.Order("size",true));
            emit(panel.view(),new UiEvent.Sorted(SftpPanel.FILES,"size"));
            assertThat(column(panel,"size").sorted()).contains(false);
            assertThat(pages).containsExactly(0L,0L);assertThat(orders).last().isEqualTo(new DirectoryCache.Order("size",false));
            click(panel.view(),SftpPanel.NEXT);
            assertThat(pages).last().isEqualTo(400L);assertThat(orders).as("paging keeps the order").last().isEqualTo(new DirectoryCache.Order("size",false));
        });
    }

    /** A sort click in one browser sorts every browser sharing the columns, whatever its layout. */
    @Test void aSortClickInOneBrowserReordersTheOthers(@TempDir Path root) throws Exception {
        edt(()-> {
            var shared=columns(root.resolve("columns.properties"));
            var narrow=panel();narrow.columns(shared);narrow.actions(actions());
            var other=new ArrayList<DirectoryCache.Order>();
            var wide=panel();wide.columns(shared);wide.wide(true);
            wide.actions(new SftpPanel.Actions(p->{},()->{},()->{},f->{},()->{},()->{},()->{},()->{},()->{},()->{},()->{},()->{},(offset,order)->other.add(order),shown->{}));
            narrow.showPage("host","/srv",List.of(FILE),0,1,false);wide.showPage("host","/srv",List.of(FILE),0,1,false);
            emit(narrow.view(),new UiEvent.Sorted(SftpPanel.FILES,"size"));
            assertThat(other).containsExactly(new DirectoryCache.Order("size",true));
            assertThat(column(wide,"size").sorted()).contains(true);
            assertThat(new ColumnStateFile(root.resolve("columns.properties")).get("sftp-classic-wide").sortColumn()).as("every layout keeps the sort").contains("size");
        });
    }

    @Test void resizedWidthsAreSavedUnderTheLayoutsKeyAndAppliedOnTheNextRender(@TempDir Path root) throws Exception {
        Path file=root.resolve("columns.properties");
        edt(()-> {
            var panel=panel();panel.columns(columns(file));panel.actions(actions());panel.wide(true);
            panel.showPage("host","/srv",List.of(FOLDER,FILE),0,2,false);
            var widths=Map.of("name",300,"modified",120,"permissions",90,"size",70);
            emit(panel.view(),new UiEvent.ColumnsResized(SftpPanel.FILES,widths));
            assertThat(new ColumnStateFile(file).get("sftp-wide").widths()).isEqualTo(widths);
            for(var entry:widths.entrySet()) assertThat(column(panel,entry.getKey()).width()).isEqualTo(OptionalInt.of(entry.getValue()));
            panel.wide(false);
            assertThat(SftpTestAccess.table(panel.view()).columns()).as("the narrow table keeps its own widths").allSatisfy(c->assertThat(c.width()).isEmpty());
            assertThat(new ColumnStateFile(file).get("sftp").widths()).isEmpty();
        });
        var properties=new Properties();try(var in=java.nio.file.Files.newInputStream(file)) { properties.load(in); }
        assertThat(properties).containsKey("sftp-wide");
    }

    /** The browser speaks SFTP's POSIX paths; LocalEndpoint stands in for SFTP only where local paths are POSIX too. */
    @Test @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void restoredStateAppliesOnFirstRender(@TempDir Path root) throws Exception {
        root=root.toRealPath();Path folder=java.nio.file.Files.createDirectory(root.resolve("home"));
        java.nio.file.Files.writeString(folder.resolve("a"),"1");java.nio.file.Files.writeString(folder.resolve("b"),"x".repeat(300));java.nio.file.Files.writeString(folder.resolve("c"),"x".repeat(20));
        Path file=root.resolve("columns.properties");java.nio.file.Files.writeString(file,"sftp=sort=size,desc;widths=name:260,size:80\n");
        var holder=new SftpController[1];var panel=new SftpPanel[1];Path cache=root.resolve("cache");
        try(var workers=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            javax.swing.SwingUtilities.invokeAndWait(()-> { panel[0]=panel();panel[0].columns(columns(file));
                holder[0]=new SftpController(cache,workers,javax.swing.SwingUtilities::invokeLater,identity->java.util.concurrent.CompletableFuture.completedFuture(new dev.jasper.remote.sftp.LocalEndpoint()),panel[0]);
                holder[0].open(UUID.randomUUID(),SftpControllerTest.host("sorted"),folder.toString(),true); });
            try {
                SftpControllerTest.awaitRows(panel[0],"b","c","a");
                edt(()-> {
                    assertThat(column(panel[0],"size").sorted()).contains(false);assertThat(column(panel[0],"name").sorted()).isEmpty();
                    assertThat(column(panel[0],"name").width()).isEqualTo(OptionalInt.of(260));assertThat(column(panel[0],"size").width()).isEqualTo(OptionalInt.of(80));
                });
            } finally { javax.swing.SwingUtilities.invokeAndWait(holder[0]::close);holder[0].stopped().get(5,java.util.concurrent.TimeUnit.SECONDS); }
        }
    }

    private static List<String> names(List<FileEntry> entries) { return entries.stream().map(FileEntry::name).toList(); }

    private static void enter(SftpPanel panel,String name) {
        emit(panel.view(),new UiEvent.Selected(SftpPanel.FILES,Set.of(SftpPanel.ROW+name)));
        press(panel.view(),SftpTestAccess.table(panel.view()).activateAction());
    }

    private static MenuItem openInEditor(UiView view) {
        return SftpTestAccess.item(node(view,SftpPanel.UPLOAD,Nodes.MenuButton.class).items(),"Open in Editor");
    }

    private static void pressRowItem(UiView view,String row,String label) {
        press(view,SftpTestAccess.item(SftpTestAccess.row(view,row).contextActions().orElseThrow(),label).action());
    }
}
