package dev.jasper.ui.metrics;

import java.awt.Font;
import java.util.Objects;

/**
 * The faces a kit draws text in, by registered full name (spec 5.6): a UI face per weight and a data face per weight. JBR
 * does not pick the weights of static faces through {@code TextAttribute.WEIGHT}, so each weight names its own file's
 * face. An empty UI name keeps the platform policy of {@link Typography} for that weight; the data roles draw in
 * {@link #dataRegular()}, or in {@link #dataMedium()} from weight 500. A face that is not registered draws in the
 * platform face for its role. Compared by value.
 */
public final class FaceSet {
    /** Today's policy: the platform UI family and {@link Font#MONOSPACED} for the data roles. */
    public static final FaceSet PLATFORM = new FaceSet("", "", "", Font.MONOSPACED, Font.MONOSPACED);

    private final String uiRegular, uiMedium, uiSemibold, dataRegular, dataMedium;

    private FaceSet(String uiRegular, String uiMedium, String uiSemibold, String dataRegular, String dataMedium) {
        this.uiRegular = uiRegular; this.uiMedium = uiMedium; this.uiSemibold = uiSemibold;
        this.dataRegular = dataRegular; this.dataMedium = dataMedium;
    }

    /**
     * Faces by registered full name; an empty UI name keeps the platform's face for that weight, and an empty data name
     * is {@link Font#MONOSPACED}.
     */
    public static FaceSet of(String uiRegular, String uiMedium, String uiSemibold, String dataRegular, String dataMedium) {
        Objects.requireNonNull(uiRegular); Objects.requireNonNull(uiMedium); Objects.requireNonNull(uiSemibold);
        Objects.requireNonNull(dataRegular); Objects.requireNonNull(dataMedium);
        return new FaceSet(uiRegular, uiMedium, uiSemibold, dataRegular.isEmpty() ? Font.MONOSPACED : dataRegular,
            dataMedium.isEmpty() ? Font.MONOSPACED : dataMedium);
    }

    /** Whether any UI weight names a face of the kit's own. */
    public boolean namesUiFaces() { return !uiRegular.isEmpty() || !uiMedium.isEmpty() || !uiSemibold.isEmpty(); }

    /** The UI face for {@code weight} (400, 500 or 600); empty for the platform's. */
    public String uiFace(int weight) { return weight >= 600 ? uiSemibold : weight >= 500 ? uiMedium : uiRegular; }

    /** The data face at regular weight. */
    public String dataRegular() { return dataRegular; }
    /** The data face from weight 500. */
    public String dataMedium() { return dataMedium; }

    @Override public boolean equals(Object other) {
        return other instanceof FaceSet faces && uiRegular.equals(faces.uiRegular) && uiMedium.equals(faces.uiMedium)
            && uiSemibold.equals(faces.uiSemibold) && dataRegular.equals(faces.dataRegular) && dataMedium.equals(faces.dataMedium);
    }
    @Override public int hashCode() { return Objects.hash(uiRegular, uiMedium, uiSemibold, dataRegular, dataMedium); }
    @Override public String toString() {
        return "FaceSet[" + uiRegular + ", " + uiMedium + ", " + uiSemibold + ", " + dataRegular + ", " + dataMedium + "]";
    }
}
