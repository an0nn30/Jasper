package dev.jasper.app.shortcuthelp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KeymapDraftTest {
    private static final Map<String, String> SAVED = Map.of("copy", "cmd+shift+c");

    @Test void stagingMergesOverTheSavedOverridesAndDropsEditsThatChangeNothing() {
        var draft = new KeymapDraft();
        draft.stage(Map.of("new_tab", Optional.of("cmd+y")), SAVED);
        draft.stage(Map.of("copy", Optional.empty()), SAVED);
        assertThat(draft.modified()).isTrue();
        assertThat(draft.overrides(SAVED)).isEqualTo(Map.of("new_tab", "cmd+y"));
        draft.stage(Map.of("paste", Optional.empty(), "copy", Optional.of("cmd+shift+c")), SAVED);
        assertThat(draft.edits()).isEqualTo(Map.of("new_tab", Optional.of("cmd+y")));
    }

    @Test void previewStagesNothingAndSavingForgetsOnlyWhatWasSaved() {
        var draft = new KeymapDraft();
        draft.stage(Map.of("new_tab", Optional.of("cmd+y")), SAVED);
        assertThat(draft.preview(SAVED, Map.of("find", Optional.of("cmd+j"))))
            .isEqualTo(Map.of("copy", "cmd+shift+c", "new_tab", "cmd+y", "find", "cmd+j"));
        assertThat(draft.edits()).containsOnlyKeys("new_tab");
        var sent = draft.edits();
        draft.stage(Map.of("find", Optional.of("cmd+j")), SAVED);
        draft.saved(sent);
        assertThat(draft.edits()).isEqualTo(Map.of("find", Optional.of("cmd+j")));
        draft.prune(Map.of("find", "cmd+j"));
        assertThat(draft.modified()).as("the saved file already holds it").isFalse();
        draft.stage(Map.of("copy", Optional.empty()), SAVED);
        draft.clear();
        assertThat(draft.modified()).isFalse();
    }
}
