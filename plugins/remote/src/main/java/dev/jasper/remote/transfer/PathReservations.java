package dev.jasper.remote.transfer;

import java.text.Normalizer;
import java.util.*;

/** Atomic no-I/O admission across canonical endpoint identities and overlapping subtrees. */
public final class PathReservations {
    public record Key(String endpoint,String path,boolean exclusive) {
        public Key {
            Objects.requireNonNull(endpoint); Objects.requireNonNull(path);
            // Conservatively serialize case/normalization aliases on local providers, including APFS.
            if(endpoint.equals("local")) path=Normalizer.normalize(path.replace('\\','/'),Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
            while(path.length()>1 && path.endsWith("/")) path=path.substring(0,path.length()-1);
        }
    }
    private record Claim(UUID owner,List<Key> keys) {}
    private final Map<UUID,Claim> claims=new HashMap<>();
    public synchronized Optional<Lease> acquire(UUID owner,List<Key> keys) {
        var copy=List.copyOf(keys);
        for(var held:claims.values()) if(!held.owner().equals(owner))
            for(var first:copy) for(var second:held.keys()) if(conflicts(first,second)) return Optional.empty();
        UUID id=UUID.randomUUID();claims.put(id,new Claim(owner,copy));return Optional.of(new Lease(id));
    }
    public static boolean overlaps(Key first,Key second) { return first.endpoint().equals(second.endpoint()) && (contains(first.path(),second.path()) || contains(second.path(),first.path())); }
    private static boolean contains(String parent,String child) { return parent.equals(child) || child.startsWith(parent.endsWith("/")?parent:parent+"/"); }
    private static boolean conflicts(Key first,Key second) { return (first.exclusive() || second.exclusive()) && overlaps(first,second); }
    public final class Lease implements AutoCloseable {
        private final UUID id;
        private Lease(UUID id) { this.id=id; }
        @Override public void close() { synchronized(PathReservations.this) { claims.remove(id); } }
    }
}
