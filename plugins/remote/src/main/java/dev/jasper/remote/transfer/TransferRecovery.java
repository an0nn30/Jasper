package dev.jasper.remote.transfer;

import dev.jasper.remote.sftp.*;
import dev.jasper.remote.transfer.store.TransferStore;
import java.io.*;
import java.nio.file.NoSuchFileException;
import java.security.*;
import java.util.*;

/** Conservative recovery: publication is reconciled before any truncation or cancellation cleanup. */
public final class TransferRecovery {
    private TransferRecovery() {}
    public static final class Attention extends IOException {
        private static final long serialVersionUID=1L;
        public Attention(String message) { super(message); }
    }
    public static Optional<FileEntry> optional(FileEndpoint endpoint,String path) throws IOException {
        try { return Optional.of(endpoint.stat(path)); }
        catch(NoSuchFileException absent) { return Optional.empty(); }
        catch(org.apache.sshd.sftp.common.SftpException absent) {
            if(absent.getStatus()==org.apache.sshd.sftp.common.SftpConstants.SSH_FX_NO_SUCH_FILE) return Optional.empty(); throw absent;
        }
    }
    public static boolean sameSource(FileEntry before,FileEntry now) {
        return before.kind()==now.kind() && before.size()==now.size() && before.modifiedMillis()==now.modifiedMillis()
            && before.linkTarget().equals(now.linkTarget()) && (before.fileKey().isEmpty() || before.fileKey().equals(now.fileKey()));
    }
    public static boolean owned(TransferEntry entry,FileEntry now) {
        if(entry.temporaryInfo().isEmpty()) return false;
        var proof=entry.temporaryInfo().orElseThrow();
        if(proof.kind()!=now.kind() || !proof.linkTarget().equals(now.linkTarget())) return false;
        if(!proof.fileKey().isEmpty()) return proof.fileKey().equals(now.fileKey());
        return proof.size()==now.size() && proof.modifiedMillis()==now.modifiedMillis();
    }
    public static MessageDigest sha() { try { return MessageDigest.getInstance("SHA-256"); } catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); } }
    public static String hex(MessageDigest digest) { return HexFormat.of().formatHex(digest.digest()); }
    public static String linkDigest(String target) { return HexFormat.of().formatHex(sha().digest(target.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
    public static String digest(FileEndpoint endpoint,String path,TransferControl control) throws IOException {
        var file=endpoint.stat(path);
        if(file.kind()==FileEntry.Kind.LINK) return linkDigest(file.linkTarget());
        if(file.kind()!=FileEntry.Kind.FILE) throw new Attention("Published target is no longer a file");
        var digest=sha();byte[] buffer=new byte[256*1024];long total=0;
        try(var in=endpoint.read(path,0)) { int count;while((count=in.read(buffer))!=-1) { control.check();digest.update(buffer,0,count);total+=count; } }
        if(total!=file.size() || !sameSource(file,endpoint.stat(path))) throw new Attention("File changed during verification");
        return hex(digest);
    }
    public static void validatePrefix(TransferStore store,TransferEntry entry,FileEndpoint source,FileEndpoint destination,TransferControl control,MessageDigest full) throws IOException {
        if(!sameSource(entry.sourceInfo(),source.stat(entry.source()))) throw new Attention("Source changed; choose Restart or Skip");
        var temporary=destination.stat(entry.temporary());
        if(!owned(entry,temporary) || temporary.size()<entry.confirmed()) throw new Attention("Partial file changed or ownership is uncertain; choose Restart or Skip");
        byte[] sourceBuffer=new byte[256*1024],targetBuffer=new byte[256*1024];long expected=0;
        try(var sourceStream=source.read(entry.source(),0);var targetStream=destination.read(entry.temporary(),0)) {
            long page=0;
            while(true) {
                var checkpoints=store.checkpoints(entry.id(),page,200);if(checkpoints.isEmpty()) break;
                for(var checkpoint:checkpoints) {
                    if(checkpoint.start()!=expected) throw new Attention("Stored checkpoint chain is incomplete");
                    var src=sha();var dst=sha();long remaining=checkpoint.length();
                    while(remaining>0) {
                        control.check();int count=(int)Math.min(sourceBuffer.length,remaining);
                        readFully(sourceStream,sourceBuffer,count);readFully(targetStream,targetBuffer,count);
                        src.update(sourceBuffer,0,count);dst.update(targetBuffer,0,count);full.update(sourceBuffer,0,count);remaining-=count;
                    }
                    if(!hex(src).equals(checkpoint.digest()) || !hex(dst).equals(checkpoint.digest())) throw new Attention("Source or partial content changed; choose Restart or Skip");
                    expected+=checkpoint.length();
                }
                page+=checkpoints.size();
            }
        }
        if(expected!=entry.confirmed()) throw new Attention("Stored checkpoint chain does not match confirmed bytes");
        if(temporary.size()>entry.confirmed()) destination.truncate(entry.temporary(),entry.confirmed());
    }
    static void readFully(InputStream stream,byte[] buffer,int count) throws IOException {
        int offset=0;while(offset<count) { int n=stream.read(buffer,offset,count-offset);if(n<0) throw new EOFException("File shortened during verification");if(n==0) throw new IOException("Read made no progress");offset+=n; }
    }
    public static void reconcile(TransferStore store,TransferEntry entry,FileEndpoint destination,TransferControl control) throws IOException {
        reconcile(store,entry,destination,control,true);
    }
    private static void reconcile(TransferStore store,TransferEntry entry,FileEndpoint destination,TransferControl control,boolean publish) throws IOException {
        if(entry.phase()!=TransferEntry.Phase.PUBLISHING) return;
        var target=optional(destination,entry.target());var temporary=optional(destination,entry.temporary());
        if(target.isPresent() && target.orElseThrow().kind()==entry.sourceInfo().kind()
            && (entry.sourceInfo().kind()!=FileEntry.Kind.FILE || target.orElseThrow().size()==entry.sourceInfo().size())
            && digest(destination,entry.target(),control).equals(entry.digest())) {
            if(temporary.isPresent()) {
                if(!owned(entry,temporary.orElseThrow())) throw new Attention("Published file verified, but partial ownership is uncertain");
                if(!digest(destination,entry.temporary(),control).equals(entry.digest())) throw new Attention("Published file verified, but partial content changed");
                destination.remove(entry.temporary(),false);
            }
            store.cleaned(entry.id());store.outcome(entry.id(),TransferEntry.Outcome.COMPLETE,"");return;
        }
        if(target.isEmpty() && entry.expectedTarget().isEmpty() && temporary.isPresent() && owned(entry,temporary.orElseThrow()) && digest(destination,entry.temporary(),control).equals(entry.digest())) {
            control.check();
            if(publish) { destination.publish(entry.temporary(),entry.target(),false);store.outcome(entry.id(),TransferEntry.Outcome.COMPLETE,""); }
            else destination.remove(entry.temporary(),false);
            store.cleaned(entry.id());return;
        }
        throw new Attention("Publication interrupted; target or partial needs review before retry");
    }
    public static void cleanup(TransferStore store,TransferEntry entry,FileEndpoint destination,TransferControl control) throws IOException {
        if(entry.phase()==TransferEntry.Phase.PUBLISHING) { reconcile(store,entry,destination,control,false); return; }
        if(entry.temporary().isEmpty()) { store.cleaned(entry.id());return; }
        var now=optional(destination,entry.temporary());
        if(now.isEmpty()) { store.cleaned(entry.id());return; }
        if(!owned(entry,now.orElseThrow())) throw new Attention("Partial ownership is uncertain; no file was deleted: "+entry.temporary());
        if(now.orElseThrow().kind()==FileEntry.Kind.FILE && entry.confirmed()>0) {
            // Cleanup needs destination prefix proof; source changes do not revoke ownership.
            try(var in=destination.read(entry.temporary(),0)) {
                byte[] buffer=new byte[256*1024];long offset=0;
                while(true) {
                    var page=store.checkpoints(entry.id(),offset,200);if(page.isEmpty()) break;
                    for(var point:page) { var hash=sha();long remaining=point.length();while(remaining>0) { control.check();int n=(int)Math.min(buffer.length,remaining);readFully(in,buffer,n);hash.update(buffer,0,n);remaining-=n; }if(!hex(hash).equals(point.digest())) throw new Attention("Partial content changed; no file was deleted"); }
                    offset+=page.size();
                }
            }
        }
        control.check(); if(!owned(entry,destination.stat(entry.temporary()))) throw new Attention("Partial changed before cleanup");
        destination.remove(entry.temporary(),false);store.cleaned(entry.id());
    }
}
