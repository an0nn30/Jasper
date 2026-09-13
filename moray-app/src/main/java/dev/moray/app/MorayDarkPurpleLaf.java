package dev.moray.app;

import com.formdev.flatlaf.FlatDarkLaf;

/** A separate defaults layer keeps the original moray-dark chrome selectable. */
final class MorayDarkPurpleLaf extends FlatDarkLaf {
    @Override public String getName() { return "Moray Dark Purple"; }
    @Override public String getDescription() { return "Eclipse-inspired dark purple Moray theme"; }
}
