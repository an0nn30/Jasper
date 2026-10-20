package dev.jasper.editor.external;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KnownEditorsTest {
    /** The plan's known-editor table, checked against each editor's documentation on 2026-10-14, row by row. */
    @Test void theTableIsThePlansKnownEditorsInOrder() {
        assertThat(KnownEditors.ALL).extracting(editor -> editor.id() + "|" + editor.name() + "|" + editor.kind() + "|"
                + String.join(",", editor.executables()) + "|" + String.join(",", editor.macApps()) + "|"
                + String.join(" ", editor.extraArgs()) + "|" + String.join(" ", editor.waitArgs()) + "|" + editor.lineForm())
            .containsExactly(
                "nvim|Neovim|TERMINAL|nvim||||PLUS_LINE_BEFORE",
                "vim|Vim|TERMINAL|vim||||PLUS_LINE_BEFORE",
                "vi|vi|TERMINAL|vi||||PLUS_LINE_BEFORE",
                "nano|nano|TERMINAL|nano||||PLUS_LINE_BEFORE",
                "micro|micro|TERMINAL|micro||||PLUS_LINE_BEFORE",
                "helix|Helix|TERMINAL|hx,helix||||FILE_COLON_LINE",
                "emacs|Emacs|TERMINAL|emacs||-nw||PLUS_LINE_BEFORE",
                "code|Visual Studio Code|APP|code|Visual Studio Code.app/Contents/Resources/app/bin/code||--wait|DASH_G_FILE_COLON_LINE",
                "cursor|Cursor|APP|cursor|Cursor.app/Contents/Resources/app/bin/cursor||--wait|DASH_G_FILE_COLON_LINE",
                "zed|Zed|APP|zed|Zed.app/Contents/MacOS/cli||--wait|FILE_COLON_LINE",
                "sublime|Sublime Text|APP|subl|Sublime Text.app/Contents/SharedSupport/bin/subl||-w|FILE_COLON_LINE",
                "bbedit|BBEdit|APP|bbedit|||--wait|PLUS_LINE_BEFORE",
                "textmate|TextMate|APP|mate|TextMate.app/Contents/Resources/mate||-w|DASH_L_LINE_BEFORE",
                "idea|IntelliJ IDEA|APP|idea|||--wait|LINE_OPTION_BEFORE",
                "notepadpp|Notepad++|APP|notepad++|||-multiInst -nosession|DASH_N_LINE_BEFORE",
                "notepad|Notepad|APP|notepad||||NONE");
        assertThat(KnownEditors.ALL.stream().map(KnownEditor::lineForm).distinct())
            .as("every line form is used").containsExactlyInAnyOrder(KnownEditor.LineForm.values());
    }

    @Test void anEditorIsLookedUpByItsId() {
        assertThat(KnownEditors.byId("code")).map(KnownEditor::name).contains("Visual Studio Code");
        assertThat(KnownEditors.byId("notepadpp")).map(KnownEditor::name).contains("Notepad++");
        assertThat(KnownEditors.byId("jasper")).as("a choice, not an editor").isEmpty();
        assertThat(KnownEditors.byId("Code")).as("ids are exact").isEmpty();
    }
}
