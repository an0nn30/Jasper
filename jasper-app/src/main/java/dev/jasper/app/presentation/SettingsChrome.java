package dev.jasper.app.presentation;

import java.util.Set;

/**
 * The Settings dialog's projection (spec 9.1 and 12.1).
 *
 * @param integratedTitle whether the dialog draws its title row in the window
 * @param collapsibleNav whether navigation sections collapse
 * @param inlineReset whether rows show their own Reset button
 * @param hiddenKeys settings the look implies, which Settings does not show
 * @param kitFontSize whether the interface font size's default is the kit's body size, which it is when the kit names a
 *     UI face; otherwise it is the platform's size
 */
public record SettingsChrome(boolean integratedTitle, boolean collapsibleNav, boolean inlineReset, Set<String> hiddenKeys, boolean kitFontSize) {
    /** Copies the set. */
    public SettingsChrome { hiddenKeys = Set.copyOf(hiddenKeys); }

    /**
     * The label of the interface font size's default switch.
     *
     * @return "Use the default font size" for the kit's size, else "Use platform font size"
     */
    public String fontRowCopy() { return kitFontSize ? "Use the default font size" : "Use platform font size"; }
}
