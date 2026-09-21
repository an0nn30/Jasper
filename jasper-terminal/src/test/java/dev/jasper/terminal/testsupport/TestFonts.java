package dev.jasper.terminal.testsupport;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.Optional;

/** Fonts that exist only on some machines; tests that need them skip elsewhere. */
public final class TestFonts {
    /** The Nerd Font git icon, missing from JetBrains Mono. */
    public static final int GIT_ICON = 0xF113;

    private TestFonts() {
    }

    /** An installed Nerd Font family that has the git icon, if any (CI machines usually have none). */
    public static Optional<String> nerdFont() {
        return Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            .filter(name -> name.contains("Nerd Font"))
            .filter(name -> new Font(name, Font.PLAIN, 14).canDisplay(GIT_ICON))
            .findFirst();
    }
}
