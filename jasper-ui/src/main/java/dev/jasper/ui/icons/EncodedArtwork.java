package dev.jasper.ui.icons;

import java.util.Arrays;
import java.util.Objects;

/** Immutable authored SVG or PNG bytes. Receiving registries validate and decode once. */
public final class EncodedArtwork {
    public enum Format { SVG, PNG }
    private final Format format;
    private final byte[] data;
    private DecodedArtwork prepared;
    private IllegalArgumentException rejected;
    public EncodedArtwork(Format format, byte[] data) {
        this.format=Objects.requireNonNull(format);
        Objects.requireNonNull(data);
        if(data.length==0 || data.length>256*1024) throw new IllegalArgumentException("Artwork must contain 1..262144 bytes");
        this.data=data.clone();
    }
    /** Eagerly validates/decodes on an owner worker. Direct registry users may omit this step. */
    public void prepare() { decoded(); }
    synchronized DecodedArtwork decoded() {
        if(rejected!=null)throw rejected;
        if(prepared==null)try {prepared=new DecodedArtwork(this);}catch(IllegalArgumentException failure){rejected=failure;throw failure;}
        return prepared;
    }
    public Format format(){return format;}
    public byte[] data(){return data.clone();}
    @Override public boolean equals(Object other){return other instanceof EncodedArtwork art && format==art.format && Arrays.equals(data,art.data);}
    @Override public int hashCode(){return 31*format.hashCode()+Arrays.hashCode(data);}
    @Override public String toString(){return "EncodedArtwork["+format+", "+data.length+" bytes]";}
}
