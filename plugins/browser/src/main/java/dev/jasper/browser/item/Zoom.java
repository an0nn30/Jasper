package dev.jasper.browser.item;

/**
 * A browser item's zoom (spec section 10): a whole percent from 25 to 500, Chromium's smallest and largest page zoom,
 * which Increase and Decrease Font Size move in steps of 5, and the zoom level Chromium is given for it.
 */
public final class Zoom {
    /** The smallest zoom, in percent: Chromium shows none smaller. */
    public static final int MIN = 25;
    /** The largest zoom, in percent: Chromium shows none larger. */
    public static final int MAX = 500;
    private static final int STEP = 5;
    // Chromium's zoom level is logarithmic: each level up is 1.2 times the size.
    private static final double LEVEL_BASE = Math.log(1.2);

    private Zoom() { }

    /**
     * {@code percent} a step of 5 up or down, kept between {@link #MIN} and {@link #MAX}.
     *
     * @param percent a zoom
     * @param direction above 0 for a step up, below 0 for a step down, 0 for none
     * @return the zoom a step on
     */
    public static int step(int percent, int direction) {
        return Math.clamp((long) percent + (long) Integer.signum(direction) * STEP, MIN, MAX);
    }

    /**
     * Chromium's zoom level for {@code percent}: the logarithm, base 1.2, of its factor. 100% is level 0, and 120% is 1.
     *
     * @param percent a zoom from {@link #MIN} to {@link #MAX}
     * @return the level
     * @throws IllegalArgumentException for a zoom outside that range
     */
    public static double cefLevel(int percent) {
        if (percent < MIN || percent > MAX) throw new IllegalArgumentException("Not a zoom: " + percent + "%");
        return Math.log(percent / 100.0) / LEVEL_BASE;
    }
}
