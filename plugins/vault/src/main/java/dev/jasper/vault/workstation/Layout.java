package dev.jasper.vault.workstation;

import dev.jasper.sdk.ui.Presentation;

/**
 * Which composition the Credential Vault's views take (Enterprise presentation spec 14.3): {@link #CLASSIC} under a
 * classic look ({@code Appearance.presentation().classic()}), else {@link #ROWS}.
 */
public enum Layout {
    /** Today's rows, cards and switches. */
    ROWS,
    /** The 07 Credential Vault mocks' classic manager, editors and Unlock form. */
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
