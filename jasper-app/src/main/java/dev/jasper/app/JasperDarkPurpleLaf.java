package dev.jasper.app;

import com.formdev.flatlaf.FlatDarkLaf;

/** A separate defaults layer keeps the original jasper-dark chrome selectable. */
final class JasperDarkPurpleLaf extends FlatDarkLaf {
    @Override public String getName() { return "Jasper Dark Purple"; }
    @Override public String getDescription() { return "Eclipse-inspired dark purple Jasper theme"; }
}
