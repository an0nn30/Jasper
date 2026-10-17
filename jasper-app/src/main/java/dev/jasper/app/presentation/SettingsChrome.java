package dev.jasper.app.presentation;

import java.util.Objects;
import java.util.Set;

/**
 * The Settings dialog's projection (spec 9.1 and 12.1).
 *
 * @param integratedTitle whether the dialog draws its title row in the window
 * @param collapsibleNav whether navigation sections collapse
 * @param inlineReset whether rows show their own Reset button
 * @param hiddenKeys settings the look implies, which Settings does not show
 * @param fontRowCopy the label of the interface font size's default switch
 */
public record SettingsChrome(boolean integratedTitle, boolean collapsibleNav, boolean inlineReset, Set<String> hiddenKeys, String fontRowCopy) {
    /** Copies the set. */
    public SettingsChrome { hiddenKeys = Set.copyOf(hiddenKeys); Objects.requireNonNull(fontRowCopy); }
}
