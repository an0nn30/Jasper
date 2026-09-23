package dev.jasper.remote.transfer;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEntry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Versioned, length-prefixed nonsecret values. Deliberately not Java object serialization. */
public final class TransferCodec {
    private static final int MAX = 1024 * 1024;
    private TransferCodec() {}
    @FunctionalInterface private interface Write { void write(DataOutputStream out) throws IOException; }
    @FunctionalInterface private interface Read<T> { T read(DataInputStream in) throws IOException; }
    private static String encode(Write write) throws IOException {
        var bytes = new ByteArrayOutputStream() {
            @Override public synchronized void write(int b) { if (count >= MAX) throw new IllegalArgumentException("Selection is too large; select its folder instead"); super.write(b); }
            @Override public synchronized void write(byte[] b, int off, int length) {
                if (length > MAX - count) throw new IllegalArgumentException("Selection is too large; select its folder instead"); super.write(b,off,length);
            }
        };
        try (var out = new DataOutputStream(bytes)) { out.writeInt(1); write.write(out); }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    private static <T> T decode(String value, Read<T> read) throws IOException {
        if (value.length() > 4L * MAX / 3 + 8) throw new IOException("Stored value is too large");
        try (var in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)))) {
            if (in.readInt() != 1) throw new IOException("Unsupported transfer value version");
            T result = read.read(in); if (in.available() != 0) throw new IOException("Trailing transfer value data"); return result;
        } catch (IllegalArgumentException | IndexOutOfBoundsException bad) { throw new IOException("Invalid transfer value", bad); }
    }
    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX) throw new IOException("Transfer field too long");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String text(DataInputStream in) throws IOException {
        int length = in.readInt(); if (length < 0 || length > MAX || length > in.available()) throw new IOException("Invalid transfer field length");
        return new String(in.readNBytes(length),StandardCharsets.UTF_8);
    }
    private static int count(DataInputStream in, int max) throws IOException {
        int count=in.readInt(); if (count<0 || count>max) throw new IOException("Invalid transfer element count"); return count;
    }
    private static void uuid(DataOutputStream out, UUID value) throws IOException { out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits()); }
    private static UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(),in.readLong()); }
    public static String identity(ConnectionIdentity identity) throws IOException {
        return encode(out -> {
            out.writeInt(identity.hops().size());
            for (var hop:identity.hops()) {
                var host=hop.host(); uuid(out,host.id()); text(out,host.name()); text(out,host.hostname()); out.writeInt(host.port());
                text(out,host.username()); text(out,hop.username());
                switch (host.auth()) {
                    case Auth.Agent ignored -> out.writeByte(0);
                    case Auth.Vault vault -> { out.writeByte(1); uuid(out,vault.credentialId()); }
                    case Auth.VaultKeys keys -> { out.writeByte(2); out.writeInt(keys.credentialIds().size()); for (UUID id:keys.credentialIds()) uuid(out,id); }
                }
            }
        });
    }
    public static ConnectionIdentity identity(String encoded) throws IOException {
        return decode(encoded,in -> {
            int count=count(in,64); if (count==0) throw new IOException("Empty route");
            var hosts=new ArrayList<RemoteHost>(); var users=new ArrayList<String>();
            for (int i=0;i<count;i++) {
                UUID id=uuid(in); String name=text(in), hostname=text(in); int port=in.readInt(); String configured=text(in), effective=text(in);
                Auth auth=switch (in.readUnsignedByte()) {
                    case 0 -> Auth.AGENT;
                    case 1 -> new Auth.Vault(uuid(in));
                    case 2 -> { int n=count(in,256); var ids=new ArrayList<UUID>(); for(int j=0;j<n;j++) ids.add(uuid(in)); yield new Auth.VaultKeys(ids); }
                    default -> throw new IOException("Unknown authentication reference");
                };
                hosts.add(new RemoteHost(id,name,hostname,port,configured,auth,"",false,Optional.empty(),Instant.EPOCH,Instant.EPOCH)); users.add(effective);
            }
            var hops=new ArrayList<ConnectionIdentity.Hop>();
            for(int i=0;i<count;i++) {
                var host=hosts.get(i);
                if(i+1<count) host=host.withEdited(host.name(),host.hostname(),host.port(),host.username(),host.auth(),"",Optional.of(hosts.get(i+1).id()));
                hops.add(new ConnectionIdentity.Hop(host,users.get(i)));
            }
            return new ConnectionIdentity(hops);
        });
    }
    private static void endpoint(DataOutputStream out, EndpointRef ref) throws IOException {
        out.writeBoolean(ref.hostId().isPresent());
        if (ref.hostId().isPresent()) { uuid(out,ref.hostId().orElseThrow()); text(out,ref.snapshot()); text(out,ref.effectiveUsername()); }
    }
    private static EndpointRef endpoint(DataInputStream in) throws IOException {
        if (!in.readBoolean()) return EndpointRef.local();
        var ref=new EndpointRef(Optional.of(uuid(in)),text(in),text(in)); ref.identity(); return ref;
    }
    public static String request(TransferRequest request) throws IOException {
        return encode(out -> { endpoint(out,request.source()); endpoint(out,request.destination()); text(out,request.directory());
            out.writeInt(request.paths().size()); for(String path:request.paths()) text(out,path); });
    }
    public static TransferRequest request(String encoded) throws IOException {
        return decode(encoded,in -> { var source=endpoint(in); var destination=endpoint(in); String directory=text(in);
            int n=count(in,100_000); var paths=new ArrayList<String>(); for(int i=0;i<n;i++) paths.add(text(in));
            return new TransferRequest(source,paths,destination,directory); });
    }
    public static String file(FileEntry file) throws IOException {
        return encode(out -> { text(out,file.name()); text(out,file.kind().name()); out.writeLong(file.size()); out.writeLong(file.modifiedMillis());
            out.writeInt(file.permissions()); text(out,file.linkTarget()); text(out,file.fileKey()); });
    }
    public static FileEntry file(String encoded) throws IOException {
        return decode(encoded,in -> new FileEntry(text(in),FileEntry.Kind.valueOf(text(in)),in.readLong(),in.readLong(),in.readInt(),text(in),text(in)));
    }
}
