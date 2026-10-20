package dev.jasper.remote.workstation;

import dev.jasper.sdk.ui.Presentation;

/**
 * Which composition Remote's views take (Enterprise presentation spec 14.3): {@link #CLASSIC} under a classic look
 * ({@code Appearance.presentation().classic()}), else {@link #ROWS}.
 */
public enum Layout {
    /** Today's rows, cards and switches. */
    ROWS,
    /** The Workstation mocks' trees, tables, Properties and toolbar view. */
    CLASSIC;

    /**
     * The layout for this run.
     *
     * @param presentation the look the host reports
     * @return {@link #CLASSIC} exactly when the look is classic, else {@link #ROWS}
     */
    public static Layout of(Presentation presentation) {
        return presentation.classic() ? CLASSIC : ROWS;
    }
}
