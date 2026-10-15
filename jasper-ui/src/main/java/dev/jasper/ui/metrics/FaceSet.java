package dev.jasper.ui.metrics;

import java.awt.Font;

/**
 * The faces a kit draws text in, by registered face name. The UI roles keep the platform policy of {@link Typography}
 * (the platform family, resolved once per process, unless the user names one); the data roles draw in
 * {@link #dataRegular()}, or in {@link #dataMedium()} from weight 500.
 */
public final class FaceSet {
    /** Today's policy: the platform UI family and {@link Font#MONOSPACED} for the data roles. */
    public static final FaceSet PLATFORM = new FaceSet(Font.MONOSPACED, Font.MONOSPACED);

    private final String dataRegular;
    private final String dataMedium;

    private FaceSet(String dataRegular, String dataMedium) { this.dataRegular = dataRegular; this.dataMedium = dataMedium; }

    /** The data face at regular weight. */
    public String dataRegular() { return dataRegular; }
    /** The data face from weight 500. */
    public String dataMedium() { return dataMedium; }
}
