package dev.jasper.app.splash;

import dev.jasper.app.startup.StartupSteps;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The splash's back buffer (spec 10.11): the splash drawn off screen at the window's device scale, from which each paint
 * reaches the window as one copy. Windows and Linux show a window's pixels as they are drawn, so a splash painted on
 * screen showed its bare gradient at every step until the art and the text were drawn over it again; from the buffer
 * each pixel goes straight from one step's value to the next. A step draws again, and copies, only what it changes: the
 * step's line and the bar for branded art; Meadow redraws its whole frame for mascot poses. Access is serialized by Splash.
 */
final class SplashBuffer {
    private static final Rectangle WHOLE = new Rectangle(0, 0, SplashPainter.WIDTH, SplashPainter.HEIGHT);
    /**
     * The step line's top, a pixel below the icon: at a fractional scale no device row then holds both, and a scaled
     * image clipped across its edge would not draw it as the whole splash does.
     */
    private static final int LINE_TOP = SplashPainter.ICON_Y + SplashPainter.ICON_SIZE + 1;
    /** The step's line, down to the bar's rule: room for any face's ink, and About's copyright line. */
    private static final Rectangle LINE = new Rectangle(SplashPainter.CONTENT_X, LINE_TOP, SplashPainter.CONTENT_WIDTH,
        SplashPainter.BAR_RULE_Y - LINE_TOP);
    private static final Rectangle BAR = new Rectangle(SplashPainter.CONTENT_X, SplashPainter.BAR_Y, SplashPainter.CONTENT_WIDTH,
        SplashPainter.BAR_HEIGHT);

    private final SplashPainter painter;
    private BufferedImage image;
    private double scaleX, scaleY;
    private StartupSteps.Step drawn;

    /**
     * A buffer that draws with {@code painter}; it draws nothing until the first paint, which gives it the device scale.
     *
     * @param painter the decoded splash
     */
    SplashBuffer(SplashPainter painter) {
        this.painter = Objects.requireNonNull(painter, "painter");
    }

    /**
     * Paints the splash on {@code step} within {@code target}'s clip, all of it without one, as one copy: the first
     * paint and the system's.
     *
     * @param target the window's graphics, at its device scale
     * @param step the step to show
     */
    void paint(Graphics2D target, StartupSteps.Step step) {
        Objects.requireNonNull(step, "step");
        if (fits(target)) {
            for (Rectangle area : changes(step)) draw(area, step);
        } else {
            AffineTransform transform = target.getTransform();
            scaleX = transform.getScaleX();
            scaleY = transform.getScaleY();
            image = new BufferedImage(ceil(SplashPainter.WIDTH * scaleX), ceil(SplashPainter.HEIGHT * scaleY), BufferedImage.TYPE_INT_RGB);
            draw(WHOLE, step);
        }
        drawn = step;
        Rectangle clip = target.getClipBounds();
        copy(target, clip == null ? WHOLE : clip.intersection(WHOLE));
    }

    /**
     * Shows {@code step}: draws again, and copies to {@code target}, only what it changes, the step's line when its text
     * differs and the bar when its fill does, one copy each.
     *
     * @param target the window's graphics, at its device scale
     * @param step the step to show
     */
    void show(Graphics2D target, StartupSteps.Step step) {
        Objects.requireNonNull(step, "step");
        if (!fits(target)) {
            paint(target, step);
            return;
        }
        List<Rectangle> changes = changes(step);
        for (Rectangle area : changes) draw(area, step);
        drawn = step;
        for (Rectangle area : changes) copy(target, area);
    }

    /** Whether the buffer is drawn at {@code target}'s device scale. */
    private boolean fits(Graphics2D target) {
        AffineTransform transform = target.getTransform();
        return image != null && transform.getScaleX() == scaleX && transform.getScaleY() == scaleY;
    }

    /** What showing {@code step} changes after the step drawn. */
    private List<Rectangle> changes(StartupSteps.Step step) {
        if (painter.animated()) return List.of(WHOLE);
        var changes = new ArrayList<Rectangle>(2);
        if (!step.text().equals(drawn.text())) changes.add(LINE);
        if (SplashPainter.fillWidth(step.fraction()) != SplashPainter.fillWidth(drawn.fraction())) changes.add(BAR);
        return changes;
    }

    /**
     * Draws the whole splash on {@code step} into the buffer, clipped to {@code area}'s pixels, over the window's own
     * first fill: at a fractional scale the border covers the edge pixels in part, and they blend with it as on screen,
     * the same at every redraw.
     */
    private void draw(Rectangle area, StartupSteps.Step step) {
        Graphics2D g = image.createGraphics();
        try {
            Rectangle pixels = pixels(area);
            g.clip(pixels);
            g.setColor(painter.background());
            g.fill(pixels);
            g.scale(scaleX, scaleY);
            painter.paint(g, step.text(), step.fraction());
        } finally {
            g.dispose();
        }
    }

    /** Copies {@code area}'s pixels from the buffer to {@code target}, one to one, in one draw. */
    private void copy(Graphics2D target, Rectangle area) {
        Rectangle pixels = pixels(area);
        if (pixels.isEmpty()) return;
        Graphics2D g = (Graphics2D) target.create();
        try {
            AffineTransform transform = g.getTransform();
            g.setTransform(AffineTransform.getTranslateInstance(transform.getTranslateX(), transform.getTranslateY()));
            int right = pixels.x + pixels.width, bottom = pixels.y + pixels.height;
            g.drawImage(image, pixels.x, pixels.y, right, bottom, pixels.x, pixels.y, right, bottom, null);
        } finally {
            g.dispose();
        }
    }

    /** The buffer's pixels that {@code area}, in window pixels, touches. */
    private Rectangle pixels(Rectangle area) {
        int left = floor(area.x * scaleX), top = floor(area.y * scaleY);
        var pixels = new Rectangle(left, top, ceil(area.getMaxX() * scaleX) - left, ceil(area.getMaxY() * scaleY) - top);
        return pixels.intersection(new Rectangle(image.getWidth(), image.getHeight()));
    }

    private static int floor(double value) { return (int) Math.floor(value + 1e-6); }

    private static int ceil(double value) { return (int) Math.ceil(value - 1e-6); }
}
