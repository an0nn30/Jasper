package dev.jasper.app.architecture;

import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PackagedResourcesTest {
    @Test void productionJarPreservesResourcesAndContainsNoBuddyOrTestImplementation() throws Exception {
        try (var jar = new JarFile(System.getProperty("jasper.appJar"))) {
            for (String name : java.util.List.of(
                "dev/jasper/app/icons/LICENSE.txt",
                "dev/jasper/app/icons/SOURCE.txt",
                "dev/jasper/app/icons/app-window.svg",
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
                "dev/jasper/app/icons/bookmark.svg",
                "dev/jasper/app/icons/columns-2.svg",
                "dev/jasper/app/icons/command.svg",
                "dev/jasper/app/icons/history.svg",
                "dev/jasper/app/icons/maximize.svg",
                "dev/jasper/app/icons/refresh.svg",
                "dev/jasper/app/icons/search.svg",
                "dev/jasper/app/icons/settings.svg",
                "dev/jasper/app/icons/square-plus.svg",
                "dev/jasper/app/icons/title/SOURCE.txt",
                "dev/jasper/app/icons/title/plus.svg",
                "dev/jasper/app/icons/title/terminal-2.svg",
                "dev/jasper/app/icons/title/x.svg",
                "dev/jasper/app/shell-integration/bash/rc.bash",
                "dev/jasper/app/shell-integration/fish/fish/vendor_conf.d/jasper.fish",
                "dev/jasper/app/shell-integration/jasper.bash",
                "dev/jasper/app/shell-integration/jasper.fish",
                "dev/jasper/app/shell-integration/jasper.zsh",
                "dev/jasper/app/shell-integration/zsh/.zlogin",
                "dev/jasper/app/shell-integration/zsh/.zprofile",
                "dev/jasper/app/shell-integration/zsh/.zshenv",
                "dev/jasper/app/shell-integration/zsh/.zshrc",
                "dev/jasper/app/themes/FlatDarkLaf.properties",
                "dev/jasper/app/themes/FlatLaf.properties",
                "dev/jasper/app/themes/FlatLightLaf.properties"
            )) assertThat(jar.getJarEntry(name)).as(name).isNotNull();
            var names = jar.stream().filter(entry -> !entry.isDirectory()).map(java.util.jar.JarEntry::getName).toList();
            assertThat(names).noneMatch(n -> n.startsWith("dev/jasper/buddy/") || n.startsWith("dev/jasper/app/buddy/")
                || n.contains("TestSupport") || n.contains("Preview.class") || n.contains("ControlledSessionChild"));
            var defaults = jar.getJarEntry("dev/jasper/app/themes/FlatLaf.properties");
            var values = new java.util.Properties();
            try (var stream = jar.getInputStream(defaults)) { values.load(stream); }
            String border = values.getProperty("SplitPaneDivider.border");
            assertThat(jar.getJarEntry(border.replace('.', '/') + ".class")).isNotNull();
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
