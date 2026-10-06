package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.testsupport.Kits;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

/** The tunnel editor on the testkit fake (Plan 2 Task 8b). */
class TunnelEditorTest {
    final RemoteHost prod = RemoteHost.create("prod", "db.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final RemoteHost web = RemoteHost.create("web", "web.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final List<Tunnel> saved = new ArrayList<>();
    int cancelled;

    TunnelEditor editor(List<Tunnel> others, Optional<Tunnel> editing, Optional<UUID> preferred) {
        return editor(new FakeKit("dev.jasper.remote"), others, editing, preferred);
    }

    TunnelEditor editor(UiKit kit, List<Tunnel> others, Optional<Tunnel> editing, Optional<UUID> preferred) {
        return new TunnelEditor(kit, List.of(prod, web), others, editing, preferred, saved::add, () -> cancelled++);
    }

    static String text(TunnelEditor editor, String id) { return node(editor.view(), id, Nodes.Text.class).text(); }
    static boolean visible(TunnelEditor editor, String id) { return find(editor.view(), id).orElseThrow().state().visible(); }
    static Nodes.Field field(TunnelEditor editor, String id) { return node(editor.view(), id, Nodes.Field.class); }
    static Nodes.Choice choice(TunnelEditor editor, String id) { return node(editor.view(), id, Nodes.Choice.class); }
    static Nodes.Toggle allowOthers(TunnelEditor editor) { return node(editor.view(), TunnelEditor.ALLOW_OTHERS, Nodes.Toggle.class); }
    static void kind(TunnelEditor editor, String option) { emit(editor.view(), new UiEvent.Selected(TunnelEditor.TYPE, Set.of(option))); }
    static void allow(TunnelEditor editor) { emit(editor.view(), new UiEvent.Toggled(TunnelEditor.ALLOW_OTHERS, !allowOthers(editor).checked())); }

    @Test void createsALocalTunnelThroughTheChosenHost() throws Exception {
        edt(() -> {
            var editor = editor(List.of(), Optional.empty(), Optional.of(web.id()));
            assertThat(choice(editor, TunnelEditor.HOST).selectedId()).isEqualTo(web.id().toString());
            assertThat(choice(editor, TunnelEditor.HOST).options()).extracting(o -> o.label()).containsExactly("prod — deploy@db.example:22", "web — deploy@web.example:22");
            assertThat(choice(editor, TunnelEditor.TYPE).selectedId()).isEqualTo(TunnelEditor.LOCAL);
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).value()).isEqualTo("localhost");
            assertThat(button(editor.view(), TunnelEditor.SAVE).label()).isEqualTo("Add Tunnel");
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(editor.message()).isEqualTo("A tunnel needs a name");
            assertThat(text(editor, TunnelEditor.MESSAGE)).isEqualTo("A tunnel needs a name");
            type(editor.view(), TunnelEditor.NAME, "pg");
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(editor.message()).isEqualTo("The listen port must be a number from 0 to 65535");
            type(editor.view(), TunnelEditor.LISTEN_PORT, "5432");
            type(editor.view(), TunnelEditor.FORWARD_HOST, "db.internal");
            type(editor.view(), TunnelEditor.FORWARD_PORT, "5432");
            assertThat(text(editor, TunnelEditor.PREVIEW)).isEqualTo("localhost:5432 on this computer → db.internal:5432 as seen from web");
            assertThat(text(editor, TunnelEditor.FORWARD_LABEL)).isEqualTo("Forward to (as seen from web)");
            assertThat(text(editor, TunnelEditor.LISTEN_LABEL)).isEqualTo("Listen on (this computer)");
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(saved).singleElement().satisfies(tunnel -> {
                assertThat(tunnel.name()).isEqualTo("pg");
                assertThat(tunnel.hostId()).isEqualTo(web.id());
                assertThat(tunnel.kind()).isEqualTo(TunnelKind.LOCAL);
                assertThat(tunnel.bindAddress()).isEqualTo("localhost");
                assertThat(tunnel.bindPort()).isEqualTo(5432);
                assertThat(tunnel.targetHost()).isEqualTo("db.internal");
                assertThat(tunnel.targetPort()).isEqualTo(5432);
            });
        });
    }

    @Test void aDynamicTunnelHasNoTargetAndARemoteOneSpeaksOfTheServer() throws Exception {
        edt(() -> {
            var editor = editor(List.of(), Optional.empty(), Optional.empty());
            assertThat(choice(editor, TunnelEditor.HOST).selectedId()).as("the first host by default").isEqualTo(prod.id().toString());
            type(editor.view(), TunnelEditor.NAME, "socks");
            kind(editor, TunnelEditor.DYNAMIC);
            assertThat(visible(editor, TunnelEditor.FORWARD_LABEL)).isFalse();
            assertThat(visible(editor, TunnelEditor.FORWARD_ROW)).isFalse();
            assertThat(text(editor, TunnelEditor.EXPLANATION)).isEqualTo("A SOCKS5 proxy on this computer; each connection goes out through prod.");
            type(editor.view(), TunnelEditor.LISTEN_PORT, "1080");
            assertThat(text(editor, TunnelEditor.PREVIEW)).isEqualTo("A SOCKS5 proxy on localhost:1080; connections leave from prod");
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(saved.getLast().kind()).isEqualTo(TunnelKind.DYNAMIC);
            assertThat(saved.getLast().targetHost()).isEmpty();

            kind(editor, TunnelEditor.REMOTE);
            assertThat(visible(editor, TunnelEditor.FORWARD_LABEL)).isTrue();
            assertThat(visible(editor, TunnelEditor.FORWARD_ROW)).isTrue();
            assertThat(text(editor, TunnelEditor.LISTEN_LABEL)).isEqualTo("Listen on (the server)");
            assertThat(text(editor, TunnelEditor.FORWARD_LABEL)).isEqualTo("Forward to (as seen from this computer)");
            allow(editor);
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).value()).isEqualTo("*");
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).state().enabled()).isFalse();
            assertThat(text(editor, TunnelEditor.EXPOSURE))
                .isEqualTo("Other computers on the network can connect to this port. The server's GatewayPorts setting must allow it.");
            assertThat(visible(editor, TunnelEditor.EXPOSURE)).isTrue();
            allow(editor);
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).value()).isEqualTo("localhost");
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).state().enabled()).isTrue();
            assertThat(visible(editor, TunnelEditor.EXPOSURE)).isFalse();
        });
    }

    @Test void namesAreUniqueAndASharedPortIsOnlyAWarning() throws Exception {
        edt(() -> {
            Tunnel existing = Tunnel.create("pg", prod.id(), TunnelKind.LOCAL, "localhost", 5432, "db", 5432);
            var editor = editor(List.of(existing), Optional.empty(), Optional.empty());
            type(editor.view(), TunnelEditor.NAME, "PG");
            type(editor.view(), TunnelEditor.LISTEN_PORT, "5432");
            type(editor.view(), TunnelEditor.FORWARD_PORT, "5432");
            assertThat(text(editor, TunnelEditor.WARNING)).isEqualTo("pg listens on the same port; they cannot run at once.");
            assertThat(visible(editor, TunnelEditor.WARNING_ROW)).isTrue();
            assertThat(node(editor.view(), TunnelEditor.WARNING + ".icon", Nodes.Icon.class).severity()).contains(Nodes.Severity.WARNING);
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(editor.message()).isEqualTo("A tunnel named pg exists");
            type(editor.view(), TunnelEditor.NAME, "pg2");
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(saved).as("a shared port does not block saving").hasSize(1);
            kind(editor, TunnelEditor.REMOTE);
            assertThat(visible(editor, TunnelEditor.WARNING_ROW)).as("a port on the server is another side").isFalse();
        });
    }

    @Test void editingKeepsTheIdentityAndSavingAQuickForwardNamesIt() throws Exception {
        edt(() -> {
            Tunnel existing = Tunnel.create("pg", prod.id(), TunnelKind.REMOTE, Tunnel.ALL, 8080, "localhost", 3000);
            var editor = editor(List.of(existing), Optional.of(existing), Optional.empty());
            assertThat(button(editor.view(), TunnelEditor.SAVE).label()).isEqualTo("Save");
            assertThat(choice(editor, TunnelEditor.TYPE).selectedId()).isEqualTo(TunnelEditor.REMOTE);
            assertThat(allowOthers(editor).checked()).isTrue();
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).state().enabled()).isFalse();
            assertThat(field(editor, TunnelEditor.FORWARD_PORT).value()).isEqualTo("3000");
            type(editor.view(), TunnelEditor.FORWARD_PORT, "3001");
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(saved.getLast().id()).isEqualTo(existing.id());
            assertThat(saved.getLast().created()).isEqualTo(existing.created());
            assertThat(saved.getLast().targetPort()).isEqualTo(3001);

            Tunnel quick = Tunnel.create("", web.id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 8080, "localhost", 8080);
            var naming = editor(List.of(existing), Optional.of(quick), Optional.empty());
            assertThat(field(naming, TunnelEditor.NAME).value()).isEmpty();
            assertThat(choice(naming, TunnelEditor.HOST).selectedId()).isEqualTo(web.id().toString());
            type(naming.view(), TunnelEditor.NAME, "web app");
            click(naming.view(), TunnelEditor.SAVE);
            assertThat(saved.getLast().id()).isEqualTo(quick.id());
            assertThat(saved.getLast().name()).isEqualTo("web app");
        });
    }

    @Test void aTunnelWhoseHostIsGoneMustChooseAnother() throws Exception {
        edt(() -> {
            Tunnel orphan = Tunnel.create("orphan", UUID.randomUUID(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 1, "x", 1);
            var editor = editor(List.of(orphan), Optional.of(orphan), Optional.empty());
            assertThat(choice(editor, TunnelEditor.HOST).selectedId()).isEmpty();
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(editor.message()).isEqualTo("Choose a host to go through");
            emit(editor.view(), new UiEvent.Selected(TunnelEditor.HOST, Set.of(web.id().toString())));
            assertThat(text(editor, TunnelEditor.FORWARD_LABEL)).as("the chosen host names the target's side").isEqualTo("Forward to (as seen from web)");
            editor.showError("tunnels.toml has errors; fix it before saving");
            assertThat(editor.message()).isEqualTo("tunnels.toml has errors; fix it before saving");
        });
    }

    @Test void tunnelTextIsLiteral() throws Exception {
        edt(() -> {
            Tunnel existing = Tunnel.create("<html><b>bold</b>", prod.id(), TunnelKind.LOCAL, "localhost", 5432, "db", 5432);
            var editor = editor(List.of(existing), Optional.empty(), Optional.empty());
            type(editor.view(), TunnelEditor.NAME, "pg");
            type(editor.view(), TunnelEditor.LISTEN_PORT, "5432");
            assertThat(text(editor, TunnelEditor.WARNING)).as("the warning starts with a saved tunnel's name").startsWith("<html>");
            type(editor.view(), TunnelEditor.LISTEN_ADDRESS, "<html>");
            assertThat(text(editor, TunnelEditor.PREVIEW)).as("the preview starts with what was typed").startsWith("<html>");
            editor.showError("<html><b>refused</b>");
            assertThat(text(editor, TunnelEditor.MESSAGE)).as("kit text is literal; no markup is interpreted").isEqualTo("<html><b>refused</b>");
        });
    }

    /** Baseline f16ae32f had no default button and no field listeners: Enter saves nothing; Escape cancels. */
    @Test void noButtonIsPrimaryAndEscapeCancels() throws Exception {
        edt(() -> {
            var editor = editor(List.of(), Optional.empty(), Optional.empty());
            assertThat(primaries(editor.view())).as("Enter never saves").isEmpty();
            assertThat(form(editor.view()).cancelAction()).isEqualTo(TunnelEditor.CANCEL);
            assertThat(all(editor.view()).stream().filter(n -> n instanceof Nodes.Field f && !f.trailingAction().isEmpty()).toList())
                .as("no field submits").isEmpty();
            assertThat(form(editor.view()).actions()).extracting(Nodes.Button::id).containsExactly(TunnelEditor.CANCEL, TunnelEditor.SAVE);
            press(editor.view(), form(editor.view()).cancelAction());
            assertThat(cancelled).isEqualTo(1);
            assertThat(saved).isEmpty();
        });
    }

    @Test void aClosedEditorIgnoresALateSaveError() throws Exception {
        edt(() -> {
            var kit = new Kits.RecordingKit("dev.jasper.remote");
            var editor = editor(kit, List.of(), Optional.empty(), Optional.empty());
            editor.close();
            editor.showError("late failure");
            assertThat(kit.lateCalls).isEmpty();
            assertThat(closed(editor.view())).isTrue();
        });
    }
}
