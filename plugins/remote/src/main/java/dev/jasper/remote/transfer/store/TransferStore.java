package dev.jasper.remote.transfer.store;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.transfer.*;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Worker-owned, transactional durable queue. Every collection read has an explicit bound. */
public final class TransferStore implements AutoCloseable {
    public record Discovered(String relative, String source, String target, FileEntry sourceInfo) {}
    public record Checkpoint(long start, long length, String digest) {}
    public record Frontier(long id, String source, String target, String relative) {}
    public record Cleanup(long entryId, String path, String reason) {}
    private Connection db;
    private FileChannel lockChannel;
    private FileLock lock;
    public TransferStore(Path directory) throws IOException {
        try {
            Files.createDirectories(directory);
            try { Files.setPosixFilePermissions(directory, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")); }
            catch (UnsupportedOperationException ignored) { }
            lockChannel=FileChannel.open(directory.resolve("queue.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            try { lock=lockChannel.tryLock(); } catch (OverlappingFileLockException busy) { throw new IOException("Transfers are managed by another Jasper instance",busy); }
            if(lock==null) throw new IOException("Transfers are managed by another Jasper instance");
            var driver=new org.sqlite.JDBC();
            try { db=driver.connect("jdbc:sqlite:"+directory.resolve("queue.sqlite").toAbsolutePath(),new Properties()); }
            finally {
                for (var registered:Collections.list(DriverManager.getDrivers()))
                    if(registered.getClass().getClassLoader()==org.sqlite.JDBC.class.getClassLoader() && registered instanceof org.sqlite.JDBC)
                        DriverManager.deregisterDriver(registered);
            }
            try(var statement=db.createStatement();var result=statement.executeQuery("PRAGMA quick_check")) {
                if(!result.next() || !"ok".equals(result.getString(1))) throw new IOException("Transfer queue integrity check failed; original database retained");
            }
            int version;
            try(var statement=db.createStatement();var result=statement.executeQuery("PRAGMA user_version")) { version=result.getInt(1); }
            if(version!=0 && version!=1) throw new IOException("Unsupported transfer queue schema "+version+"; original database retained");
            execute("PRAGMA journal_mode=WAL"); execute("PRAGMA synchronous=FULL"); execute("PRAGMA foreign_keys=ON");
            execute("PRAGMA busy_timeout=2000"); execute("PRAGMA cache_size=-4096"); execute("PRAGMA wal_autocheckpoint=1000");
            if(version==0) schema();
            update("UPDATE jobs SET state=CASE WHEN intent='CANCEL' THEN 'CANCELLED' ELSE 'PAUSED' END, intent=CASE WHEN intent='CANCEL' THEN 'CANCEL' ELSE 'PAUSE' END WHERE state NOT IN ('COMPLETED','COMPLETED_WITH_ISSUES','CANCELLED')");
            update("INSERT OR IGNORE INTO cleanup(entry,reason) SELECT e.id,'Cancelled before cleanup completed' FROM entries e JOIN jobs j ON j.id=e.job WHERE j.intent='CANCEL' AND e.temp!='' AND e.outcome!='COMPLETE'");
        } catch (SQLException | IOException | RuntimeException failure) {
            try { close(); } catch(IOException close) { failure.addSuppressed(close); }
            if(failure instanceof IOException io) throw io;
            throw new IOException("Cannot open transfer queue; original database retained",failure);
        }
    }
    private void schema() throws SQLException {
        db.setAutoCommit(false);
        try {
            execute("CREATE TABLE jobs(id TEXT PRIMARY KEY,request TEXT NOT NULL,source TEXT NOT NULL,destination TEXT NOT NULL,state TEXT NOT NULL,intent TEXT NOT NULL,created INTEGER NOT NULL,total_entries INTEGER NOT NULL DEFAULT 0,total_bytes INTEGER NOT NULL DEFAULT 0,confirmed_bytes INTEGER NOT NULL DEFAULT 0,completed INTEGER NOT NULL DEFAULT 0,skipped INTEGER NOT NULL DEFAULT 0,failed INTEGER NOT NULL DEFAULT 0,scanned INTEGER NOT NULL DEFAULT 0,detail TEXT NOT NULL DEFAULT '')");
            execute("CREATE TABLE entries(id INTEGER PRIMARY KEY,job TEXT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,relative TEXT NOT NULL,source TEXT NOT NULL,target TEXT NOT NULL,info TEXT NOT NULL,temp TEXT NOT NULL DEFAULT '',temp_info TEXT,phase TEXT NOT NULL DEFAULT 'PENDING',confirmed INTEGER NOT NULL DEFAULT 0,digest TEXT NOT NULL DEFAULT '',publication TEXT NOT NULL DEFAULT 'NONE',expected TEXT,decision TEXT NOT NULL DEFAULT 'ASK',outcome TEXT NOT NULL DEFAULT 'PENDING',error TEXT NOT NULL DEFAULT '',UNIQUE(job,relative))");
            execute("CREATE INDEX entries_work ON entries(job,outcome,id)");
            execute("CREATE TABLE checkpoints(entry INTEGER NOT NULL REFERENCES entries(id) ON DELETE CASCADE,start INTEGER NOT NULL,length INTEGER NOT NULL,digest TEXT NOT NULL,PRIMARY KEY(entry,start))");
            execute("CREATE TABLE scan_frontier(id INTEGER PRIMARY KEY,job TEXT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,source TEXT NOT NULL,target TEXT NOT NULL,relative TEXT NOT NULL,UNIQUE(job,relative))");
            execute("CREATE INDEX frontier_jobs ON scan_frontier(job,id)");
            execute("CREATE TABLE cleanup(entry INTEGER PRIMARY KEY REFERENCES entries(id) ON DELETE CASCADE,reason TEXT NOT NULL)");
            execute("PRAGMA user_version=1"); db.commit();
        } catch(SQLException failure) { db.rollback(); throw failure; }
        finally { db.setAutoCommit(true); }
    }
    private void execute(String sql) throws SQLException { try(var statement=db.createStatement()) { statement.execute(sql); } }
    private PreparedStatement prepare(String sql,Object... args) throws SQLException {
        var statement=db.prepareStatement(sql);
        for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i] instanceof UUID ? args[i].toString() : args[i]);
        return statement;
    }
    private int update(String sql,Object... args) throws SQLException { try(var statement=prepare(sql,args)) { return statement.executeUpdate(); } }
    private static IOException failure(SQLException error) { return new IOException("Transfer queue operation failed",error); }
    private static void page(long offset,int limit) { if(offset<0 || limit<1 || limit>200) throw new IllegalArgumentException("Page must contain 1..200 entries"); }
    private void begin() throws SQLException { db.setAutoCommit(false); }
    private void finish() throws SQLException { db.commit(); db.setAutoCommit(true); }
    private void rollback() throws IOException { try { db.rollback(); db.setAutoCommit(true); } catch(SQLException e) { throw failure(e); } }
    public synchronized UUID create(TransferRequest request) throws IOException {
        var id=UUID.randomUUID(); String encoded=TransferCodec.request(request);
        try { update("INSERT INTO jobs(id,request,source,destination,state,intent,created) VALUES(?,?,?,?,?,?,?)",id,encoded,request.source().label(),request.destination().label(),"QUEUED","RUN",System.currentTimeMillis()); return id; }
        catch(SQLException e) { throw failure(e); }
    }
    public synchronized TransferRequest request(UUID job) throws IOException {
        try(var statement=prepare("SELECT request FROM jobs WHERE id=?",job);var result=statement.executeQuery()) {
            if(!result.next()) throw new IOException("Transfer no longer exists"); return TransferCodec.request(result.getString(1));
        } catch(SQLException e) { throw failure(e); }
    }
    private TransferJob readJob(ResultSet r) throws SQLException {
        return new TransferJob(UUID.fromString(r.getString("id")),r.getString("source"),r.getString("destination"),TransferState.valueOf(r.getString("state")),TransferJob.Intent.valueOf(r.getString("intent")),r.getLong("created"),r.getLong("total_entries"),r.getLong("total_bytes"),r.getLong("confirmed_bytes"),r.getLong("completed"),r.getLong("skipped"),r.getLong("failed"),r.getBoolean("scanned"),r.getString("detail"),r.getLong("cleanup_count"));
    }
    private static final String JOBS="SELECT jobs.*, (SELECT count(*) FROM cleanup c JOIN entries e ON e.id=c.entry WHERE e.job=jobs.id) AS cleanup_count FROM jobs";
    public synchronized TransferJob job(UUID id) throws IOException {
        try(var statement=prepare(JOBS+" WHERE id=?",id);var result=statement.executeQuery()) {
            if(!result.next()) throw new IOException("Transfer no longer exists"); return readJob(result);
        } catch(SQLException e) { throw failure(e); }
    }
    public synchronized List<TransferJob> jobs(long offset,int limit) throws IOException {
        page(offset,limit); var values=new ArrayList<TransferJob>();
        try(var statement=prepare(JOBS+" ORDER BY created DESC,id LIMIT ? OFFSET ?",limit,offset);var result=statement.executeQuery()) { while(result.next()) values.add(readJob(result)); return List.copyOf(values); }
        catch(SQLException e) { throw failure(e); }
    }
    public synchronized void state(UUID id,TransferState state,String detail) throws IOException {
        try { update("UPDATE jobs SET state=?,detail=? WHERE id=?",state.name(),detail,id); } catch(SQLException e) { throw failure(e); }
    }
    public void markRunning(UUID id) throws IOException { state(id,TransferState.RUNNING,""); }
    public synchronized void intent(UUID id,TransferJob.Intent intent) throws IOException {
        try { update("UPDATE jobs SET intent=? WHERE id=? AND intent!='CANCEL'",intent.name(),id); } catch(SQLException e) { throw failure(e); }
    }
    public synchronized void scanned(UUID id) throws IOException { try { update("UPDATE jobs SET scanned=1 WHERE id=?",id); } catch(SQLException e) { throw failure(e); } }
    public void discover(UUID job,List<Discovered> entries) throws IOException { discover(job,entries,false); }
    public synchronized void discover(UUID job,List<Discovered> entries,boolean queueDirectories) throws IOException {
        if(entries.size()>256) throw new IllegalArgumentException("Discovery batch exceeds 256 entries");
        try {
            begin(); long count=0,bytes=0;
            for(var entry:entries) {
                try(var statement=prepare("SELECT source FROM entries WHERE job=? AND relative=?",job,entry.relative());var prior=statement.executeQuery()) {
                    if(prior.next() && !prior.getString(1).equals(entry.source())) throw new IOException("Selected sources have the same destination name: "+entry.relative());
                }
                if(update("INSERT OR IGNORE INTO entries(job,relative,source,target,info) VALUES(?,?,?,?,?)",job,entry.relative(),entry.source(),entry.target(),TransferCodec.file(entry.sourceInfo()))!=0) {
                count++; if(entry.sourceInfo().kind()==FileEntry.Kind.FILE) bytes=Math.addExact(bytes,Math.max(0,entry.sourceInfo().size()));
            }
                if(queueDirectories && entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY)
                    update("INSERT OR IGNORE INTO scan_frontier(job,source,target,relative) VALUES(?,?,?,?)",job,entry.source(),entry.target(),entry.relative());
            }
            update("UPDATE jobs SET total_entries=total_entries+?,total_bytes=total_bytes+? WHERE id=?",count,bytes,job); finish();
        } catch(SQLException | IOException | RuntimeException e) { rollback(); if(e instanceof IOException io) throw io; if(e instanceof SQLException sql) throw failure(sql); throw (RuntimeException)e; }
    }
    private TransferEntry readEntry(ResultSet r) throws SQLException,IOException {
        String temp=r.getString("temp_info"), expected=r.getString("expected");
        return new TransferEntry(r.getLong("id"),UUID.fromString(r.getString("job")),r.getString("relative"),r.getString("source"),r.getString("target"),TransferCodec.file(r.getString("info")),r.getString("temp"),temp==null?Optional.empty():Optional.of(TransferCodec.file(temp)),TransferEntry.Phase.valueOf(r.getString("phase")),r.getLong("confirmed"),r.getString("digest"),TransferEntry.Publication.valueOf(r.getString("publication")),expected==null?Optional.empty():Optional.of(TransferCodec.file(expected)),ConflictDecision.valueOf(r.getString("decision")),TransferEntry.Outcome.valueOf(r.getString("outcome")),r.getString("error"));
    }
    public synchronized TransferEntry entry(long id) throws IOException {
        try(var statement=prepare("SELECT * FROM entries WHERE id=?",id);var result=statement.executeQuery()) { if(!result.next()) throw new IOException("Transfer entry no longer exists"); return readEntry(result); }
        catch(SQLException e) { throw failure(e); }
    }
    public synchronized List<TransferEntry> entries(UUID job,long offset,int limit) throws IOException {
        page(offset,limit); return entryQuery("SELECT * FROM entries WHERE job=? ORDER BY id LIMIT ? OFFSET ?",job,limit,offset);
    }
    public synchronized List<TransferEntry> pending(UUID job,int limit) throws IOException {
        page(0,limit); return entryQuery("SELECT * FROM entries WHERE job=? AND outcome='PENDING' AND error='' ORDER BY id LIMIT ?",job,limit);
    }
    private List<TransferEntry> entryQuery(String sql,Object... args) throws IOException {
        var values=new ArrayList<TransferEntry>();
        try(var statement=prepare(sql,args);var result=statement.executeQuery()) { while(result.next()) values.add(readEntry(result)); return List.copyOf(values); }
        catch(SQLException e) { throw failure(e); }
    }
    public synchronized void addFrontier(UUID job,String source,String target,String relative) throws IOException {
        try { update("INSERT OR IGNORE INTO scan_frontier(job,source,target,relative) VALUES(?,?,?,?)",job,source,target,relative); } catch(SQLException e) { throw failure(e); }
    }
    public synchronized List<Frontier> frontier(UUID job,int limit) throws IOException {
        page(0,limit);var values=new ArrayList<Frontier>();
        try(var statement=prepare("SELECT * FROM scan_frontier WHERE job=? ORDER BY id LIMIT ?",job,limit);var result=statement.executeQuery()) { while(result.next()) values.add(new Frontier(result.getLong("id"),result.getString("source"),result.getString("target"),result.getString("relative")));return List.copyOf(values); }
        catch(SQLException e) { throw failure(e); }
    }
    public synchronized void finishFrontier(long id) throws IOException { try { update("DELETE FROM scan_frontier WHERE id=?",id); } catch(SQLException e) { throw failure(e); } }
    public synchronized void planTemporary(long id,String path) throws IOException {
        try { if(update("UPDATE entries SET temp=?,phase='PLANNED_TEMP' WHERE id=? AND phase='PENDING'",path,id)!=1) throw new IOException("Entry is not awaiting a temporary file"); } catch(SQLException e) { throw failure(e); }
    }
    public synchronized void created(long id,FileEntry proof) throws IOException {
        try { if(update("UPDATE entries SET temp_info=?,phase='CREATED_TEMP' WHERE id=? AND phase='PLANNED_TEMP'",TransferCodec.file(proof),id)!=1) throw new IOException("Temporary file creation was not planned"); } catch(SQLException e) { throw failure(e); }
    }
    public synchronized void checkpoint(long id,long start,long length,String digest,FileEntry proof) throws IOException {
        if(start<0 || length<=0 || length>8*1024*1024 || !digest.matches("[0-9a-f]{64}")) throw new IOException("Invalid checkpoint");
        try {
            begin(); var entry=entry(id);
            if(entry.confirmed()!=start || entry.temporaryInfo().isEmpty() || !(entry.phase()==TransferEntry.Phase.CREATED_TEMP || entry.phase()==TransferEntry.Phase.COPYING)) throw new IOException("Checkpoint does not extend the confirmed prefix");
            long end=Math.addExact(start,length); if(end>entry.sourceInfo().size()) throw new IOException("Checkpoint exceeds source length");
            update("INSERT INTO checkpoints VALUES(?,?,?,?)",id,start,length,digest);
            update("UPDATE entries SET confirmed=?,temp_info=?,phase='COPYING' WHERE id=?",end,TransferCodec.file(proof),id);
            update("UPDATE jobs SET confirmed_bytes=confirmed_bytes+? WHERE id=?",length,entry.jobId()); finish();
        } catch(SQLException | IOException | RuntimeException e) { rollback(); if(e instanceof IOException io) throw io; if(e instanceof SQLException sql) throw failure(sql); throw (RuntimeException)e; }
    }
    public synchronized List<Checkpoint> checkpoints(long id,long offset,int limit) throws IOException {
        page(offset,limit);var values=new ArrayList<Checkpoint>();
        try(var statement=prepare("SELECT * FROM checkpoints WHERE entry=? ORDER BY start LIMIT ? OFFSET ?",id,limit,offset);var result=statement.executeQuery()) { while(result.next()) values.add(new Checkpoint(result.getLong("start"),result.getLong("length"),result.getString("digest")));return List.copyOf(values); }
        catch(SQLException e) { throw failure(e); }
    }
    public synchronized void refreshTemporary(long id,FileEntry proof) throws IOException {
        try { update("UPDATE entries SET temp_info=? WHERE id=? AND temp_info IS NOT NULL",TransferCodec.file(proof),id); } catch(SQLException e) { throw failure(e); }
    }
    public synchronized void conflict(long id,Optional<FileEntry> target,String error) throws IOException {
        try { update("UPDATE entries SET expected=?,decision='ASK',error=? WHERE id=?",target.isPresent()?TransferCodec.file(target.orElseThrow()):null,error,id); } catch(SQLException e) { throw failure(e); }
    }
    public synchronized void reset(long id,FileEntry source) throws IOException {
        try {
            begin();var entry=entry(id);
            if(entry.outcome()!=TransferEntry.Outcome.PENDING) throw new IOException("Only unfinished entries can restart");
            update("DELETE FROM checkpoints WHERE entry=?",id);
            update("UPDATE entries SET info=?,temp='',temp_info=NULL,phase='PENDING',confirmed=0,digest='',publication='NONE',error='' WHERE id=?",TransferCodec.file(source),id);
            update("UPDATE jobs SET confirmed_bytes=confirmed_bytes-?,total_bytes=total_bytes+? WHERE id=?",entry.confirmed(),Math.max(0,source.size())-Math.max(0,entry.sourceInfo().size()),entry.jobId());finish();
        } catch(SQLException | IOException e) { rollback();if(e instanceof IOException io) throw io;throw failure((SQLException)e); }
    }
    public synchronized List<TransferEntry> directories(UUID job,long offset,int limit) throws IOException {
        page(offset,limit);
        // Directory entries are indexed through their job and walked in reverse discovery order.
        return entryQuery("SELECT * FROM entries WHERE job=? AND outcome='COMPLETE' ORDER BY id DESC LIMIT ? OFFSET ?",job,limit,offset);
    }
    public synchronized void publishing(long id,String digest,TransferEntry.Publication method,Optional<FileEntry> target) throws IOException {
        if(!digest.matches("[0-9a-f]{64}") || method==TransferEntry.Publication.NONE) throw new IOException("Invalid publication evidence");
        try { if(update("UPDATE entries SET digest=?,publication=?,expected=?,phase='PUBLISHING' WHERE id=? AND temp_info IS NOT NULL AND phase IN ('CREATED_TEMP','COPYING')",digest,method.name(),target.isPresent()?TransferCodec.file(target.orElseThrow()):null,id)!=1) throw new IOException("Entry is not ready for publication"); } catch(SQLException e) { throw failure(e); }
    }
    public synchronized void renameTree(long id,String target) throws IOException {
        try {
            begin();var entry=entry(id);String prefix=entry.relative()+"/";
            update("UPDATE entries SET target=? || substr(target,?),expected=NULL,decision='RENAME' WHERE job=? AND (id=? OR substr(relative,1,?)=?)",target,entry.target().length()+1,entry.jobId(),id,prefix.length(),prefix);
            finish();
        } catch(SQLException | IOException e) { rollback();if(e instanceof IOException io) throw io;throw failure((SQLException)e); }
    }
    public synchronized void skipTree(long id) throws IOException {
        try {
            begin();var entry=entry(id);String prefix=entry.relative()+"/";
            int count=update("UPDATE entries SET outcome='SKIPPED',decision='SKIP',error='Skipped' WHERE job=? AND outcome='PENDING' AND (id=? OR substr(relative,1,?)=?)",entry.jobId(),id,prefix.length(),prefix);
            update("UPDATE jobs SET skipped=skipped+? WHERE id=?",count,entry.jobId());finish();
        } catch(SQLException | IOException e) { rollback();if(e instanceof IOException io) throw io;throw failure((SQLException)e); }
    }
    public synchronized void attention(long id,String message) throws IOException {
        try {
            begin();var entry=entry(id);update("UPDATE entries SET error=? WHERE id=?",message,id);
            if(entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY) {
                String prefix=entry.relative()+"/";
                update("UPDATE entries SET error='Waiting for parent decision' WHERE job=? AND outcome='PENDING' AND error='' AND substr(relative,1,?)=?",entry.jobId(),prefix.length(),prefix);
            }
            finish();
        } catch(SQLException | IOException e) { rollback();if(e instanceof IOException io) throw io;throw failure((SQLException)e); }
    }
    private void unblockChildren(TransferEntry entry) throws SQLException {
        String prefix=entry.relative()+"/";
        update("UPDATE entries SET error='' WHERE job=? AND error='Waiting for parent decision' AND substr(relative,1,?)=?",entry.jobId(),prefix.length(),prefix);
    }
    public synchronized void decision(long id,ConflictDecision decision,String target) throws IOException {
        try {
            begin();var entry=entry(id);update("UPDATE entries SET decision=?,target=?,error='' WHERE id=? AND outcome='PENDING'",decision.name(),target,id);
            if(entry.sourceInfo().kind()==FileEntry.Kind.DIRECTORY) unblockChildren(entry);finish();
        } catch(SQLException | IOException e) { rollback();if(e instanceof IOException io) throw io;throw failure((SQLException)e); }
    }
    public synchronized void outcome(long id,TransferEntry.Outcome outcome,String error) throws IOException {
        if(outcome==TransferEntry.Outcome.PENDING) throw new IllegalArgumentException("Use reset for restart");
        try {
            begin(); var entry=entry(id);
            if(entry.outcome()==TransferEntry.Outcome.PENDING) {
                update("UPDATE entries SET outcome=?,phase=CASE WHEN ?='COMPLETE' THEN 'COMPLETE' ELSE phase END,error=? WHERE id=?",outcome.name(),outcome.name(),error,id);
                String column=switch(outcome) { case COMPLETE -> "completed"; case SKIPPED -> "skipped"; case FAILED -> "failed"; default -> throw new AssertionError(); };
                update("UPDATE jobs SET "+column+"="+column+"+1 WHERE id=?",entry.jobId());
            }
            finish();
        } catch(SQLException | IOException e) { rollback(); if(e instanceof IOException io) throw io; throw failure((SQLException)e); }
    }
    public synchronized void markCleanup(UUID job) throws IOException {
        try { update("INSERT OR IGNORE INTO cleanup(entry,reason) SELECT id,'Cancelled; partial cleanup pending' FROM entries WHERE job=? AND temp!='' AND outcome!='COMPLETE'",job); } catch(SQLException e) { throw failure(e); }
    }
    public synchronized void cleanup(long id,String reason) throws IOException {
        try { update("INSERT INTO cleanup(entry,reason) VALUES(?,?) ON CONFLICT(entry) DO UPDATE SET reason=excluded.reason",id,reason); } catch(SQLException e) { throw failure(e); }
    }
    public synchronized void cleaned(long id) throws IOException { try { update("DELETE FROM cleanup WHERE entry=?",id); } catch(SQLException e) { throw failure(e); } }
    public synchronized List<Cleanup> cleanup(UUID job,long offset,int limit) throws IOException {
        page(offset,limit);var values=new ArrayList<Cleanup>();
        try(var statement=prepare("SELECT c.entry,e.temp,c.reason FROM cleanup c JOIN entries e ON e.id=c.entry WHERE e.job=? ORDER BY c.entry LIMIT ? OFFSET ?",job,limit,offset);var result=statement.executeQuery()) { while(result.next()) values.add(new Cleanup(result.getLong(1),result.getString(2),result.getString(3))); return List.copyOf(values); }
        catch(SQLException e) { throw failure(e); }
    }
    public synchronized void clear(UUID id,boolean acknowledgeCleanup) throws IOException {
        var job=job(id); if(!job.state().terminal()) throw new IOException("Transfer is still active");
        if(job.cleanupPending()>0 && !acknowledgeCleanup) throw new IOException("Transfer has pending cleanup");
        try { update("DELETE FROM jobs WHERE id=?",id); } catch(SQLException e) { throw failure(e); }
    }
    @Override public synchronized void close() throws IOException {
        IOException problem=null;
        if(db!=null) { try { db.close(); } catch(SQLException e) { problem=failure(e); } finally { db=null; } }
        try { if(lock!=null) lock.release(); } catch(IOException e) { problem=e; } finally { lock=null; }
        try { if(lockChannel!=null) lockChannel.close(); } catch(IOException e) { problem=e; } finally { lockChannel=null; }
        if(problem!=null) throw problem;
    }
}
