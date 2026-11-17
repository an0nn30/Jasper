package dev.jasper.editor.associations;

import dev.jasper.sdk.ui.FileAssociations.FileTypeStatus;
import dev.jasper.sdk.ui.FileAssociations.Handler;
import dev.jasper.sdk.ui.FileAssociations.Refusal;
import dev.jasper.sdk.ui.FileAssociations.TypeState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FileTypeRowStateTest {
    private static final Handler JASPER = new Handler.Jasper();
    private static final Handler NONE = new Handler.None();
    private static final Handler UNKNOWN = new Handler.Unknown();

    private static Handler other(String name) { return new Handler.Other(name); }

    private static TypeState type(String type, Handler handler) { return new TypeState(type, handler, Optional.empty()); }

    private static TypeState type(String type, Handler handler, Handler previous) { return new TypeState(type, handler, Optional.of(previous)); }

    /** The two-extension row: .yaml and .yml share the first type, and .yml has its own second one. */
    private static List<FileTypeStatus> yaml(TypeState shared, TypeState own) {
        return List.of(new FileTypeStatus(".yaml", List.of(shared)), new FileTypeStatus(".yml", List.of(shared, own)));
    }

    private static FileTypeRowState fold(List<FileTypeStatus> statuses) {
        return FileTypeRowState.of(statuses, Map.of(), false);
    }

    @Test void everyTypeJasperIsJasper() {
        assertThat(fold(yaml(type("a", JASPER), type("b", JASPER)))).isEqualTo(new FileTypeRowState.Jasper(false));
    }

    @Test void someJasperIsPartlyEvenWithUnknownsOrOthers() {
        assertThat(fold(yaml(type("a", JASPER), type("b", other("Xcode"))))).isEqualTo(new FileTypeRowState.Partly(false));
        assertThat(fold(yaml(type("a", JASPER), type("b", UNKNOWN)))).isEqualTo(new FileTypeRowState.Partly(false));
    }

    @Test void unknownWithNothingJasperIsUnknown() {
        assertThat(fold(yaml(type("a", UNKNOWN), type("b", other("Xcode"))))).isEqualTo(new FileTypeRowState.Unknown());
    }

    @Test void allNoneIsNone() {
        assertThat(fold(yaml(type("a", NONE), type("b", NONE)))).isEqualTo(new FileTypeRowState.None());
    }

    @Test void oneSharedOtherNameIsOtherAndNoneTypesAreIgnored() {
        assertThat(fold(yaml(type("a", other("Xcode")), type("b", other("Xcode"))))).isEqualTo(new FileTypeRowState.Other("Xcode"));
        assertThat(fold(yaml(type("a", other("Xcode")), type("b", NONE)))).isEqualTo(new FileTypeRowState.Other("Xcode"));
    }

    @Test void differentOtherNamesAreSeveral() {
        assertThat(fold(yaml(type("a", other("Xcode")), type("b", other("TextEdit"))))).isEqualTo(new FileTypeRowState.Several());
    }

    @Test void noClaimableExtensionIsNotClaimableWithTheFirstRefusal() {
        var statuses = List.of(new FileTypeStatus(".yaml", List.of()), new FileTypeStatus(".yml", List.of()));
        assertThat(FileTypeRowState.of(statuses, Map.of(".yml", Refusal.NOT_TEXT, ".yaml", Refusal.NO_SYSTEM_TYPE), false))
            .isEqualTo(new FileTypeRowState.NotClaimable(Refusal.NO_SYSTEM_TYPE));
        assertThat(FileTypeRowState.of(statuses, Map.of(".yml", Refusal.NOT_TEXT), false))
            .isEqualTo(new FileTypeRowState.NotClaimable(Refusal.NOT_TEXT));
    }

    @Test void anUnclaimableExtensionBesideAClaimableOneIsIgnored() {
        var statuses = List.of(new FileTypeStatus(".yaml", List.of()), new FileTypeStatus(".yml", List.of(type("b", JASPER))));
        assertThat(FileTypeRowState.of(statuses, Map.of(".yaml", Refusal.NOT_TEXT), false)).isEqualTo(new FileTypeRowState.Jasper(false));
    }

    @Test void restorableNeedsASavedPreviousAppThatIsNotNone() {
        assertThat(fold(yaml(type("a", JASPER, other("Xcode")), type("b", JASPER)))).isEqualTo(new FileTypeRowState.Jasper(true));
        assertThat(fold(yaml(type("a", JASPER, other("Xcode")), type("b", other("TextEdit"))))).isEqualTo(new FileTypeRowState.Partly(true));
    }

    @Test void aSavedNoneCountsOnlyWhereNoneIsRestorable() {
        var statuses = yaml(type("a", JASPER, NONE), type("b", JASPER));
        assertThat(FileTypeRowState.of(statuses, Map.of(), false)).isEqualTo(new FileTypeRowState.Jasper(false));
        assertThat(FileTypeRowState.of(statuses, Map.of(), true)).isEqualTo(new FileTypeRowState.Jasper(true));
    }
}
