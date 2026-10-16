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
                emit(editor,new UiEvent.Edited(ServerEditor.FOLDER,root.resolve("not-present").toString()));
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
    @Test void explicitBrowseCancellationKeepsLongDraftAndPerformsNoImplicitChooserRead() {
        UiTestAccess.edt(()->{
            int[] requests={0};var kit=new FakeKit("dev.jasper.fileserver");
            try(var editor=new ServerEditor(kit,List.of(),Optional.empty(),false,Set.of(Protocol.HTTP),List.of(),false,id->Optional.empty(),
                ()->CompletableFuture.completedFuture(Optional.empty()),()->{requests[0]++;return Optional.empty();},ServerEditor.KeySources.NONE,false,d->{},()->{})) {
                String longPath=root.resolve("very-long-synthetic-folder/another-segment/still-editable").toString();
                emit(editor,new UiEvent.Edited(ServerEditor.FOLDER,longPath));assertThat(requests[0]).isZero();
                emit(editor,new UiEvent.Action(ServerEditor.CHOOSE_FOLDER));assertThat(requests[0]).isEqualTo(1);
                assertThat(((Nodes.Field)node(editor,ServerEditor.FOLDER)).value()).isEqualTo(longPath);
            }
        });
    }
    private ServerEditor editor(Optional<ServerDefinition> initial) {
        return new ServerEditor(new FakeKit("dev.jasper.fileserver"), List.of(),initial,initial.isPresent(),EnumSet.allOf(Protocol.class),List.of(),true,
            id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()),Optional::empty,ServerEditor.KeySources.NONE,false,d -> {},() -> {});
    }
    private static Nodes.Node node(ServerEditor editor,String id) {return UiTestAccess.node((FakeKitView)editor.view(),id);}
    private static void emit(ServerEditor editor,UiEvent event) {((FakeKitView)editor.view()).emit(event);}
}
