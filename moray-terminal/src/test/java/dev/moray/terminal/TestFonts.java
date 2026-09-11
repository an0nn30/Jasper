package dev.moray.terminal;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.Optional;

/** Fonts that exist only on some machines; tests that need them skip elsewhere. */
final class TestFonts {
    /** The Nerd Font git icon, missing from JetBrains Mono. */
    static final int GIT_ICON = 0xF113;

    private TestFonts() {
    }

    /** An installed Nerd Font family that has the git icon, if any (CI machines usually have none). */
    static Optional<String> nerdFont() {
        return Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            .filter(name -> name.contains("Nerd Font"))
            .filter(name -> new Font(name, Font.PLAIN, 14).canDisplay(GIT_ICON))
            .findFirst();
    }
}
