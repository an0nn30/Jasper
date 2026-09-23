package dev.jasper.remote.hosts;

/** Best-effort, non-secret facts learned from an authenticated SSH session. Empty means unknown. */
public record HostInfo(String os, String address) {
    public static final HostInfo EMPTY = new HostInfo("", "");
    public HostInfo { os = clean(os); address = clean(address); }
    private static String clean(String value) {
        if (value == null) return "";
        value = value.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        return value.substring(0, Math.min(120, value.length()));
    }
}
