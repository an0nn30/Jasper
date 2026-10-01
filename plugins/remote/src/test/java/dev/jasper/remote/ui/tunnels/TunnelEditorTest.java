package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TunnelEditorTest {
    final RemoteHost prod = RemoteHost.create("prod", "db.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final RemoteHost web = RemoteHost.create("web", "web.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final List<Tunnel> saved = new ArrayList<>();

    TunnelEditor editor(List<Tunnel> others, Optional<Tunnel> editing, Optional<UUID> preferred) {
        return new TunnelEditor(List.of(prod, web), others, editing, preferred, saved::add, () -> { });
    }

    @Test void createsALocalTunnelThroughTheChosenHost() {
        var editor = editor(List.of(), Optional.empty(), Optional.of(web.id()));
        assertThat(editor.host.getSelectedItem()).isEqualTo(web);
        assertThat(editor.local.isSelected()).isTrue();
        assertThat(editor.listenAddress.getText()).isEqualTo("localhost");
        assertThat(editor.save.getText()).isEqualTo("Add Tunnel");
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("A tunnel needs a name");
        editor.name.setText("pg");
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("The listen port must be a number from 0 to 65535");
        editor.listenPort.setText("5432");
        editor.forwardHost.setText("db.internal");
        editor.forwardPort.setText("5432");
        assertThat(editor.preview.getText()).isEqualTo("localhost:5432 on this computer → db.internal:5432 as seen from web");
        assertThat(editor.forwardLabel.getText()).isEqualTo("Forward to (as seen from web)");
        assertThat(editor.listenLabel.getText()).isEqualTo("Listen on (this computer)");
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(tunnel -> {
            assertThat(tunnel.name()).isEqualTo("pg");
            assertThat(tunnel.hostId()).isEqualTo(web.id());
            assertThat(tunnel.kind()).isEqualTo(TunnelKind.LOCAL);
            assertThat(tunnel.bindAddress()).isEqualTo("localhost");
            assertThat(tunnel.bindPort()).isEqualTo(5432);
            assertThat(tunnel.targetHost()).isEqualTo("db.internal");
            assertThat(tunnel.targetPort()).isEqualTo(5432);
        });
    }

    @Test void aDynamicTunnelHasNoTargetAndARemoteOneSpeaksOfTheServer() {
        var editor = editor(List.of(), Optional.empty(), Optional.empty());
        assertThat(editor.host.getSelectedItem()).as("the first host by default").isEqualTo(prod);
        editor.name.setText("socks");
        editor.dynamic.doClick();
        assertThat(editor.forwardLabel.isVisible()).isFalse();
        assertThat(editor.explanation.getText()).isEqualTo("A SOCKS5 proxy on this computer; each connection goes out through prod.");
        editor.listenPort.setText("1080");
        assertThat(editor.preview.getText()).isEqualTo("A SOCKS5 proxy on localhost:1080; connections leave from prod");
        editor.save.doClick();
        assertThat(saved.getLast().kind()).isEqualTo(TunnelKind.DYNAMIC);
        assertThat(saved.getLast().targetHost()).isEmpty();

        editor.remote.doClick();
        assertThat(editor.forwardLabel.isVisible()).isTrue();
        assertThat(editor.listenLabel.getText()).isEqualTo("Listen on (the server)");
        assertThat(editor.forwardLabel.getText()).isEqualTo("Forward to (as seen from this computer)");
        editor.allowOthers.doClick();
        assertThat(editor.listenAddress.getText()).isEqualTo("*");
        assertThat(editor.listenAddress.isEnabled()).isFalse();
        assertThat(editor.exposure.getText())
            .isEqualTo("Other computers on the network can connect to this port. The server's GatewayPorts setting must allow it.");
        editor.allowOthers.doClick();
        assertThat(editor.listenAddress.getText()).isEqualTo("localhost");
        assertThat(editor.listenAddress.isEnabled()).isTrue();
    }

    @Test void namesAreUniqueAndASharedPortIsOnlyAWarning() {
        Tunnel existing = Tunnel.create("pg", prod.id(), TunnelKind.LOCAL, "localhost", 5432, "db", 5432);
        var editor = editor(List.of(existing), Optional.empty(), Optional.empty());
        editor.name.setText("PG");
        editor.listenPort.setText("5432");
        editor.forwardPort.setText("5432");
        assertThat(editor.warning.getText()).isEqualTo("pg listens on the same port; they cannot run at once.");
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("A tunnel named pg exists");
        editor.name.setText("pg2");
        editor.save.doClick();
        assertThat(saved).as("a shared port does not block saving").hasSize(1);
        editor.remote.doClick();
        assertThat(editor.warning.getText()).as("a port on the server is another side").isBlank();
    }

    @Test void editingKeepsTheIdentityAndSavingAQuickForwardNamesIt() {
        Tunnel existing = Tunnel.create("pg", prod.id(), TunnelKind.REMOTE, Tunnel.ALL, 8080, "localhost", 3000);
        var editor = editor(List.of(existing), Optional.of(existing), Optional.empty());
        assertThat(editor.save.getText()).isEqualTo("Save");
        assertThat(editor.remote.isSelected()).isTrue();
        assertThat(editor.allowOthers.isSelected()).isTrue();
        assertThat(editor.listenAddress.isEnabled()).isFalse();
        assertThat(editor.forwardPort.getText()).isEqualTo("3000");
        editor.forwardPort.setText("3001");
        editor.save.doClick();
        assertThat(saved.getLast().id()).isEqualTo(existing.id());
        assertThat(saved.getLast().created()).isEqualTo(existing.created());
        assertThat(saved.getLast().targetPort()).isEqualTo(3001);

        Tunnel quick = Tunnel.create("", web.id(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 8080, "localhost", 8080);
        var naming = editor(List.of(existing), Optional.of(quick), Optional.empty());
        assertThat(naming.name.getText()).isEmpty();
        assertThat(naming.host.getSelectedItem()).isEqualTo(web);
        naming.name.setText("web app");
        naming.save.doClick();
        assertThat(saved.getLast().id()).isEqualTo(quick.id());
        assertThat(saved.getLast().name()).isEqualTo("web app");
    }

    @Test void aTunnelWhoseHostIsGoneMustChooseAnother() {
        Tunnel orphan = Tunnel.create("orphan", UUID.randomUUID(), TunnelKind.LOCAL, Tunnel.LOCALHOST, 1, "x", 1);
        var editor = editor(List.of(orphan), Optional.of(orphan), Optional.empty());
        assertThat(editor.host.getSelectedItem()).isNull();
        editor.save.doClick();
        assertThat(editor.message.getText()).isEqualTo("Choose a host to go through");
        editor.showError("tunnels.toml has errors; fix it before saving");
        assertThat(editor.message.getText()).isEqualTo("tunnels.toml has errors; fix it before saving");
    }

    @Test void tunnelTextIsShownAsPlainTextNeverAsHtml() {
        Tunnel existing = Tunnel.create("<html><b>bold</b>", prod.id(), TunnelKind.LOCAL, "localhost", 5432, "db", 5432);
        var editor = editor(List.of(existing), Optional.empty(), Optional.empty());
        editor.name.setText("pg");
        editor.listenPort.setText("5432");
        assertThat(editor.warning.getText()).as("the warning starts with a saved tunnel's name").startsWith("<html>");
        editor.listenAddress.setText("<html>");
        assertThat(editor.preview.getText()).as("the preview starts with what was typed").startsWith("<html>");
        editor.showError("<html><b>refused</b>");
        for (JLabel label : List.of(editor.explanation, editor.listenLabel, editor.forwardLabel, editor.exposure, editor.preview, editor.warning, editor.message))
            assertThat(label.getClientProperty(BasicHTML.propertyKey)).as("no HTML view on " + label.getText()).isNull();
        assertThat(editor.message.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
    }
}
