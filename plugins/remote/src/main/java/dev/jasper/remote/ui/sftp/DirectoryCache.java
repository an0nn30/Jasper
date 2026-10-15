package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.sftp.FileEntry;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Disposable worker-owned directory spool, independent of the durable transfer database. Pages and counts can leave out
 * hidden items, the names starting with a dot.
 */
public final class DirectoryCache implements AutoCloseable {
    private final Path path;
    private Connection db;
    private long count,directories,hidden,hiddenDirectories;
    public DirectoryCache(Path directory) throws IOException {
        Files.createDirectories(directory);path=Files.createTempFile(directory,"browser-",".sqlite");
        try {
            var driver=new org.sqlite.JDBC();
            try { db=driver.connect("jdbc:sqlite:"+path,new Properties()); }
            finally { for(var registered:Collections.list(DriverManager.getDrivers())) if(registered instanceof org.sqlite.JDBC && registered.getClass().getClassLoader()==getClass().getClassLoader()) DriverManager.deregisterDriver(registered); }
            try(var s=db.createStatement()) {
                s.execute("PRAGMA journal_mode=OFF");s.execute("PRAGMA synchronous=OFF");s.execute("PRAGMA cache_size=-2048");s.execute("PRAGMA temp_store=FILE");
                s.execute("CREATE TABLE files(name TEXT PRIMARY KEY,rank INTEGER NOT NULL,kind TEXT NOT NULL,size INTEGER NOT NULL,modified INTEGER NOT NULL,permissions INTEGER NOT NULL,target TEXT NOT NULL,file_key TEXT NOT NULL)");
                s.execute("CREATE INDEX listing ON files(rank,name COLLATE NOCASE,name)");
            }
        } catch(SQLException failure) { try { close(); } catch(IOException ignored) { failure.addSuppressed(ignored); }throw new IOException("Cannot cache directory",failure); }
    }
    public Path path() { return path; }
    public long count() { return count; }
    public void append(List<FileEntry> entries) throws IOException {
        if(entries.size()>256) throw new IllegalArgumentException("Directory batch exceeds 256");
        try {
            db.setAutoCommit(false);
            try(var statement=db.prepareStatement("INSERT INTO files VALUES(?,?,?,?,?,?,?,?)")) {
                for(var entry:entries) {
                    statement.setString(1,entry.name());statement.setInt(2,entry.kind()==FileEntry.Kind.DIRECTORY?0:1);statement.setString(3,entry.kind().name());statement.setLong(4,entry.size());statement.setLong(5,entry.modifiedMillis());statement.setInt(6,entry.permissions());statement.setString(7,entry.linkTarget());statement.setString(8,entry.fileKey());statement.addBatch();
                }
                statement.executeBatch();db.commit();count+=entries.size();directories+=entries.stream().filter(DirectoryCache::folder).count();
                hidden+=entries.stream().filter(DirectoryCache::hidden).count();hiddenDirectories+=entries.stream().filter(e->folder(e) && hidden(e)).count();
            } catch(SQLException failure) { db.rollback();throw failure; }
            finally { db.setAutoCommit(true); }
        } catch(SQLException failure) { throw new IOException("Cannot cache directory listing",failure); }
    }
    private static boolean folder(FileEntry entry) { return entry.kind()==FileEntry.Kind.DIRECTORY || entry.kind()==FileEntry.Kind.LINK; }
    private static boolean hidden(FileEntry entry) { return entry.name().startsWith("."); }
    public long count(boolean foldersOnly) { return count(foldersOnly,true); }
    /** The entries a page can show: folders (and links) only or every entry, with or without the hidden ones. */
    public long count(boolean foldersOnly,boolean showHidden) {
        return foldersOnly?directories-(showHidden?0:hiddenDirectories):count-(showHidden?0:hidden);
    }
    public List<FileEntry> page(long offset,int limit) throws IOException { return page(offset,limit,false); }
    public List<FileEntry> page(long offset,int limit,boolean foldersOnly) throws IOException { return page(offset,limit,foldersOnly,true); }
    public List<FileEntry> page(long offset,int limit,boolean foldersOnly,boolean showHidden) throws IOException {
        if(offset<0 || limit<1 || limit>200) throw new IllegalArgumentException("Directory page must contain 1..200 rows");
        var where=new ArrayList<String>();
        if(foldersOnly) where.add("kind IN ('DIRECTORY','LINK')");
        if(!showHidden) where.add("substr(name,1,1)<>'.'");
        try(var statement=db.prepareStatement("SELECT * FROM files "+(where.isEmpty()?"":"WHERE "+String.join(" AND ",where)+" ")
                +"ORDER BY rank,name COLLATE NOCASE,name LIMIT ? OFFSET ?")) {
            statement.setInt(1,limit);statement.setLong(2,offset);var result=new ArrayList<FileEntry>();
            try(var rows=statement.executeQuery()) { while(rows.next()) result.add(new FileEntry(rows.getString("name"),FileEntry.Kind.valueOf(rows.getString("kind")),rows.getLong("size"),rows.getLong("modified"),rows.getInt("permissions"),rows.getString("target"),rows.getString("file_key"))); }
            return List.copyOf(result);
        } catch(SQLException failure) { throw new IOException("Cannot read directory listing",failure); }
    }
    @Override public void close() throws IOException {
        try { if(db!=null) db.close(); } catch(SQLException failure) { throw new IOException("Cannot close directory cache",failure); }
        finally { db=null;Files.deleteIfExists(path); }
    }
}
