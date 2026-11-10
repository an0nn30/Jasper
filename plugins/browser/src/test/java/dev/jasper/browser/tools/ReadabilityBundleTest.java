package dev.jasper.browser.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bundled Readability.js (plan 7's Task 5, ruling D2): {@code package/Readability.js} of npm's
 * {@code @mozilla/readability} 0.6.0 tarball, unmodified, which reader mode runs in a page ({@link ToolScripts}).
 */
class ReadabilityBundleTest {
    /** The SHA-256 of the tarball's {@code package/Readability.js}, 89,980 bytes. */
    static final String READABILITY_SHA256 = "34dcab3d0832d0019f02990eed6b6124e029e8c32b9f0c6f2550544ff8dff174";
    private static final String RESOURCE = "/dev/jasper/browser/third_party/Readability.js";

    @Test void theBundledFileIsTheTarballsUnmodified() throws IOException, NoSuchAlgorithmException {
        byte[] file = bundled();
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file))).isEqualTo(READABILITY_SHA256);
        assertThat(file).hasSize(89_980);
    }

    /** Its Apache License 2.0 header, which the notices' component names, comes first. */
    @Test void itStartsWithItsApacheLicenseHeader() throws IOException {
        assertThat(new String(bundled(), StandardCharsets.UTF_8)).startsWith("/*\n * Copyright (c) 2010 Arc90 Inc\n *\n"
            + " * Licensed under the Apache License, Version 2.0 (the \"License\");\n");
    }

    /**
     * The wrapper {@link ToolScripts} puts it in relies on its last statement: it exports itself only where
     * {@code module} is an object, so inside a function whose {@code module} is undefined it touches nothing of the
     * page's. A newer Readability.js must keep that guard.
     */
    @Test void itEndsWithTheModuleGuardTheWrapperReliesOn() throws IOException {
        String script = new String(bundled(), StandardCharsets.UTF_8);
        assertThat(script).endsWith("module.exports = Readability;\n}\n");
        String guard = script.substring(script.lastIndexOf("if (typeof module === \"object\") {"));
        assertThat(guard).as("the last statement").isEqualTo("if (typeof module === \"object\") {\n"
            + "  /* eslint-disable-next-line no-redeclare */\n  /* global module */\n  module.exports = Readability;\n}\n");
        assertThat(script).as("its one declaration").contains("\nfunction Readability(doc, options) {\n");
    }

    static byte[] bundled() throws IOException {
        try (InputStream in = ReadabilityBundleTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as(RESOURCE).isNotNull();
            return in.readAllBytes();
        }
    }
}
