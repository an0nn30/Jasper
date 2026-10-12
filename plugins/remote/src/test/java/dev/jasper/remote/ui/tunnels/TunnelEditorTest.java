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

/** The tunnel editor's behavior on the testkit fake (Plan 2 Task 8b; recomposed in Plan 3 Task 6). */
class TunnelEditorTest {
    final RemoteHost prod = RemoteHost.create("prod", "db.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final RemoteHost web = RemoteHost.create("web", "web.example", 2222, "deploy", Auth.AGENT, "Work", Optional.empty());
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
    static Nodes.FormRow row(TunnelEditor editor, String id) { return node(editor.view(), id, Nodes.FormRow.class); }
    static Nodes.Choice choice(TunnelEditor editor, String id) { return node(editor.view(), id, Nodes.Choice.class); }
    static Nodes.Toggle allowOthers(TunnelEditor editor) { return node(editor.view(), TunnelEditor.ALLOW_OTHERS, Nodes.Toggle.class); }
    static boolean saveEnabled(TunnelEditor editor) { return button(editor.view(), TunnelEditor.SAVE).state().enabled(); }
    static void kind(TunnelEditor editor, String option) { emit(editor.view(), new UiEvent.Selected(TunnelEditor.TYPE, Set.of(option))); }
    static void allow(TunnelEditor editor) { emit(editor.view(), new UiEvent.Toggled(TunnelEditor.ALLOW_OTHERS, !allowOthers(editor).checked())); }
    static List<String> route(TunnelEditor editor) {
        var strip = node(editor.view(), TunnelEditor.ROUTE, Nodes.Stack.class);
        var lines = new ArrayList<String>();
        for (var step : strip.children()) for (var child : ((Nodes.Stack) step).children()) if (child instanceof Nodes.Text line) lines.add(line.text());
        return lines;
    }

    @Test void createsALocalTunnelThroughTheChosenHost() throws Exception {
        edt(() -> {
            var editor = editor(List.of(), Optional.empty(), Optional.of(web.id()));
            var hosts = choice(editor, TunnelEditor.HOST);
            assertThat(hosts.kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN);
            assertThat(hosts.selectedId()).isEqualTo(web.id().toString());
            assertThat(hosts.options()).extracting(o -> o.label()).containsExactly("prod", "web");
            assertThat(hosts.options()).extracting(o -> o.description()).containsExactly("deploy@db.example:22", "deploy@web.example:2222 · Work");
            assertThat(hosts.face()).as("the closed face describes the chosen host").hasValueSatisfying(face -> {
                assertThat(face.primary()).isEqualTo("web");
                assertThat(face.caption()).isEqualTo("deploy@web.example:2222 · Work");
                assertThat(face.icon()).isEqualTo("server");
            });
            assertThat(choice(editor, TunnelEditor.TYPE).selectedId()).isEqualTo(TunnelEditor.LOCAL);
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).value()).isEqualTo("localhost");
            assertThat(button(editor.view(), TunnelEditor.SAVE).label()).isEqualTo("Add Tunnel");
            assertThat(saveEnabled(editor)).as("an empty draft cannot be added").isFalse();
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(editor.message()).as("a forced activation still validates").isEqualTo("A tunnel needs a name");
            assertThat(text(editor, TunnelEditor.MESSAGE)).isEqualTo("A tunnel needs a name");
            type(editor.view(), TunnelEditor.NAME, "pg");
            assertThat(find(editor.view(), TunnelEditor.MESSAGE)).as("editing clears the message").isEmpty();
            assertThat(text(editor, TunnelEditor.PREVIEW)).isEqualTo("ssh -L <port>:localhost:<port> deploy@web.example -p 2222");
            assertThat(route(editor)).containsExactly("This computer", "localhost:?", "Via", "web", "Target on web", "localhost:?");
            type(editor.view(), TunnelEditor.LISTEN_PORT, "5432");
            type(editor.view(), TunnelEditor.FORWARD_HOST, "db.internal");
            type(editor.view(), TunnelEditor.FORWARD_PORT, "5432");
            assertThat(text(editor, TunnelEditor.PREVIEW)).isEqualTo("ssh -L 5432:db.internal:5432 deploy@web.example -p 2222");
            assertThat(route(editor)).containsExactly("This computer", "localhost:5432", "Via", "web", "Target on web", "db.internal:5432");
            assertThat(row(editor, TunnelEditor.LISTEN_ROW)).satisfies(row -> { assertThat(row.label()).isEqualTo("Listen On"); assertThat(row.caption()).isEqualTo("this computer"); });
            assertThat(row(editor, TunnelEditor.FORWARD_ROW)).satisfies(row -> { assertThat(row.label()).isEqualTo("Forward To"); assertThat(row.caption()).isEqualTo("as seen from web"); });
            assertThat(saveEnabled(editor)).isTrue();
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

    @Test void savingWaitsForTheOwnerAndAnErrorLetsItSaveAgain() throws Exception {
        edt(() -> {
            var editor = editor(List.of(), Optional.empty(), Optional.empty());
            type(editor.view(), TunnelEditor.NAME, "pg");
            type(editor.view(), TunnelEditor.LISTEN_PORT, "5432");
            type(editor.view(), TunnelEditor.FORWARD_PORT, "5432");
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(saved).hasSize(1);
            var busy = button(editor.view(), TunnelEditor.SAVE);
            assertThat(busy.label()).isEqualTo("Saving…");
            assertThat(busy.state().busy()).isTrue();
            assertThat(field(editor, TunnelEditor.NAME).state().enabled()).as("the draft being saved stays as shown").isFalse();
            click(editor.view(), TunnelEditor.SAVE);
            type(editor.view(), TunnelEditor.NAME, "changed");
            assertThat(saved).as("one save per gesture while the owner saves").hasSize(1);
            assertThat(field(editor, TunnelEditor.NAME).value()).isEqualTo("pg");
            editor.showError("tunnels.toml could not be written");
            assertThat(text(editor, TunnelEditor.MESSAGE)).isEqualTo("tunnels.toml could not be written");
            assertThat(saveEnabled(editor)).isTrue();
            assertThat(button(editor.view(), TunnelEditor.SAVE).label()).isEqualTo("Add Tunnel");
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(saved).hasSize(2);
        });
    }

    @Test void aDynamicTunnelHasNoTargetAndARemoteOneSpeaksOfTheServer() throws Exception {
        edt(() -> {
            var editor = editor(List.of(), Optional.empty(), Optional.empty());
            assertThat(choice(editor, TunnelEditor.HOST).selectedId()).as("the first host by default").isEqualTo(prod.id().toString());
            type(editor.view(), TunnelEditor.NAME, "socks");
            kind(editor, TunnelEditor.DYNAMIC);
            assertThat(visible(editor, TunnelEditor.FORWARD_ROW)).isFalse();
            assertThat(text(editor, TunnelEditor.EXPLANATION)).isEqualTo("A SOCKS5 proxy on this computer; each connection goes out through prod.");
            assertThat(node(editor.view(), TunnelEditor.EXPLANATION, Nodes.Text.class).role()).isEqualTo(Nodes.TextRole.SUPPORTING);
            type(editor.view(), TunnelEditor.LISTEN_PORT, "1080");
            assertThat(text(editor, TunnelEditor.PREVIEW)).isEqualTo("ssh -D 1080 deploy@db.example");
            assertThat(route(editor)).containsExactly("SOCKS5 proxy", "localhost:1080", "Via", "prod", "Destinations", "as each client asks");
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(saved.getLast().kind()).isEqualTo(TunnelKind.DYNAMIC);
            assertThat(saved.getLast().targetHost()).isEmpty();
            editor.showError("again");

            kind(editor, TunnelEditor.REMOTE);
            assertThat(visible(editor, TunnelEditor.FORWARD_ROW)).isTrue();
            assertThat(row(editor, TunnelEditor.LISTEN_ROW).caption()).isEqualTo("on prod");
            assertThat(row(editor, TunnelEditor.FORWARD_ROW).caption()).isEqualTo("as seen from this computer");
            assertThat(text(editor, TunnelEditor.EXPLANATION)).isEqualTo("Connections to a port on prod come back through this computer to the target.");
            var setting = node(editor.view(), "row." + TunnelEditor.ALLOW_OTHERS, Nodes.SettingRow.class);
            assertThat(setting.description()).isEqualTo("Listens on all network interfaces, not just localhost");
            allow(editor);
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).value()).isEqualTo("*");
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).state().enabled()).isFalse();
            assertThat(node(editor.view(), "row." + TunnelEditor.ALLOW_OTHERS, Nodes.SettingRow.class).description())
                .isEqualTo("Listens on all of the server's interfaces; its GatewayPorts setting must allow it");
            type(editor.view(), TunnelEditor.FORWARD_PORT, "3000");
            assertThat(text(editor, TunnelEditor.PREVIEW)).isEqualTo("ssh -R *:1080:localhost:3000 deploy@db.example");
            allow(editor);
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).value()).isEqualTo("localhost");
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).state().enabled()).isTrue();
        });
    }

    @Test void namesAreUniqueAndASharedPortIsOnlyAWarning() throws Exception {
        edt(() -> {
            Tunnel existing = Tunnel.create("pg", prod.id(), TunnelKind.LOCAL, "localhost", 5432, "db", 5432);
            var editor = editor(List.of(existing), Optional.empty(), Optional.empty());
            type(editor.view(), TunnelEditor.NAME, "PG");
            type(editor.view(), TunnelEditor.LISTEN_PORT, "5432");
            type(editor.view(), TunnelEditor.FORWARD_PORT, "5432");
            assertThat(field(editor, TunnelEditor.NAME).state().error()).as("the name is marked on its field").isEqualTo("A tunnel named pg exists");
            assertThat(saveEnabled(editor)).isFalse();
            assertThat(text(editor, TunnelEditor.WARNING)).isEqualTo("pg listens on the same port; they cannot run at once.");
            assertThat(visible(editor, TunnelEditor.WARNING_ROW)).isTrue();
            assertThat(node(editor.view(), TunnelEditor.WARNING + ".icon", Nodes.Icon.class).severity()).contains(Nodes.Severity.WARNING);
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(editor.message()).isEqualTo("A tunnel named pg exists");
            type(editor.view(), TunnelEditor.NAME, "pg2");
            assertThat(field(editor, TunnelEditor.NAME).state().error()).isEmpty();
            assertThat(saveEnabled(editor)).as("a shared port does not block saving").isTrue();
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(saved).hasSize(1);
            editor.showError("x");
            type(editor.view(), TunnelEditor.NAME, "pg3");
            assertThat(visible(editor, TunnelEditor.WARNING_ROW)).isTrue();
            kind(editor, TunnelEditor.REMOTE);
            assertThat(find(editor.view(), TunnelEditor.WARNING_ROW)).as("a port on the server is another side").isEmpty();
        });
    }

    @Test void portsOutOfRangeAreMarkedOnTheirFields() throws Exception {
        edt(() -> {
            var editor = editor(List.of(), Optional.empty(), Optional.empty());
            type(editor.view(), TunnelEditor.NAME, "pg");
            assertThat(field(editor, TunnelEditor.LISTEN_PORT).state().error()).as("a blank port only waits").isEmpty();
            type(editor.view(), TunnelEditor.LISTEN_PORT, "99999");
            type(editor.view(), TunnelEditor.FORWARD_PORT, "0");
            assertThat(field(editor, TunnelEditor.LISTEN_PORT).state().error()).isEqualTo("Use 0 to 65535");
            assertThat(field(editor, TunnelEditor.FORWARD_PORT).state().error()).isEqualTo("Use 1 to 65535");
            assertThat(saveEnabled(editor)).isFalse();
            type(editor.view(), TunnelEditor.LISTEN_PORT, "0");
            type(editor.view(), TunnelEditor.FORWARD_PORT, "80");
            assertThat(field(editor, TunnelEditor.LISTEN_PORT).state().error()).as("0 asks for any free port").isEmpty();
            assertThat(route(editor)).contains("localhost:any port");
            assertThat(saveEnabled(editor)).isTrue();
        });
    }

    @Test void everyProblemThatDisablesThePrimaryIsMarkedOnItsField() throws Exception {
        edt(() -> {
            var editor = editor(List.of(), Optional.empty(), Optional.empty());
            type(editor.view(), TunnelEditor.NAME, "pg");
            type(editor.view(), TunnelEditor.LISTEN_PORT, "5432");
            type(editor.view(), TunnelEditor.FORWARD_PORT, "5432");
            assertThat(saveEnabled(editor)).isTrue();
            type(editor.view(), TunnelEditor.LISTEN_ADDRESS, "my-laptop");
            assertThat(saveEnabled(editor)).as("a host name is refused so binding never waits on DNS").isFalse();
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).state().error()).isEqualTo("Use localhost, * or an IP address");
            type(editor.view(), TunnelEditor.LISTEN_ADDRESS, "");
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).state().error()).as("the placeholder is not a value").isEqualTo("Enter localhost, * or an IP address");
            type(editor.view(), TunnelEditor.LISTEN_ADDRESS, "127.0.0.1");
            assertThat(field(editor, TunnelEditor.LISTEN_ADDRESS).state().error()).isEmpty();
            type(editor.view(), TunnelEditor.FORWARD_HOST, " ");
            assertThat(saveEnabled(editor)).isFalse();
            assertThat(field(editor, TunnelEditor.FORWARD_HOST).state().error()).isEqualTo("Enter the host to forward to");
            kind(editor, TunnelEditor.DYNAMIC);
            assertThat(field(editor, TunnelEditor.FORWARD_HOST).state().error()).as("a SOCKS proxy has no target").isEmpty();
            assertThat(saveEnabled(editor)).isTrue();
            var empty = new TunnelEditor(new FakeKit("dev.jasper.remote"), List.of(), List.of(), Optional.empty(), Optional.empty(), saved::add, () -> cancelled++);
            assertThat(choice(empty, TunnelEditor.HOST).state().error()).isEqualTo("Add a host in Sessions first");
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
            assertThat(field(editor, TunnelEditor.NAME).state().error()).as("its own name is not a duplicate").isEmpty();
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
            assertThat(choice(editor, TunnelEditor.HOST).face()).as("no host to describe").isEmpty();
            assertThat(saveEnabled(editor)).isFalse();
            assertThat(choice(editor, TunnelEditor.HOST).state().error()).as("the disabled primary says why").isEqualTo("Choose a host to go through");
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(editor.message()).isEqualTo("Choose a host to go through");
            emit(editor.view(), new UiEvent.Selected(TunnelEditor.HOST, Set.of(web.id().toString())));
            assertThat(choice(editor, TunnelEditor.HOST).state().error()).isEmpty();
            assertThat(row(editor, TunnelEditor.FORWARD_ROW).caption()).as("the chosen host names the target's side").isEqualTo("as seen from web");
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
            type(editor.view(), TunnelEditor.FORWARD_HOST, "<html>");
            assertThat(route(editor)).as("the route shows what was typed").contains("<html>:?");
            editor.showError("<html><b>refused</b>");
            assertThat(text(editor, TunnelEditor.MESSAGE)).as("kit text is literal; no markup is interpreted").isEqualTo("<html><b>refused</b>");
        });
    }

    /** Baseline f16ae32f had no default button and no field listeners: Enter saves nothing; Escape cancels. */
    @Test void enterSubmitsNothingAndEscapeCancels() throws Exception {
        edt(() -> {
            var editor = editor(List.of(), Optional.empty(), Optional.empty());
            assertThat(form(editor.view()).submission()).as("Enter never saves, as in Add Host").isEqualTo(Nodes.FormSubmission.NONE);
            assertThat(primaries(editor.view())).extracting(Nodes.Button::id).as("Add Tunnel is the visual primary").containsExactly(TunnelEditor.SAVE);
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
