package dev.jasper.app.architecture;

import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PackagedResourcesTest {
    @Test void bundledTerminalFontsLoadFromTheProductionJarWithTheirNotices() throws Exception {
        try (var jar = new JarFile(System.getProperty("jasper.appJar"))) {
            String root = "dev/jasper/app/fonts/cousine/";
            for (String style : java.util.List.of("Regular", "Bold", "Italic", "BoldItalic")) {
                var entry = jar.getJarEntry(root + "CousineNerdFontMono-" + style + ".ttf");
                assertThat(entry).as(style + " is shipped").isNotNull();
                try (var stream = jar.getInputStream(entry)) {
                    var font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream);
                    assertThat(font.getFamily(java.util.Locale.ROOT)).isEqualTo("Cousine Nerd Font Mono");
                    assertThat(font.getPSName()).isEqualTo("CousineNFM-" + style);
                    assertThat(font.canDisplay(0xE0B0)).as("Powerline glyph in " + style).isTrue();
                    assertThat(font.canDisplay(0xF120)).as("Nerd Font terminal glyph in " + style).isTrue();
                }
            }
            for (String notice : java.util.List.of("OFL.txt", "NERD-FONTS-LICENSE.txt", "README.md", "NOTICE.md"))
                assertThat(jar.getJarEntry(root + notice)).as(notice + " travels with the fonts").isNotNull();
        }
    }

    @Test void productionJarPreservesResourcesAndContainsNoBuddyOrTestImplementation() throws Exception {
        try (var jar = new JarFile(System.getProperty("jasper.appJar"))) {
            for (String name : java.util.List.of(
                "dev/jasper/app/icons/intellij/terminal/toolwindow/terminal.svg",
                "dev/jasper/app/icons/intellij/nodes/pluginLogo.svg",
                "dev/jasper/app/icons/intellij/nodes/pluginLogo_dark.svg",
                "dev/jasper/app/icons/intellij/assets.tsv",
                "dev/jasper/app/icons/intellij/LICENSE.txt",
                "dev/jasper/app/icons/intellij/NOTICE.txt",
                "dev/jasper/app/icons/intellij/SOURCE.md",
                "dev/jasper/app/icons/app/linux/icon-16.png",
                "dev/jasper/app/icons/app/linux/icon-24.png",
                "dev/jasper/app/icons/app/linux/icon-32.png",
                "dev/jasper/app/icons/app/linux/icon-48.png",
                "dev/jasper/app/icons/app/linux/icon-64.png",
                "dev/jasper/app/icons/app/linux/icon-96.png",
                "dev/jasper/app/icons/app/linux/icon-128.png",
                "dev/jasper/app/icons/app/linux/icon-256.png",
                "dev/jasper/app/icons/app/linux/icon-512.png",
                "dev/jasper/app/icons/app/linux/icon-1024.png",
                "dev/jasper/app/icons/app/macos/icon-1024.png",
                "dev/jasper/app/icons/app/macos/icon-128.png",
                "dev/jasper/app/icons/app/macos/icon-16.png",
                "dev/jasper/app/icons/app/macos/icon-256.png",
                "dev/jasper/app/icons/app/macos/icon-32.png",
                "dev/jasper/app/icons/app/macos/icon-512.png",
                "dev/jasper/app/icons/app/macos/icon-64.png",
                "dev/jasper/app/icons/app/windows/icon-128.png",
                "dev/jasper/app/icons/app/windows/icon-16.png",
                "dev/jasper/app/icons/app/windows/icon-20.png",
                "dev/jasper/app/icons/app/windows/icon-24.png",
                "dev/jasper/app/icons/app/windows/icon-256.png",
                "dev/jasper/app/icons/app/windows/icon-30.png",
                "dev/jasper/app/icons/app/windows/icon-32.png",
                "dev/jasper/app/icons/app/windows/icon-36.png",
                "dev/jasper/app/icons/app/windows/icon-40.png",
                "dev/jasper/app/icons/app/windows/icon-48.png",
                "dev/jasper/app/icons/app/windows/icon-60.png",
                "dev/jasper/app/icons/app/windows/icon-64.png",
                "dev/jasper/app/icons/app/windows/icon-72.png",
                "dev/jasper/app/icons/app/windows/icon-80.png",
                "dev/jasper/app/icons/app/windows/icon-96.png",
                "dev/jasper/app/shell-integration/bash/rc.bash",
                "dev/jasper/app/shell-integration/fish/fish/vendor_conf.d/jasper.fish",
                "dev/jasper/app/shell-integration/jasper.bash",
                "dev/jasper/app/shell-integration/jasper.fish",
                "dev/jasper/app/shell-integration/jasper.zsh",
                "dev/jasper/app/shell-integration/zsh/.zlogin",
                "dev/jasper/app/shell-integration/zsh/.zprofile",
                "dev/jasper/app/shell-integration/zsh/.zshenv",
                "dev/jasper/app/shell-integration/zsh/.zshrc"
            )) assertThat(jar.getJarEntry(name)).as(name).isNotNull();
            var names = jar.stream().filter(entry -> !entry.isDirectory()).map(java.util.jar.JarEntry::getName).toList();
            assertThat(names).as("no theme resources: the built-in themes live in jasper-ui").noneMatch(n -> n.startsWith("dev/jasper/app/themes/"));
            assertThat(names.stream().filter(n -> n.startsWith("dev/jasper/app/icons/intellij/") && n.endsWith(".svg")).toList())
                .containsExactlyInAnyOrder("dev/jasper/app/icons/intellij/terminal/toolwindow/terminal.svg",
                    "dev/jasper/app/icons/intellij/nodes/pluginLogo.svg", "dev/jasper/app/icons/intellij/nodes/pluginLogo_dark.svg");
            assertThat(names).noneMatch(n -> n.startsWith("dev/jasper/buddy/") || n.startsWith("dev/jasper/app/buddy/")
                || n.contains("TestSupport") || n.contains("Preview.class") || n.contains("ControlledSessionChild"));
            assertThat(jar.getJarEntry("dev/jasper/app/themes/FlatLaf.properties")).isNull();
            String border = "dev.jasper.app.workspace.SplitDividerBorder";
            assertThat(jar.getJarEntry(border.replace('.', '/') + ".class")).isNotNull();
        }
    }
    @Test void theBuiltInThemesAndCatppuccinsLicenseAreTheUiJarsOnlyThemeResources() throws Exception {
        try (var jar = new JarFile(dev.jasper.ui.theme.GeneratedTheme.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath())) {
            assertThat(jar.stream().map(java.util.jar.JarEntry::getName)
                    .filter(name -> name.startsWith("dev/jasper/ui/theme/") && !name.endsWith("/") && !name.endsWith(".class")).toList())
                .containsExactlyInAnyOrder("dev/jasper/ui/theme/slate-cobalt.json", "dev/jasper/ui/theme/catppuccin-latte.json",
                    "dev/jasper/ui/theme/catppuccin-frappe.json", "dev/jasper/ui/theme/catppuccin-macchiato.json",
                    "dev/jasper/ui/theme/catppuccin-mocha.json", "dev/jasper/ui/theme/ubuntu-yaru.json",
                    "dev/jasper/ui/theme/catppuccin.LICENSE.txt");
            assertThat(jar.stream().map(java.util.jar.JarEntry::getName).toList())
                .noneMatch(name -> name.startsWith("dev/jasper/ui/themeimport/") || name.endsWith("THIRD-PARTY-THEMES.md"));
        }
        try (var jar = new JarFile(System.getProperty("jasper.appJar"))) {
            assertThat(jar.stream().map(java.util.jar.JarEntry::getName).toList())
                .noneMatch(name -> name.startsWith("dev/jasper/app/themeimport/") || name.endsWith("THIRD-PARTY-THEMES.md"));
        }
    }
    @Test void libraryJarsDoNotContainApplicationClasses() throws Exception {
        for (String property : java.util.List.of("jasper.buddyJar", "jasper.terminalJar")) {
            try (var jar = new JarFile(System.getProperty(property))) {
                assertThat(jar.stream().filter(entry -> !entry.isDirectory()).map(java.util.jar.JarEntry::getName).toList())
                    .noneMatch(n -> n.startsWith("dev/jasper/app/") || n.contains("TestSupport"));
            }
        }
    }
}
