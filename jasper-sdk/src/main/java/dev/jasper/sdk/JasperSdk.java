package dev.jasper.sdk;

/**
 * The SDK version a plugin's {@code sdk} range is checked against: Jasper does not load a plugin whose range in
 * {@code plugin.toml} excludes {@link #VERSION}. 0.x carries no compatibility promise.
 */
public final class JasperSdk {
    /** Semantic version of this SDK. */
    public static final String VERSION = "0.22.0";

    private JasperSdk() { }
}
