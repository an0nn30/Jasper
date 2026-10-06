package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.ui.transfers.TransferStrip;
import dev.jasper.sdk.ui.Column;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Crumb;
import dev.jasper.sdk.ui.MenuItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.PanelHeader;
import dev.jasper.sdk.ui.TableRow;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The compact SFTP file explorer on host-rendered kit descriptors (Plan 2 Task 8c). In the sidebar it owns one kit
 * Panel: a row of icon buttons (Back, Forward, Refresh and Upload, whose menu holds the folder's other actions) over the
 * path as breadcrumbs, the folder's entries as a two-column table (folders first, a folder, file or link glyph, the
 * modified time in the row tooltip), and a footer with paging, the status line, the last operation's result, the Follow
 * terminal folder switch and the window's transfers. While nothing is connected the body is a "Not Connected" empty
 * state with Choose Session. In a Copy to host dialog the same browser is embedded in another owner's form
 * ({@link #destination}): folders only, single selection, no menus, no follow switch, a New folder button.
 *
 * <p>Rows are keyed by entry name. A new listing or page selects its first row. Enter or a double click on one entry
 * opens a folder (or a link, without the Editor) or hands a file or link to the Editor. Each row's own context menu acts
 * on the selection when the row is selected and on that row alone otherwise; a context press never selects. Clicking a
 * crumb opens that folder; clicking beside the crumbs edits the path (Enter goes, Escape or leaving cancels). UI thread
 * only; after {@link #close()} nothing touches the view.
 */
public final class SftpPanel implements AutoCloseable {
    public record Actions(Consumer<String> navigate,Runnable up,Runnable refresh,Consumer<Boolean> follow,
                          Runnable upload,Runnable uploadFolder,Runnable download,Runnable newFolder,
                          Runnable delete,Runnable copyPath,Runnable copyToHost,Runnable cancel,Consumer<Long> page) {}
    /** What double-click, Enter and Open in Editor do with files and links; set only when the Editor is installed. */
    public record Editing(Consumer<FileEntry> activate,Runnable open) {}

    public static final String ROOT="sftp", HEADER="sftp.header", NAVIGATION="sftp.navigation", TOOLS="sftp.tools", BACK="sftp.back",
        FORWARD="sftp.forward", REFRESH="sftp.refresh", UPLOAD="sftp.upload", NEW_FOLDER="sftp.new-folder", PATH="sftp.path",
        FILES="sftp.files", EMPTY="sftp.empty", CHOOSE="sftp.choose", FOOTER="sftp.footer", PAGING="sftp.paging", PAGE="sftp.page",
        PREVIOUS="sftp.previous", NEXT="sftp.next", STATUS="sftp.status", MESSAGE="sftp.message", CANCEL="sftp.cancel",
        RESULT="sftp.result", FOLLOW="sftp.follow", FOLLOW_ROW="sftp.follow-row", BROWSER="sftp.browser";
    /** The table's activate action (Enter, double click), the path's actions and the menu action prefixes. */
    static final String OPEN="open", PATH_EDIT="path.edit", PATH_GO="path.go", PATH_DONE="path.done", MENU="menu:", ROW_MENU="row:", ROW="entry:";
    static final String NO_HOST="Select an SSH host to browse files", FOLLOW_TEXT="Follow terminal folder",
        FOLLOW_DESCRIPTION="Keep the SFTP browser in the active SSH terminal's folder";
    private static final int PAGE_SIZE=200;

    private final UiView view;
    private final Runnable changed;
    private final Consumer<String> focuser;
    private final boolean destination;
    private Actions actions;
    private Editing editing;
    private Runnable back=()->{},forward=()->{},choose=()->{},onLoaded=()->{};
    private Consumer<PanelHeader> headerChanged=header->{};
    private TransferStrip transfers;
    private boolean canBack,canForward,working,browsingEnabled=true,loaded,following=true,editingPath,closed;
    private String host=NO_HOST,directory="",message="",result="",pathValue="";
    private List<FileEntry> entries=List.of();
    private Set<String> selected=Set.of();
    /** While a row's own menu item runs on that row alone: what {@link #selection()} reports. */
    private List<FileEntry> acting;
    private long offset,total;

    /** The sidebar explorer, owning its kit view. */
    public SftpPanel(UiKit kit) {
        changed=null;focuser=null;destination=false;
        view=kit.create(new ViewSpec(panel()),event->{ if(!closed) handle(event); });
    }

    private SftpPanel(Runnable changed,Consumer<String> focus) {
        this.changed=Objects.requireNonNull(changed);this.focuser=Objects.requireNonNull(focus);destination=true;view=null;
    }

    /**
     * A destination browser embedded in another owner's form: {@link #destinationBody()} is its content,
     * {@link #handle} takes its events, {@code changed} renders the owner again and {@code focus} focuses a node there.
     */
    static SftpPanel destination(Runnable changed,Consumer<String> focus) { return new SftpPanel(changed,focus); }

    /** The sidebar's kit view. */
    public UiView view() { if(view==null) throw new IllegalStateException("An embedded browser has no view of its own");return view; }

    public void actions(Actions actions) { this.actions=actions;render(); }
    /** Navigation commands apply to the currently connected browser's history. */
    public void navigation(Runnable back,Runnable forward) { this.back=Objects.requireNonNull(back);this.forward=Objects.requireNonNull(forward); }
    public void navigationAvailable(boolean back,boolean forward) { canBack=back;canForward=forward;render(); }
    /** Publishes connection detail to the owning app header, including the current state immediately. */
    public void onHeaderChanged(Consumer<PanelHeader> listener) { headerChanged=Objects.requireNonNull(listener);publishHeader(); }
    /** Opens the owning window's existing SSH host chooser from the disconnected state. */
    public void onChooseSession(Runnable action) { choose=Objects.requireNonNull(action); }
    /** Runs after every listing or page shown. */
    public void onLoaded(Runnable listener) { onLoaded=Objects.requireNonNull(listener); }
    /** Shows Open in Editor and sends files to {@code editing}: double-click and Enter on one file or link, and the menu item. */
    public void editing(Editing editing) { this.editing=editing;render(); }
    /** The window's transfers, shown at the bottom of the sidebar. */
    public void transfers(TransferStrip strip) {
        if(transfers!=null) transfers.onChanged(()->{});
        transfers=strip;strip.onChanged(this::render);render();
    }

    /** What file actions act on: the selected entries in display order, or the row whose own menu item is running. */
    public List<FileEntry> selection() {
        if(acting!=null) return acting;
        var result=new ArrayList<FileEntry>();for(var entry:entries) if(selected.contains(rowId(entry))) result.add(entry);return List.copyOf(result);
    }
    public String directory() { return directory; }
    public boolean following() { return following; }
    public void following(boolean value) { following=value;render(); }

    public void showPage(String hostname,String directory,List<FileEntry> values,long offset,long total,boolean focus) {
        if(closed) return;
        entries=List.copyOf(values);this.offset=offset;this.total=total;loaded=true;host=hostname;this.directory=directory;
        // A new listing or page selects its first row, as the old table did.
        selected=entries.isEmpty()?Set.of():Set.of(rowId(entries.getFirst()));
        working=false;message="";render();publishHeader();onLoaded.run();
        if(focus) focus(FILES);
    }
    public void busy(boolean busy,String text) { if(closed) return;working=busy;message=text;render(); }
    public void error(String text) { busy(false,text); }
    /** Empties the view: no host, folder or rows, and nothing for file actions to act on. */
    public void clear(String text) {
        if(closed) return;
        entries=List.of();offset=0;total=0;loaded=false;host=NO_HOST;directory="";selected=Set.of();editingPath=false;
        canBack=false;canForward=false;working=false;message=text;render();publishHeader();
    }
    public void operationStatus(boolean running,String text) { if(closed) return;result=running?"":text;busy(running,running?text:""); }
    public void enableBrowsing(boolean enabled) { browsingEnabled=enabled;render(); }

    /** The panel's Back shortcut (Alt+Left anywhere in the panel). */
    public void back() { if(browsingEnabled && canBack && !working) back.run(); }
    /** The panel's Forward shortcut (Alt+Right anywhere in the panel). */
    public void forward() { if(browsingEnabled && canForward && !working) forward.run(); }
    /** The panel's parent folder shortcut (Alt+Up). */
    public void parent() { if(actions!=null && loaded && !working) actions.up().run(); }

    String operationMessage() { return result; }
    String hostText() { return host; }
    String messageText() { return message; }
    /** The names the table shows, in order. */
    List<String> rowNames() { return entries.stream().map(FileEntry::name).toList(); }
    static String rowId(FileEntry entry) { return ROW+entry.name(); }

    @Override public void close() {
        if(closed) return;closed=true;headerChanged=header->{};
        if(transfers!=null) transfers.onChanged(()->{});
        if(view!=null) view.close();
    }

    // ---- events

    /** Acts on one of the browser's events and says whether it was one. */
    public boolean handle(UiEvent event) {
        if(closed) return false;
        switch(event) {
            case UiEvent.Selected chosen when chosen.id().equals(FILES) -> {
                var known=new LinkedHashSet<String>();for(var entry:entries) if(chosen.ids().contains(rowId(entry))) known.add(rowId(entry));
                selected=Set.copyOf(known);render();return true;
            }
            case UiEvent.Selected crumb when crumb.id().equals(PATH) -> {
                crumb.ids().stream().findFirst().flatMap(this::crumbTarget).ifPresent(target-> { if(actions!=null && browsingEnabled && !working) actions.navigate().accept(target); });
                return true;
            }
            case UiEvent.Edited edited when edited.id().equals(PATH) -> { pathValue=edited.text();return true; }
            case UiEvent.Toggled toggled when toggled.id().equals(FOLLOW) -> {
                if(browsingEnabled) { following=toggled.checked();render();if(actions!=null) actions.follow().accept(following); }
                return true;
            }
            case UiEvent.Action action -> { return act(action.id()) || transfers!=null && transfers.handle(event); }
            default -> { return transfers!=null && transfers.handle(event); }
        }
    }

    private boolean act(String id) {
        boolean ready=actions!=null && loaded && !working;
        switch(id) {
            case OPEN -> activate();
            case BACK -> { if(ready) back(); }
            case FORWARD -> { if(ready) forward(); }
            case REFRESH -> { if(ready && browsingEnabled) actions.refresh().run(); }
            case UPLOAD -> { if(ready) actions.upload().run(); }
            case NEW_FOLDER -> { if(ready) actions.newFolder().run(); }
            case CANCEL -> { if(actions!=null) actions.cancel().run(); }
            case PREVIOUS -> { if(ready && offset>0) actions.page().accept(Math.max(0,offset-PAGE_SIZE)); }
            case NEXT -> { if(ready && offset+entries.size()<total) actions.page().accept(offset+PAGE_SIZE); }
            case CHOOSE -> { if(!working) choose.run(); }
            case PATH_EDIT -> {
                if(browsingEnabled && !working && (loaded || destination)) { editingPath=true;pathValue=directory;render();focus(PATH); }
            }
            case PATH_GO -> {
                if(!editingPath) return true;
                editingPath=false;String target=pathValue;render();
                if(actions!=null && browsingEnabled && !working) actions.navigate().accept(target);
            }
            case PATH_DONE -> { if(editingPath) { editingPath=false;render(); } }
            default -> {
                if(id.startsWith(MENU)) { run(id.substring(MENU.length()),selection());return true; }
                if(id.startsWith(ROW_MENU)) { rowMenu(id.substring(ROW_MENU.length()));return true; }
                return false;
            }
        }
        return true;
    }

    /** Enter or a double click on exactly one entry. */
    private void activate() {
        if(actions==null || working || !loaded) return;var chosen=selection();if(chosen.size()!=1) return;var entry=chosen.getFirst();
        if(entry.kind()==FileEntry.Kind.DIRECTORY || entry.kind()==FileEntry.Kind.LINK && editing==null) actions.navigate().accept(entry.name());
        else if(editing!=null && (entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK)) editing.activate().accept(entry);
    }

    /** A row's own menu item: on the selection when the row is selected, otherwise on that row alone. */
    private void rowMenu(String rest) {
        int colon=rest.indexOf(':');if(colon<0) return;
        String verb=rest.substring(0,colon),row=rest.substring(colon+1);
        var entry=entries.stream().filter(candidate->rowId(candidate).equals(row)).findFirst();if(entry.isEmpty()) return;
        List<FileEntry> target=selected.contains(row)?selection():List.of(entry.get());
        acting=target;
        try { run(verb,target); } finally { acting=null; }
    }

    private void run(String verb,List<FileEntry> target) {
        if(actions==null || !enabled(verb,target)) return;
        switch(verb) {
            case "open" -> editing.open().run();
            case "up" -> actions.up().run();
            case "new-folder" -> actions.newFolder().run();
            case "upload-folder" -> actions.uploadFolder().run();
            case "download" -> actions.download().run();
            case "copy-host" -> actions.copyToHost().run();
            case "copy-paths" -> actions.copyPath().run();
            case "delete" -> actions.delete().run();
            default -> { }
        }
    }

    /** Whether a menu verb applies to {@code target} now: the old popup's enabled rules. */
    private boolean enabled(String verb,List<FileEntry> target) {
        boolean ready=loaded && !working,any=ready && !target.isEmpty();
        return switch(verb) {
            case "open" -> editing!=null && ready && target.stream().anyMatch(entry->entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK);
            case "up" -> ready && !directory.equals("/");
            case "new-folder","upload-folder" -> ready;
            case "download","copy-host","copy-paths","delete" -> any;
            default -> false;
        };
    }

    private Optional<String> crumbTarget(String crumbId) {
        var crumbs=crumbs();
        for(int i=0;i<crumbs.size();i++) if(crumbId(i).equals(crumbId)) return Optional.of(crumbs.get(i)[1]);
        return Optional.empty();
    }

    // ---- descriptors

    private void render() {
        if(closed) return;
        if(view!=null) view.update(new ViewSpec(panel()));
        else if(changed!=null) changed.run();
    }

    private void focus(String id) { if(view!=null) view.focus(id);else if(focuser!=null) focuser.accept(id); }

    private void publishHeader() { if(!destination && !closed) headerChanged.accept(new PanelHeader(loaded?host:"",loaded,List.of())); }

    private static ControlState state(boolean visible,boolean enabled) { return new ControlState(visible,enabled,false,""); }

    private Nodes.Panel panel() {
        var header=new Nodes.Header(HEADER,ControlState.READY,"SFTP",OptionalInt.empty(),Optional.empty(),Optional.empty(),List.of());
        Nodes.Node body=loaded?table():new Nodes.Empty(EMPTY,ControlState.READY,
            new Nodes.Icon(EMPTY+".icon",ControlState.READY,"folder",Nodes.Hue.NEUTRAL,Nodes.IconSize.EMPTY,"SFTP"),"Not Connected",
            "Connect to an SSH session to browse its files.",
            List.of(new Nodes.Button(CHOOSE,state(true,!working),"Choose Session","chevron.down",Nodes.ButtonKind.SECONDARY,CHOOSE,"")));
        var footer=new ArrayList<Nodes.Node>(List.of(paging(),status(),resultLine(),
            new Nodes.SettingRow(FOLLOW_ROW,ControlState.READY,FOLLOW_TEXT,"",
                new Nodes.Toggle(FOLLOW,state(true,browsingEnabled),FOLLOW_TEXT,following,FOLLOW,true,FOLLOW_DESCRIPTION),false)));
        if(transfers!=null) transfers.node().ifPresent(footer::add);
        return new Nodes.Panel(ROOT,ControlState.READY,header,Optional.of(navigation()),body,
            Optional.of(new Nodes.Stack(FOOTER,ControlState.READY,Nodes.LayoutRole.VERTICAL,footer)));
    }

    /** The Copy to host browser: tools and path, the status line, folders, paging and the last operation's result. */
    Nodes.Node destinationBody() {
        return new Nodes.Stack(BROWSER,ControlState.READY,Nodes.LayoutRole.VERTICAL,List.of(navigation(),status(),table(),paging(),resultLine()));
    }

    private Nodes.Node navigation() {
        boolean ready=loaded && !working;
        var tools=new ArrayList<Nodes.Node>();
        tools.add(tool(BACK,"back","Back",browsingEnabled && ready && canBack));
        tools.add(tool(FORWARD,"forward","Forward",browsingEnabled && ready && canForward));
        tools.add(tool(REFRESH,"refresh","Refresh",browsingEnabled && ready));
        if(destination) tools.add(tool(NEW_FOLDER,"folder.add","New folder",ready));
        else tools.add(new Nodes.MenuButton(UPLOAD,state(true,ready),"","upload",Nodes.ButtonKind.GHOST,UPLOAD,menu(MENU,"",selection()),"Upload files"));
        var crumbs=new ArrayList<Crumb>();var steps=crumbs();
        for(int i=0;i<steps.size();i++) crumbs.add(new Crumb(crumbId(i),steps.get(i)[0],List.of()));
        var path=new Nodes.Breadcrumbs(PATH,state(true,browsingEnabled && !working),crumbs,"Remote directory",editingPath,pathValue,PATH_EDIT,PATH_GO,PATH_DONE);
        return new Nodes.Stack(NAVIGATION,state(loaded || destination,true),Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.Stack(TOOLS,ControlState.READY,Nodes.LayoutRole.INLINE,tools),path));
    }

    private static Nodes.Button tool(String id,String icon,String name,boolean enabled) {
        return new Nodes.Button(id,state(true,enabled),"",icon,Nodes.ButtonKind.GHOST,id,name);
    }

    private static String crumbId(int index) { return "crumb."+index; }

    /** Each crumb's label and the folder it opens: the root, then every folder down to the one shown. */
    private List<String[]> crumbs() {
        var result=new ArrayList<String[]>();
        if(directory.isEmpty()) return result;
        if(!directory.startsWith("/")) { result.add(new String[] {directory,directory});return result; }
        result.add(new String[] {"/","/"});
        var path=new StringBuilder();
        for(String part:directory.substring(1).split("/")) {
            if(part.isEmpty()) continue;
            path.append('/').append(part);result.add(new String[] {part,path.toString()});
        }
        return result;
    }

    private Nodes.Table table() {
        var rows=new ArrayList<TableRow>();
        for(var entry:entries) {
            String id=rowId(entry);
            rows.add(new TableRow(id,Map.of("name",entry.name(),"size",formatSize(entry)),Optional.of(glyph(entry)),false,
                Optional.of(destination?List.<MenuItem>of():menu(ROW_MENU,":"+id,selected.contains(id)?selection():List.of(entry))),
                entry.name()+" — "+formatSize(entry)+" — "+modified(entry)));
        }
        return new Nodes.Table(FILES,state(true,!working),List.of(
                new Column("name","Name",Nodes.TextRole.BODY,false,2.4,Optional.empty()),
                new Column("size","Size",Nodes.TextRole.DESCRIPTION,true,1,Optional.empty())),
            rows,selected,OPEN,List.of(),destination?Nodes.SelectionMode.SINGLE:Nodes.SelectionMode.MULTIPLE,Nodes.ScrollPolicy.STANDARD);
    }

    private static String glyph(FileEntry entry) {
        return switch(entry.kind()) { case DIRECTORY->"folder";case LINK->"link";default->"file"; };
    }

    /** The folder's actions, as the old right-click menu listed them; {@code suffix} names the row a row menu acts on. */
    private List<MenuItem> menu(String prefix,String suffix,List<FileEntry> target) {
        var items=new ArrayList<MenuItem>();
        if(editing!=null) { items.add(item(prefix,suffix,"open","Open in Editor","file",target));items.add(separator(0)); }
        items.add(item(prefix,suffix,"up","Up","chevron.up",target));
        items.add(item(prefix,suffix,"new-folder","New folder…","folder.add",target));
        items.add(separator(1));
        items.add(item(prefix,suffix,"upload-folder","Upload folder…","upload",target));
        items.add(item(prefix,suffix,"download","Download selected…","download",target));
        items.add(item(prefix,suffix,"copy-host","Copy to host…","server",target));
        items.add(separator(2));
        items.add(item(prefix,suffix,"copy-paths","Copy paths","copy",target));
        items.add(item(prefix,suffix,"delete","Delete selected…","delete",target));
        return items;
    }

    private MenuItem item(String prefix,String suffix,String verb,String label,String icon,List<FileEntry> target) {
        return new MenuItem("menu."+verb,label,icon,prefix+verb+suffix,enabled(verb,target),false);
    }

    private static MenuItem separator(int index) { return new MenuItem("menu.separator."+index,"","","",false,true); }

    private Nodes.Node paging() {
        boolean ready=loaded && !working;
        String label=total==0?"Empty folder":(offset+1)+"–"+(offset+entries.size())+" of "+total;
        return new Nodes.Stack(PAGING,state(loaded && total>PAGE_SIZE,true),Nodes.LayoutRole.INLINE,List.of(
            new Nodes.Text(PAGE,ControlState.READY,label,Nodes.TextRole.CAPTION),
            tool(PREVIOUS,"back","Previous page",ready && offset>0),
            tool(NEXT,"forward","Next page",ready && offset+entries.size()<total)));
    }

    /** What is happening or went wrong, wrapping at the panel's width; Cancel while something runs. */
    private Nodes.Node status() {
        return new Nodes.Stack(STATUS,state(working || !message.isEmpty(),true),Nodes.LayoutRole.VERTICAL,List.of(
            new Nodes.Text(MESSAGE,state(!message.isEmpty(),true),message,Nodes.TextRole.DESCRIPTION),
            new Nodes.Button(CANCEL,state(working,true),"Cancel","close",Nodes.ButtonKind.SECONDARY,CANCEL,"")));
    }

    private Nodes.Node resultLine() { return new Nodes.Text(RESULT,state(!result.isEmpty(),true),result,Nodes.TextRole.DESCRIPTION); }

    static String formatSize(FileEntry entry) { if(entry.kind()==FileEntry.Kind.DIRECTORY) return "—";long value=entry.size();if(value<0) return "—";if(value<1000) return value+" B";if(value<1000*1000) return String.format(Locale.ROOT,"%.1f KB",value/1000.0);if(value<1000L*1000*1000) return String.format(Locale.ROOT,"%.1f MB",value/(1000.0*1000));return String.format(Locale.ROOT,"%.1f GB",value/(1000.0*1000*1000)); }
    static String modified(FileEntry entry) { return entry.modifiedMillis()<0?"":DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.modifiedMillis())); }
}
