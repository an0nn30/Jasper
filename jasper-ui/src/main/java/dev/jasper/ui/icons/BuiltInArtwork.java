package dev.jasper.ui.icons;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Complete immutable engine catalog, using exactly the external safe decoder. */
final class BuiltInArtwork {
    record Pair(DecodedArtwork light, DecodedArtwork dark) {
        DecodedArtwork select(boolean darkTheme) { return darkTheme ? dark : light; }
    }
    static final Set<String> IDENTITIES = Set.of("network", "tunnel", "more.vertical");
    private static final class Holder {
        static final Map<String, Pair> CATALOG = Map.of(
            "settings", pair("general/settings"), "network", pair("toolwindows/web"),
            "tunnel", pair("actions/swapPanels"), "more.vertical", pair("general/moreVertical"),
            "chevron.right", pair("general/chevronRight"));
    }
    static Map<String, Pair> catalog() { return Holder.CATALOG; }
    private static Pair pair(String stem) { return new Pair(read(stem + ".svg"), read(stem + "_dark.svg")); }
    private static final class Hashes {
        static final Map<String,String> EXPECTED = hashes();
        private static Map<String,String> hashes() {
            try(var input=BuiltInArtwork.class.getResourceAsStream("assets-selected.tsv")) {
                if(input==null)throw new IOException("Missing selected hash manifest");
                var result=new java.util.LinkedHashMap<String,String>();
                for(String line:new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
                    var columns=line.split("\\t");
                    if(columns.length!=3 || result.put(columns[0],columns[2])!=null)throw new IOException("Invalid selected hash manifest");
                }
                if(result.size()!=10)throw new IOException("Expected ten original resources");
                return Map.copyOf(result);
            }catch(IOException failure){throw new IllegalStateException("Required bundled artwork: assets-selected.tsv",failure);}
        }
    }
    private static DecodedArtwork read(String file) {
        String expected=Hashes.EXPECTED.get(file);
        try (var stream = BuiltInArtwork.class.getResourceAsStream(file)) {
            return decodeRequired(file,stream==null?null:stream.readAllBytes(),expected);
        } catch (IOException failure) {
            throw new IllegalStateException("Required bundled artwork: " + file + " SHA-256 " + expected, failure);
        }
    }
    static DecodedArtwork decodeRequired(String file, byte[] data, String expected) {
        try {
            if(data==null)throw new IOException("Missing bundled artwork");
            String actual=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
            if(!actual.equals(expected))throw new IOException("Bundled artwork hash mismatch: " + actual);
            return new EncodedArtwork(EncodedArtwork.Format.SVG, data).decoded();
        } catch (IOException | java.security.NoSuchAlgorithmException | IllegalArgumentException failure) {
            throw new IllegalStateException("Required bundled artwork: " + file + " SHA-256 " + expected, failure);
        }
    }
}
