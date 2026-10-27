package dev.jasper.app.splash;

import java.awt.Color;

/**
 * The boot splash's colours (spec 10.11). They belong to the splash artwork, including each themed
 * illustration, so they live here rather than in an interface theme; ColourLiteralScanTest exempts this one file for them.
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

    static final Color MEADOW_PAPER = new Color(0xFAF9F5);
    static final Color MEADOW_PANEL = new Color(0xF3F2E9);
    static final Color MEADOW_INK = new Color(0x302F28);
    static final Color MEADOW_MUTED = new Color(0x676B5C);
    static final Color MEADOW_MOSS = new Color(0x52643F);
    static final Color MEADOW_BORDER = new Color(0xDADCCF);
    static final Color MEADOW_TRACK = new Color(0xE8ECDF);

    record Palette(Color panel, Color ink, Color muted, Color accent, Color border, Color track) { }

    static Palette palette(String id) {
        return switch (id) {
            case "slate-cobalt" -> palette(0x23262C, 0xEEF4FF, 0xBFCEE7, 0x77B5F2, 0x3B4658, 0x171C25);
            case "ubuntu-yaru" -> palette(0xF7F7F7, 0x29282F, 0x62616D, 0xC34113, 0xDAD8DF, 0xE9E5EB);
            case "catppuccin-latte" -> palette(0xE6E9EF, 0x4C4F69, 0x5C5F77, 0x8839EF, 0xBCC0CC, 0xCCD0DA);
            case "catppuccin-frappe" -> palette(0x292C3C, 0xF2F1F7, 0xC6D0F5, 0xB7A3DA, 0x51576D, 0x232634);
            case "catppuccin-macchiato" -> palette(0x242438, 0xF4EFFA, 0xCAD3F5, 0xC6A0F6, 0x494D64, 0x181926);
            case "catppuccin-mocha" -> palette(0x211D31, 0xF5EFFB, 0xCDD6F4, 0xCBA6F7, 0x45405B, 0x181522);
            default -> new Palette(MEADOW_PANEL, MEADOW_INK, MEADOW_MUTED, MEADOW_MOSS, MEADOW_BORDER, MEADOW_TRACK);
        };
    }

    private static Palette palette(int panel, int ink, int muted, int accent, int border, int track) {
        return new Palette(new Color(panel), new Color(ink), new Color(muted), new Color(accent), new Color(border), new Color(track));
    }

    private SplashColors() { }
}
