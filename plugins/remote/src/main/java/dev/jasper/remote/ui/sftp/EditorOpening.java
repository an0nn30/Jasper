package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.edit.RemoteEditing;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.FilePaths;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Opening what an SFTP browser shows in the Editor, shared by the SFTP panel and Open Remote File. UI thread only. */
final class EditorOpening {
    /**
     * The browser the files came from.
     *
     * @param ui the UI thread
     * @param live whether the browser and its window are still open
     * @param showing whether the browser still shows the connection and folder a capture was taken from
     * @param error shows an error in the browser
     * @param navigate enters a folder in the browser
     * @param opened runs after files were handed to the Editor
     */
    record Browser(Executor ui,BooleanSupplier live,Predicate<SftpController.Capture> showing,Consumer<String> error,Consumer<String> navigate,Runnable opened) {}
    private EditorOpening() {}
    /** Double-click or Enter on one file or link: a file opens in the Editor; a link opens when it leads to a file and navigates when it leads to a folder. */
    static void activate(RemoteEditing editing,Browser browser,SftpController.Capture capture,FileEntry entry,WindowHandle owner) {
        String path;try { path=FilePaths.child(capture.directory(),entry.name()); } catch(IOException invalid) { browser.error().accept(invalid.getMessage());return; }
        if(entry.kind()==FileEntry.Kind.FILE) { editing.open(capture.identity(),path,owner);browser.opened().run();return; }
        editing.kinds(capture.identity(),List.of(path),owner).whenComplete((kinds,failure)->browser.ui().execute(()-> {
            if(!browser.live().getAsBoolean()) return;
            if(failure!=null) { if(browser.showing().test(capture)) browser.error().accept(message(failure));return; }
            var kind=kinds.getFirst();
            if(kind.isEmpty()) { if(browser.showing().test(capture)) browser.error().accept(entry.name()+": its target does not exist"); }
            else if(kind.orElseThrow()==FileEntry.Kind.FILE) { editing.open(capture.identity(),path,owner);browser.opened().run(); }
            else if(kind.orElseThrow()==FileEntry.Kind.DIRECTORY) { if(browser.showing().test(capture)) browser.navigate().accept(path); }
            else if(browser.showing().test(capture)) browser.error().accept(entry.name()+" is not a regular file");
        }));
    }
    /** Open: the selected files and links to files, at most {@link RemoteEditing#MAX_OPEN}. A failed link resolution still opens the selection's regular files. */
    static void openSelected(RemoteEditing editing,Browser browser,SftpController.Capture capture,WindowHandle owner) {
        var candidates=capture.selection().stream().filter(entry->entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK).toList();if(candidates.isEmpty()) return;
        var paths=new ArrayList<String>();var links=new ArrayList<String>();
        try { for(var entry:candidates) { String path=FilePaths.child(capture.directory(),entry.name());paths.add(path);if(entry.kind()==FileEntry.Kind.LINK) links.add(path); } }
        catch(IOException invalid) { browser.error().accept(invalid.getMessage());return; }
        CompletableFuture<List<Optional<FileEntry.Kind>>> resolved=links.isEmpty()?CompletableFuture.completedFuture(List.of()):editing.kinds(capture.identity(),links,owner);
        resolved.whenComplete((kinds,failure)->browser.ui().execute(()-> {
            if(!browser.live().getAsBoolean()) return;
            var files=new ArrayList<String>();
            if(failure==null) {
                int link=0;
                for(int i=0;i<candidates.size();i++) {
                    if(candidates.get(i).kind()==FileEntry.Kind.FILE) files.add(paths.get(i));
                    else if(kinds.get(link++).filter(kind->kind==FileEntry.Kind.FILE).isPresent()) files.add(paths.get(i));
                }
            } else for(int i=0;i<candidates.size();i++) if(candidates.get(i).kind()==FileEntry.Kind.FILE) files.add(paths.get(i));
            for(String path:files.subList(0,Math.min(files.size(),RemoteEditing.MAX_OPEN))) editing.open(capture.identity(),path,owner);
            if(!files.isEmpty()) browser.opened().run();
            if(!browser.showing().test(capture)) return;
            if(failure!=null) browser.error().accept(message(failure));
            else if(files.size()>RemoteEditing.MAX_OPEN) browser.error().accept("Opened "+RemoteEditing.MAX_OPEN+" of "+files.size()+" files.");
        }));
    }
    static String message(Throwable failure) { while((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause()!=null) failure=failure.getCause();return failure.getMessage()==null?"SFTP operation failed":failure.getMessage(); }
}
