package dev.jasper.app.themesui;

import dev.jasper.app.appearance.*;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.themeimport.*;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javax.swing.SwingUtilities;

/** Page-owned serial file actions. Confirmed running writes may finish after disposal, but never reopen UI. */
public final class ThemeActions implements AutoCloseable {
    private final PageContext context;
    private final Path home;
    private final ThemeFiles files;
    private final ThemeImporter importer;
    private final ThemeDialogs dialogs;
    private final Supplier<CompletableFuture<Void>> refresh;
    private final Consumer<Path> opener;
    private final Predicate<Path> trash;
    private final ExecutorService worker;
    private final List<Future<?>> jobs=new ArrayList<>();
    private volatile boolean closed;
    private volatile long generation;
    private boolean busy;
    Runnable changed = () -> {};
    public ThemeActions(PageContext context,Path home,AuxiliaryWindows windows,Supplier<CompletableFuture<Void>> refresh,
                        Consumer<Path> openFolder,Predicate<Path> trash,ExecutorService worker){
        this.context=context;this.home=home;this.files=new ThemeFiles(home);this.importer=new ThemeImporter(home);
        this.dialogs=new ThemeDialogs(windows,context.surface());this.refresh=refresh;this.opener=openFolder;this.trash=trash;this.worker=worker;
    }
    public boolean busy(){return busy;}
    private boolean begin(){if(closed||busy||!dialogs.available())return false;generation++;busy=true;changed.run();context.changed().run();return true;}
    private void done(){if(closed)return;busy=false;changed.run();context.changed().run();}
    private void error(Throwable failure){done();if(!closed)dialogs.message("Theme operation failed",Objects.toString(failure.getMessage(),failure.toString()));}
    private <T> void work(Callable<T> operation,Consumer<T> success,boolean writes){
        long ticket=generation;
        try { jobs.removeIf(Future::isDone);jobs.add(worker.submit(()->{
            if(closed||ticket!=generation)return;
            T value=null;Throwable failure=null;
            try{value=operation.call();}catch(Exception e){failure=e;}
            final T result=value;final Throwable problem=failure;
            CompletableFuture<Void> reloaded=CompletableFuture.completedFuture(null);
            if(writes)try{reloaded=refresh.get();}catch(Exception e){reloaded=CompletableFuture.failedFuture(e);}
            reloaded.whenComplete((ignored,reloadFailure)->SwingUtilities.invokeLater(()->{
                if(closed||ticket!=generation)return;
                if(problem!=null)error(problem);else if(reloadFailure!=null)error(new IllegalStateException("File operation finished, but reload failed: "+reloadFailure.getMessage(),reloadFailure));else success.accept(result);
            }));
        }));}catch(RejectedExecutionException failure){error(failure);}
    }
    /** Read-only eligibility; the destructive action still captures and validates a fresh intent. */
    void checkRemoval(Theme theme, Consumer<Boolean> result) {
        if (closed) return;
        if (!(theme.source() instanceof Theme.UserFile user)) { result.accept(false); return; }
        jobs.removeIf(Future::isDone);
        try { jobs.add(worker.submit(() -> {
            if (closed) return;
            boolean valid;
            try { files.prepareRemoval(user.file()); valid = true; } catch (Exception failure) { valid = false; }
            boolean eligible = valid;
            SwingUtilities.invokeLater(() -> { if (!closed) result.accept(eligible); });
        })); } catch (RejectedExecutionException failure) { if (!closed) result.accept(false); }
    }
    public void importThemes(Consumer<ThemeImporter.ImportReport> report){
        if(!begin())return; long ticket=generation;
        Optional<Path> chosen;
        try{chosen=context.chooseFile().apply("Import theme (.theme.json, .jar, .zip, .toml)",Optional.empty());}catch(Exception failure){error(failure);return;}
        if(closed||ticket!=generation)return;if(chosen.isEmpty()){done();return;}
        String suffix=chosen.get().getFileName().toString().toLowerCase(Locale.ROOT);
        if(!(suffix.endsWith(".theme.json")||suffix.endsWith(".jar")||suffix.endsWith(".zip")||suffix.endsWith(".toml"))){error(new IllegalArgumentException("Choose a .theme.json, .jar, .zip or .toml file."));return;}
        work(()->importer.plan(chosen.get()),plan->dialogs.view("Import themes",(accept,cancel)->new ThemeImportView(plan,choices->{
            accept.run();if(closed||ticket!=generation)return;
            work(()->importer.commit(plan,choices),result->{done();report.accept(result);},true);
        },cancel),this::done),false);
    }
    public void duplicate(Theme original,GeneratedTheme effective,Consumer<String> created){
        if(!begin())return;long ticket=generation;
        work(()->ThemeCatalog.beside(home.resolve("config.toml")).scan(),catalog->{
            String stem=original.name()+" Copy",candidate=stem;int number=2;while(catalog.find(candidate).isPresent())candidate=stem+" "+number++;
            dialogs.name(candidate,name->!name.isBlank()&&!name.equalsIgnoreCase("match")&&catalog.find(name).isEmpty(),name->{
                if(closed||ticket!=generation)return;work(()->{files.duplicate(original,effective,name);return name;},value->{done();created.accept(value);},true);
            },this::done);
        },false);
    }
    public void remove(Theme original,Consumer<String> result){
        if(!(original.source() instanceof Theme.UserFile user)||!begin())return;long ticket=generation;
        work(()->files.prepareRemoval(user.file()),intent->dialogs.confirm("Remove theme","Move "+original.name()+" to Trash? Saved theme choices will be retained.","Move to Trash",()->{
            if(closed||ticket!=generation)return;
            work(()->{try{return files.trash(intent,trash)?null:"Trash is unavailable or did not remove the file.";}catch(Exception failure){return failure.getMessage();}},failure->{
                if(failure==null){done();result.accept("Moved "+original.name()+" to Trash.");return;}
                dialogs.confirm("Delete theme permanently",failure+"\nPermanently delete "+original.name()+"? This cannot be undone.","Delete permanently",()->{
                    if(closed||ticket!=generation)return;work(()->{files.delete(intent);return "Deleted "+original.name()+".";},message->{done();result.accept(message);},true);
                },this::done);
            },true);
        },this::done),false);
    }
    public void openFolder(Consumer<String> failure){if(!begin())return;work(()->{try{opener.accept(files.ensureDirectory());return "";}catch(Exception e){return Objects.toString(e.getMessage(),e.toString());}},message->{done();failure.accept(message);},false);}
    public void about(){if(!begin())return;work(ThemeNotices::read,text->{done();dialogs.message("About bundled themes",text);},false);}
    @Override public void close(){if(closed)return;closed=true;generation++;for(var job:jobs)job.cancel(false);dialogs.close();worker.shutdown();busy=false;changed=()->{};}
}
