package dev.jasper.sdk.ui;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FileAssociationsValuesTest {
    @Test void nullComponentsAreRefused() {
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.Handler.Other(null));
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.TypeState(null, new FileAssociations.Handler.None(), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.TypeState("t", null, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.TypeState("t", new FileAssociations.Handler.None(), null));
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.FileTypeStatus(null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.FileTypeStatus(".txt", null));
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.Claimability.Claimable(null, true));
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.Claimability.NotClaimable(null));
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.Outcome.Refused(null));
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.Outcome.Failed(null));
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.TypeResult(null, "t", new FileAssociations.Outcome.Done()));
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.TypeResult(".txt", null, new FileAssociations.Outcome.Done()));
        assertThatNullPointerException().isThrownBy(() -> new FileAssociations.TypeResult(".txt", "t", null));
    }

    @Test void listsAreCopied() {
        var states = Stream.of(new FileAssociations.TypeState("t", new FileAssociations.Handler.Jasper(), Optional.empty())).collect(Collectors.toCollection(java.util.ArrayList::new));
        var status = new FileAssociations.FileTypeStatus(".txt", states);
        states.clear();
        assertThat(status.types()).hasSize(1);
        var types = Stream.of("public.plain-text").collect(Collectors.toCollection(java.util.ArrayList::new));
        var claimable = new FileAssociations.Claimability.Claimable(types, false);
        types.clear();
        assertThat(claimable.types()).containsExactly("public.plain-text");
    }

    @Test void aClaimableExtensionNeedsATypeButAStatusMayHaveNone() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FileAssociations.Claimability.Claimable(List.of(), false));
        assertThat(new FileAssociations.FileTypeStatus(".zzz", List.of()).types()).isEmpty();
        assertThat(new FileAssociations.Claimability.NotClaimable(FileAssociations.Refusal.NOT_TEXT).refusal()).isEqualTo(FileAssociations.Refusal.NOT_TEXT);
    }
}
