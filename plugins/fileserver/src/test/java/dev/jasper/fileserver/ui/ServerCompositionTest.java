package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.*;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ServerCompositionTest {
    @TempDir Path root;
    /** What Choose… answers; nothing (a cancelled chooser) when null. */
    private Path nextFolder;
    @Test void groupedFormUsesRealProtocolsConditionalAccountAndExplicitSubmissionPolicy() {
        UiTestAccess.edt(() -> {
            try (var editor = editor(Optional.empty())) {
                var form = (Nodes.Form) ((FakeKitView) editor.view()).spec().root();
                assertThat(form.sections()).filteredOn(n -> n instanceof Nodes.Section)
                    .extracting(n -> ((Nodes.Section)n).title()).containsExactly("Server", "Access", "Security & Startup");
                assertThat(form.submission()).isEqualTo(Nodes.FormSubmission.NONE);
                var protocol = (Nodes.Choice) node(editor, ServerEditor.PROTOCOL);
                assertThat(protocol.kind()).isEqualTo(Nodes.ChoiceKind.EXCLUSIVE);
                assertThat(protocol.options()).extracting(Option::id).containsExactly("HTTP", "FTP", "SFTP", "TFTP");
                assertThat(node(editor, "server.account.row").state().visible()).isFalse();
                assertThat(((Nodes.Button)node(editor, ServerEditor.SAVE)).kind()).isEqualTo(Nodes.ButtonKind.PRIMARY);
                assertThat(node(editor, ServerEditor.SAVE).state().enabled()).isFalse();
                emit(editor, new UiEvent.Selected(ServerEditor.LOGIN, Set.of("vault")));
                assertThat(node(editor,"server.account.row").state().visible()).isTrue();
                assertThat(((Nodes.Choice)node(editor, ServerEditor.CHOOSE_ACCOUNT)).face().orElseThrow().density()).isEqualTo(Nodes.PickerDensity.DEFAULT);
                emit(editor, new UiEvent.Selected(ServerEditor.PROTOCOL, Set.of("TFTP")));
                assertThat(node(editor,"server.account.row").state().visible()).isFalse();
                assertThat(((Nodes.Text)node(editor,"server.preview")).text()).isEqualTo("tftp://127.0.0.1:6969");
                // Spec 8.2: TFTP's one login is a fact, not a choice: a read-only field in the choice's place.
                assertThat(node(editor, ServerEditor.LOGIN)).isInstanceOfSatisfying(Nodes.Field.class, login -> {
                    assertThat(login.readOnly()).isTrue();
                    assertThat(login.value()).isEqualTo("No login (TFTP has none)");
                    assertThat(login.presentation()).isEqualTo(Nodes.FieldPresentation.EMBEDDED);
                });
                emit(editor, new UiEvent.Selected(ServerEditor.PROTOCOL, Set.of("HTTP")));
                assertThat(node(editor, ServerEditor.LOGIN)).isInstanceOf(Nodes.Choice.class);
            }
        });
    }
    @Test void cheapReadinessKeepsFinalDirectoryValidationAndRequiredTlsIsNeverSilentlyRemoved() {
        UiTestAccess.edt(() -> {
            var initial = new ServerDefinition(UUID.randomUUID(), "files", Protocol.FTP, root, Bind.LOOPBACK,2121,
                Optional.empty(),false,Login.SESSION,List.of(),true,true,false);
            try(var editor = editor(Optional.of(initial))) {
                emit(editor,new UiEvent.Toggled(ServerEditor.TLS,false));
                assertThat(((Nodes.Toggle)node(editor,ServerEditor.REQUIRE_TLS)).checked()).isTrue();
                assertThat(node(editor,ServerEditor.SAVE).state().enabled()).isFalse();
                assertThatThrownBy(editor::build).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Require TLS");
                emit(editor,new UiEvent.Toggled(ServerEditor.TLS,true));
                nextFolder=root.resolve("not-present");emit(editor,new UiEvent.Action(ServerEditor.CHOOSE_FOLDER));
                assertThat(node(editor,ServerEditor.SAVE).state().enabled()).as("readiness does not inspect the filesystem").isTrue();
                assertThatThrownBy(editor::build).hasMessageContaining("does not exist");
            }
        });
    }
    @Test void previewUsesHttpsOnlyForHttpAndKeepsExplicitTlsFtpScheme() {
        UiTestAccess.edt(() -> {
            try (var editor = editor(Optional.empty())) {
                emit(editor, new UiEvent.Toggled(ServerEditor.TLS, true));
                assertThat(((Nodes.Text)node(editor,"server.preview")).text()).isEqualTo("https://127.0.0.1:8080");
                emit(editor, new UiEvent.Selected(ServerEditor.PROTOCOL, Set.of("FTP")));
                assertThat(((Nodes.Text)node(editor,"server.preview")).text()).isEqualTo("ftp://127.0.0.1:2121");
            }
        });
    }
    @Test void explicitBrowseCancellationKeepsTheChosenFolderAndPerformsNoImplicitChooserRead() {
        UiTestAccess.edt(()->{
            String chosenPath=root.resolve("very-long-synthetic-folder/another-segment/still-chosen").toString();
            var answers=new java.util.ArrayDeque<Optional<Path>>(List.of(Optional.of(Path.of(chosenPath)),Optional.empty()));
            int[] requests={0};var kit=new FakeKit("dev.jasper.fileserver");
            try(var editor=new ServerEditor(kit,List.of(),Optional.empty(),false,Set.of(Protocol.HTTP),List.of(),false,id->Optional.empty(),
                ()->CompletableFuture.completedFuture(Optional.empty()),()->{requests[0]++;return answers.remove();},ServerEditor.KeySources.NONE,false,d->{},()->{})) {
                // One offered protocol is a fact, not a choice (spec 8.2: an EXCLUSIVE choice has 2 to 4 options).
                assertThat(node(editor, ServerEditor.PROTOCOL)).isInstanceOfSatisfying(Nodes.Field.class, protocol -> {
                    assertThat(protocol.readOnly()).isTrue();
                    assertThat(protocol.value()).isEqualTo("HTTP");
                });
                assertThat(requests[0]).as("opening the editor reads no folder").isZero();
                emit(editor,new UiEvent.Action(ServerEditor.CHOOSE_FOLDER));
                assertThat(((Nodes.Field)node(editor,ServerEditor.FOLDER)).value()).isEqualTo(chosenPath);
                emit(editor,new UiEvent.Action(ServerEditor.CHOOSE_FOLDER));assertThat(requests[0]).isEqualTo(2);
                assertThat(((Nodes.Field)node(editor,ServerEditor.FOLDER)).value()).as("a cancelled Choose… keeps the folder").isEqualTo(chosenPath);
                assertThat(((Nodes.Field)node(editor,ServerEditor.FOLDER)).readOnly()).isTrue();
            }
        });
    }
    private ServerEditor editor(Optional<ServerDefinition> initial) {
        return new ServerEditor(new FakeKit("dev.jasper.fileserver"), List.of(),initial,initial.isPresent(),EnumSet.allOf(Protocol.class),List.of(),true,
            id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()),()->Optional.ofNullable(nextFolder),ServerEditor.KeySources.NONE,false,d -> {},() -> {});
    }
    private static Nodes.Node node(ServerEditor editor,String id) {return UiTestAccess.node((FakeKitView)editor.view(),id);}
    private static void emit(ServerEditor editor,UiEvent event) {((FakeKitView)editor.view()).emit(event);}
}
