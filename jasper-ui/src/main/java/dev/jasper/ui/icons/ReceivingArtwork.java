package dev.jasper.ui.icons;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;

/** Scoped artwork aliases; publish before retiring a generation, and detach controls before closing. */
public final class ReceivingArtwork implements AutoCloseable {
    private static final AtomicLong OWNERS=new AtomicLong();
    private record Entry(String name,IconRegistration registration) { }
    private final IconRegistry registry;
    private final String namespace="received"+OWNERS.incrementAndGet();
    private final LinkedHashMap<Object,Entry> entries=new LinkedHashMap<>();
    private Set<Object> requested;
    private long sequence;
    private boolean closed;
    public ReceivingArtwork(IconRegistry registry){this.registry=Objects.requireNonNull(registry);}
    public String name(EncodedArtwork artwork) {
        return name(artwork, () -> registry.register(nextAlias(), artwork));
    }
    /** Registers bytes and declared tile hue as one scoped receiving identity. */
    public String name(IdentityArtwork artwork) {
        return name(artwork, () -> registry.register(nextAlias(), artwork));
    }
    private String nextAlias() { return namespace+":art"+(++sequence); }
    private String name(Object artwork, java.util.function.Supplier<IconRegistration> register) {
        requireOpen();Objects.requireNonNull(artwork);
        var entry=entries.get(artwork);
        if(entry==null){var registration=register.get();String alias=namespace+":art"+sequence;entry=new Entry(alias,registration);entries.put(artwork,entry);}
        if(requested!=null)requested.add(artwork);
        return entry.name();
    }
    public void reconcile(Runnable publishModel) {
        requireOpen();if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Reconcile on EDT");
        if(requested!=null)throw new IllegalStateException("Nested artwork publication");
        requested=new HashSet<>();
        try {
            publishModel.run();
            entries.entrySet().removeIf(entry->{if(requested.contains(entry.getKey()))return false;entry.getValue().registration().close();return true;});
        } finally {requested=null;}
    }
    private void requireOpen(){if(closed)throw new IllegalStateException("Artwork receiver closed");}
    @Override public void close(){if(closed)return;closed=true;entries.values().forEach(entry->entry.registration().close());entries.clear();}
}
