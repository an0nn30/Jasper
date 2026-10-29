package dev.jasper.remote.ui.sftp;

import dev.jasper.sdk.ui.Column;
import dev.jasper.sdk.ui.ColumnState;
import dev.jasper.sdk.ui.ColumnStateFile;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The sort and column widths every SFTP browser shares. The sort (column and direction) is one for all tables; widths
 * are kept per column set: {@link #NARROW} (the docked table and Copy to host's browser), {@link #WIDE}, and the classic
 * look's {@link #CLASSIC} and {@link #CLASSIC_WIDE}. A file keeps each set under its key, the sort written into every
 * key and read back from {@link #NARROW}'s. Name ascending until the user picks a column. UI thread only.
 */
final class SftpColumns {
    static final String NARROW="sftp",WIDE="sftp-wide",CLASSIC="sftp-classic",CLASSIC_WIDE="sftp-classic-wide";
    private static final List<String> KEYS=List.of(NARROW,WIDE,CLASSIC,CLASSIC_WIDE);
    private final Optional<ColumnStateFile> file;
    private final Consumer<IOException> failed;
    private final Map<String,ColumnState> states=new HashMap<>();
    private final List<Runnable> sortListeners=new CopyOnWriteArrayList<>();
    private ColumnState sort;

    /** Reads every key from {@code file}; a write that fails is reported to {@code failed}. */
    SftpColumns(ColumnStateFile file,Consumer<IOException> failed) {
        this.file=Optional.of(file);this.failed=failed;
        for(String key:KEYS) states.put(key,file.get(key));
        var stored=states.get(NARROW);
        sort=stored.sortColumn().isPresent()?ColumnState.sortedBy(stored.sortColumn().get(),stored.ascending()):ColumnState.sortedBy(DirectoryCache.Order.DEFAULT.column(),true);
    }

    private SftpColumns() { file=Optional.empty();failed=failure->{};sort=ColumnState.sortedBy(DirectoryCache.Order.DEFAULT.column(),true); }

    /** Kept only in memory, for a browser whose owner keeps no file. */
    static SftpColumns memory() { return new SftpColumns(); }

    /** The order every browser's pages take. */
    DirectoryCache.Order order() { return new DirectoryCache.Order(sort.sortColumn().orElseThrow(),sort.ascending()); }

    /** {@code columns} marked with the shared sort and {@code key}'s widths. */
    List<Column> apply(String key,List<Column> columns) {
        var widths=states.getOrDefault(key,ColumnState.NONE).widths();
        return sort.resized(widths).apply(columns);
    }

    /** A header click on {@code columnId}: the same column flips, another sorts ascending; every listener hears of it. */
    void sorted(String columnId) {
        sort=sort.sorted(columnId);
        for(String key:KEYS) states.put(key,new ColumnState(sort.sortColumn(),sort.ascending(),states.getOrDefault(key,ColumnState.NONE).widths()));
        save(KEYS);
        for(var listener:sortListeners) listener.run();
    }

    /** The user finished dragging a boundary in {@code key}'s column set. */
    void resized(String key,Map<String,Integer> widths) {
        states.put(key,new ColumnState(sort.sortColumn(),sort.ascending(),widths));
        save(List.of(key));
    }

    /** Runs {@code listener} after each sort change; the returned runnable stops that. */
    Runnable onSorted(Runnable listener) { sortListeners.add(listener);return ()->sortListeners.remove(listener); }

    private void save(List<String> keys) {
        if(file.isEmpty()) return;
        try { for(String key:keys) file.get().put(key,states.get(key)); }
        catch(IOException failure) { failed.accept(failure); }
    }
}
