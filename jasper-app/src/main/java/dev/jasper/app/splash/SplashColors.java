package dev.jasper.app.splash;

import java.awt.Color;

/**
 * The boot splash's colours (spec 10.11). They belong to the splash artwork, including the Protean composition, so they live here rather than in an interface theme; ColourLiteralScanTest exempts this one file for them.
 */
final class SplashColors {
    /** The 1 px window border and the rule under the art. */
    static final Color BORDER = new Color(0x8A8F96);
    /** The content gradient's top. */
    static final Color TOP = new Color(0x17346F);
    /** The content gradient's bottom. */
    static final Color BOTTOM = new Color(0x102552);
    /** The product name. */
    static final Color PRODUCT = new Color(0xFFFFFF);
    /** The version line and the step. */
    static final Color TEXT = new Color(0xC7D6F2);
    /** The copyright line. */
    static final Color COPYRIGHT = new Color(0x9FB3DA);
    /** The bar's track. */
    static final Color TRACK = new Color(0x0B1A3C);
    /** The 1 px rule above the track. */
    static final Color TRACK_RULE = new Color(0x2A4580);
    /** The fill gradient's top, its stop at 60 % and its bottom. */
    static final Color FILL_TOP = new Color(0x5A8BE0), FILL_MIDDLE = new Color(0x2F65CA), FILL_BOTTOM = new Color(0x1F4C9E);

    static final Color PROTEAN_BACKGROUND = new Color(0x212a33), PROTEAN_RED = new Color(0xf34532),
        PROTEAN_WHITE = new Color(0xf3f3f2), PROTEAN_DIM = new Color(0x828b95), PROTEAN_TRACK = new Color(0x424b55),
        PROTEAN_GLOW = new Color(92,35,35,16), PROTEAN_CLEAR = new Color(92,35,35,0);

    private SplashColors() { }
}
