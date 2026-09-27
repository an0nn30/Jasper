package dev.jasper.editor.syntax;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.*;

class SyntaxDetectorTest {
    @ParameterizedTest
    @CsvSource({
        "config.toml,text/toml", "Cargo.lock,text/toml", "Pipfile,text/toml", "poetry.lock,text/toml",
        "package.json,text/json", "settings.jsonc,text/jshintrc",
        "app.py,text/python", "types.pyi,text/python", "gui.pyw,text/python",
        "deploy.sh,text/unix", "x.bash,text/unix", "x.zsh,text/unix", ".bashrc,text/unix", ".bash_profile,text/unix",
        ".profile,text/unix", ".zshrc,text/unix",
        "ci.yml,text/yaml", "ci.yaml,text/yaml",
        "main.go,text/golang", "Dockerfile,text/dockerfile", "Makefile,text/makefile",
        "nginx.conf,text/plain", "README,text/plain"
    })
    void namesAndExtensions(String name, String style) {
        assertThat(SyntaxDetector.detect(name, "")).isEqualTo(style);
    }

    @Test void aShebangDecidesOnlyWhenTheNameHasNoRecognisedExtension() {
        assertThat(SyntaxDetector.detect("deploy", "#!/usr/bin/env python3")).isEqualTo(SYNTAX_STYLE_PYTHON);
        assertThat(SyntaxDetector.detect("run", "#!/bin/bash -e")).isEqualTo(SYNTAX_STYLE_UNIX_SHELL);
        assertThat(SyntaxDetector.detect("run", "#!/usr/bin/env -S zsh -f")).isEqualTo(SYNTAX_STYLE_UNIX_SHELL);
        assertThat(SyntaxDetector.detect("tool.py", "#!/bin/sh")).as("the extension wins").isEqualTo(SYNTAX_STYLE_PYTHON);
        assertThat(SyntaxDetector.detect("notes.txt", "#!/usr/bin/python")).isEqualTo(SYNTAX_STYLE_PYTHON);
        assertThat(SyntaxDetector.detect("run", "#!/usr/bin/perl")).isEqualTo(SYNTAX_STYLE_NONE);
        assertThat(SyntaxDetector.detect("run", "echo hi")).isEqualTo(SYNTAX_STYLE_NONE);
    }
}
