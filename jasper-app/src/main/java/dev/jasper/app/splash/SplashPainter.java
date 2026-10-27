package dev.jasper.app.splash;

import dev.jasper.ui.icons.EncodedArtwork;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Draws the boot splash (spec 10.11) as the Boot Splash frames and the animated mock's markup give it, in window pixels:
 * a 720 by 440 window whose 1 px border surrounds a 718 by 438 content area at (1, 1), so a content position (x, y) of
 * the mock is window pixel (x + 1, y + 1) here. The positions are the frames' (the plan's Measured targets). The same in
 * every theme: it reads no theme and no UIManager key. {@link #of} decodes the files once; painting draws the art from a
 * raster made once per device scale.
 */
public final class SplashPainter {
    /** The window's width in logical pixels. */
    public static final int WIDTH = 720;
    /** The window's height in logical pixels. */
    public static final int HEIGHT = 440;
    static final int CONTENT_X = 1, CONTENT_Y = 1, CONTENT_WIDTH = 718, CONTENT_HEIGHT = 438;
    static final int ART_HEIGHT = 250, RULE_Y = 251;
    static final int ICON_X = 41, ICON_Y = 279, ICON_SIZE = 64;
    static final int TEXT_X = 121, NAME_BASELINE = 312, VERSION_BASELINE = 333;
    static final int STEP_X = 41, STEP_BASELINE = 405, COPYRIGHT_RIGHT = 679, COPYRIGHT_BASELINE = 406, GAP = 24;
    static final int BAR_RULE_Y = 432, BAR_Y = 433, BAR_HEIGHT = 6;
    static final float NAME_SIZE = 30f, NAME_TRACKING = -0.4f, VERSION_SIZE = 12f, STEP_SIZE = 12f, COPYRIGHT_SIZE = 11f;
    static final int MAX_SCALE = 4;
    private static final System.Logger LOG = System.getLogger(SplashPainter.class.getName());

    /**
     * What the splash draws.
     *
     * @param product the product name
     * @param version the version line
     * @param copyright the copyright line
     * @param icon64 the 64 px icon's PNG file
     * @param icon128 the 128 px icon's PNG file, for a 2x screen
     * @param art the splash art's SVG file
     * @param nameFace the UI face's semibold file, for the product name; empty for the platform face
     * @param textFace the UI face's regular file, for the step and the copyright line; empty for the platform face
     * @param versionFace the data face's regular file, for the version line; empty for the platform monospaced face
     */
    public record Content(String product, String version, String copyright, byte[] icon64, byte[] icon128, byte[] art,
                          Optional<byte[]> nameFace, Optional<byte[]> textFace, Optional<byte[]> versionFace) {
        /** Every part is required. */
        public Content {
            Objects.requireNonNull(product, "product"); Objects.requireNonNull(version, "version");
            Objects.requireNonNull(copyright, "copyright"); Objects.requireNonNull(icon64, "icon64");
            Objects.requireNonNull(icon128, "icon128"); Objects.requireNonNull(art, "art");
            Objects.requireNonNull(nameFace, "nameFace"); Objects.requireNonNull(textFace, "textFace");
            Objects.requireNonNull(versionFace, "versionFace");
        }
    }

    private final MeadowSplash meadow;
    private final ThemeSplash themed;
    private final String product, version, copyright;
    private final Image icon;
    private final EncodedArtwork art;
    private final Font name, versionFont, stepFont, copyrightFont;
    private final Map<Integer, BufferedImage> rasters = new ConcurrentHashMap<>();

    private SplashPainter(String product, String version, String copyright, Image icon, EncodedArtwork art, Font name,
                          Font versionFont, Font stepFont, Font copyrightFont) {
        this.meadow = null;
        this.themed = null;
        this.product = product; this.version = version; this.copyright = copyright; this.icon = icon; this.art = art;
        this.name = name; this.versionFont = versionFont; this.stepFont = stepFont; this.copyrightFont = copyrightFont;
    }

    private SplashPainter(MeadowSplash meadow) {
        this.meadow = meadow;
        this.themed = null;
        product = version = copyright = null;
        icon = null; art = null;
        name = versionFont = stepFont = copyrightFont = null;
    }

    /**
     * Decodes Jasper's standard Meadow splash before its window opens.
     * @param version the build version to display
     * @param scale initial device scale, clamped to 1 through 4
     * @return the default splash painter
     */
    public static SplashPainter meadow(String version, int scale) {
        return new SplashPainter(new MeadowSplash(version, scale));
    }

    private SplashPainter(ThemeSplash themed) {
        this.themed = themed; meadow = null;
        product = version = copyright = null;
        icon = null; art = null;
        name = versionFont = stepFont = copyrightFont = null;
    }

    /**
     * Decodes the bundled still illustration for an interface theme. Custom and Desktop themes use Meadow art;
     * unreadable art falls back to the original animated Meadow.
     * @param themeId the validated interface theme id
     * @param version the build version to display
     * @param scale initial device scale for the original Meadow fallback, clamped to 1 through 4
     * @return the decoded painter
     */
    public static SplashPainter themed(String themeId, String version, int scale) {
        Objects.requireNonNull(themeId, "themeId"); Objects.requireNonNull(version, "version");
        try { return new SplashPainter(new ThemeSplash(themeId, version)); }
        catch (RuntimeException undrawable) {
            LOG.log(System.Logger.Level.WARNING, "The theme splash cannot be drawn; using Meadow", undrawable);
            return meadow(version, scale);
        }
    }

    boolean animated() { return meadow != null; }
    boolean illustrated() { return themed != null; }
    java.awt.Color background() { return themed != null ? themed.background() : meadow == null ? SplashColors.TOP : SplashColors.MEADOW_PAPER; }
    void animationFrame(int tick) { if (meadow != null) meadow.frame(tick); }

    /**
     * Decodes {@code content} once and draws the art for {@code scale}, so the first paint decodes nothing. Any thread.
     *
     * @param content what the splash draws
     * @param scale the main screen's device scale, 1 to 4
     * @return the painter
     * @throws IllegalArgumentException when an icon or the art cannot be drawn
     */
    public static SplashPainter of(Content content, int scale) {
        Objects.requireNonNull(content, "content");
        Image icon = new BaseMultiResolutionImage(png(content.icon64(), ICON_SIZE), png(content.icon128(), 2 * ICON_SIZE));
        var art = new EncodedArtwork(EncodedArtwork.Format.SVG, content.art());
        art.prepare();
        Font semibold = face(content.nameFace(), Font.SANS_SERIF, Font.BOLD);
        Font regular = face(content.textFace(), Font.SANS_SERIF, Font.PLAIN);
        Font data = face(content.versionFace(), Font.MONOSPACED, Font.PLAIN);
        var painter = new SplashPainter(content.product(), content.version(), content.copyright(), icon, art,
            semibold.deriveFont(Map.of(TextAttribute.SIZE, NAME_SIZE, TextAttribute.TRACKING, NAME_TRACKING / NAME_SIZE)),
            data.deriveFont(VERSION_SIZE), regular.deriveFont(STEP_SIZE), regular.deriveFont(COPYRIGHT_SIZE));
        painter.raster(Math.clamp(scale, 1, MAX_SCALE));
        return painter;
    }

    /**
     * Paints the whole splash at the graphics' origin, in logical pixels, at the graphics' device scale.
     *
     * @param target where to paint; its state is left as it was
     * @param step the step's text, ellipsised to the room before the copyright line
     * @param fraction how far the bar is filled, 0 to 1
     */
    public void paint(Graphics2D target, String step, double fraction) {
        Objects.requireNonNull(step, "step");
        Graphics2D g = (Graphics2D) target.create();
        try {
            if (themed != null) { themed.paint(g, step, fraction); return; }
            if (meadow != null) { meadow.paint(g, step, fraction); return; }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setPaint(new LinearGradientPaint(0, CONTENT_Y, 0, CONTENT_Y + CONTENT_HEIGHT, new float[] {0f, 1f},
                new Color[] {SplashColors.TOP, SplashColors.BOTTOM}));
            g.fillRect(CONTENT_X, CONTENT_Y, CONTENT_WIDTH, CONTENT_HEIGHT);
            g.drawImage(raster(scale(g)), CONTENT_X, CONTENT_Y, CONTENT_WIDTH, ART_HEIGHT, null);
            g.setColor(SplashColors.BORDER);
            g.fillRect(CONTENT_X, RULE_Y, CONTENT_WIDTH, 1);
            g.drawImage(icon, ICON_X, ICON_Y, ICON_SIZE, ICON_SIZE, null);
            FontRenderContext frc = g.getFontRenderContext();
            Shape clip = g.getClip();
            g.clipRect(TEXT_X, 0, COPYRIGHT_RIGHT - TEXT_X, HEIGHT);
            text(g, displayable(name, product, Font.SANS_SERIF, Font.BOLD), SplashColors.PRODUCT, product, TEXT_X, NAME_BASELINE);
            text(g, versionFont, SplashColors.TEXT, version, TEXT_X, VERSION_BASELINE);
            g.setClip(clip);
            float copyrightX = COPYRIGHT_RIGHT - advance(copyrightFont, frc, copyright);
            text(g, copyrightFont, SplashColors.COPYRIGHT, copyright, copyrightX, COPYRIGHT_BASELINE);
            Font stepFace = displayable(stepFont, step, Font.SANS_SERIF, Font.PLAIN);
            text(g, stepFace, SplashColors.TEXT, fit(stepFace, frc, step, copyrightX - GAP - STEP_X), STEP_X, STEP_BASELINE);
            g.setColor(SplashColors.TRACK_RULE);
            g.fillRect(CONTENT_X, BAR_RULE_Y, CONTENT_WIDTH, 1);
            g.setColor(SplashColors.TRACK);
            g.fillRect(CONTENT_X, BAR_Y, CONTENT_WIDTH, BAR_HEIGHT);
            int fill = fillWidth(fraction);
            if (fill > 0) {
                g.setPaint(new LinearGradientPaint(0, BAR_Y, 0, BAR_Y + BAR_HEIGHT, new float[] {0f, 0.6f, 1f},
                    new Color[] {SplashColors.FILL_TOP, SplashColors.FILL_MIDDLE, SplashColors.FILL_BOTTOM}));
                g.fillRect(CONTENT_X, BAR_Y, fill, BAR_HEIGHT);
            }
            g.setColor(SplashColors.BORDER);
            g.fillRect(0, 0, WIDTH, 1);
            g.fillRect(0, HEIGHT - 1, WIDTH, 1);
            g.fillRect(0, 0, 1, HEIGHT);
            g.fillRect(WIDTH - 1, 0, 1, HEIGHT);
        } finally {
            g.dispose();
        }
    }

    /** The fill's width in pixels: the fraction of the 718 px bar, rounded; no easing. */
    static int fillWidth(double fraction) {
        if (!(fraction > 0)) return 0;
        return (int) Math.round(Math.min(1, fraction) * CONTENT_WIDTH);
    }

    /** {@code text}, shortened with an ellipsis to fit {@code room} pixels. */
    static String fit(Font font, FontRenderContext frc, String text, float room) {
        if (advance(font, frc, text) <= room) return text;
        for (int end = text.length(); end > 0; end = text.offsetByCodePoints(end, -1)) {
            String shortened = text.substring(0, end).stripTrailing() + "…";
            if (advance(font, frc, shortened) <= room) return shortened;
        }
        return "";
    }

    /**
     * {@code font}, or the platform's {@code family} at {@code style} and {@code font}'s size when {@code font} lacks a
     * glyph of {@code text} (a plugin or product name in another script), as the application's own text does under a
     * kit face (plan 5b).
     */
    static Font displayable(Font font, String text, String family, int style) {
        if (font.canDisplayUpTo(text) == -1) return font;
        return new Font(family, style, 1).deriveFont(font.getSize2D());
    }

    /** {@code text}'s advance in logical pixels. */
    static float advance(Font font, FontRenderContext frc, String text) {
        return text.isEmpty() ? 0f : (float) font.getStringBounds(text, frc).getWidth();
    }

    private static void text(Graphics2D g, Font font, Color color, String text, float x, float baseline) {
        if (text.isEmpty()) return;
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, x, baseline);
    }

    private static int scale(Graphics2D g) {
        return Math.clamp((long) Math.ceil(g.getTransform().getScaleX() - 1e-6), 1, MAX_SCALE);
    }

    private BufferedImage raster(int scale) {
        return rasters.computeIfAbsent(scale, factor -> art.raster(CONTENT_WIDTH * factor, ART_HEIGHT * factor));
    }

    private static BufferedImage png(byte[] file, int size) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file));
            if (image != null) return image;
        } catch (IOException | RuntimeException unreadable) {
            throw new IllegalArgumentException("The splash's " + size + " px icon does not decode", unreadable);
        }
        throw new IllegalArgumentException("The splash's " + size + " px icon does not decode");
    }

    private static Font face(Optional<byte[]> file, String fallbackFamily, int fallbackStyle) {
        if (file.isPresent()) {
            try { return Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(file.get())); }
            catch (FontFormatException | IOException | RuntimeException undecodable) {
                LOG.log(System.Logger.Level.WARNING, "A splash face does not decode; the platform face draws its text", undecodable);
            }
        }
        return new Font(fallbackFamily, fallbackStyle, 1);
    }
}
