package dev.jasper.app.documentation;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.testing.FakeKitView;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.ListItem;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The plugin UI guide's snippets ({@code docs/plugin-ui.md}), exercised against the SDK and its testkit. */
class PluginUiExamplesTest {
    // example:uiform:start
    /** A New Server dialog: grouped cards, a monospace preview and one primary action. */
    static final class ServerForm {
        private final java.util.function.Consumer<String> onSave;
        private final Runnable onCancel;
        private final UiView view;
        private String name = "";
        private boolean startup;

        ServerForm(dev.jasper.sdk.ui.UiKit kit, java.util.function.Consumer<String> onSave, Runnable onCancel) {
            this.onSave = onSave;
            this.onCancel = onCancel;
            view = kit.create(spec(), this::handle);
        }

        UiView view() { return view; }

        // Domain state changes, then the whole spec is reconciled by stable id: input, focus and selection survive.
        private void handle(UiEvent event) {
            switch (event) {
                case UiEvent.Edited edited when edited.id().equals("name") -> { name = edited.text(); view.update(spec()); }
                case UiEvent.Toggled toggled when toggled.id().equals("startup") -> { startup = toggled.checked(); view.update(spec()); }
                case UiEvent.Action action when action.id().equals("save") -> onSave.accept(name.strip());
                case UiEvent.Action action when action.id().equals("cancel") -> onCancel.run();
                default -> { }
            }
        }

        private ViewSpec spec() {
            var general = new Nodes.Section("general", ControlState.READY, "Server", new Nodes.Card("general.card", ControlState.READY,
                Nodes.CardKind.FORM, List.of(new Nodes.FormRow("row.name", ControlState.READY, "Name", "",
                    new Nodes.Field("name", ControlState.READY, Nodes.FieldKind.TEXT, name, "e.g. Downloads", "Name", ""), Optional.empty()))));
            var options = new Nodes.Section("options", ControlState.READY, "Startup", new Nodes.Card("options.card", ControlState.READY,
                Nodes.CardKind.SETTINGS, List.of(new Nodes.SettingRow("row.startup", ControlState.READY, "Start when Jasper starts", "",
                    new Nodes.Toggle("startup", ControlState.READY, "Start when Jasper starts", startup, "startup", false), false))));
            // The primary stays disabled while the draft is invalid; the host greys it and ignores Enter.
            var valid = name.isBlank() ? new ControlState(true, false, false, "") : ControlState.READY;
            return new ViewSpec(new Nodes.Form("server", ControlState.READY, Nodes.FormKind.FULL, "New Server", Optional.empty(),
                List.of(general, options), Optional.of(new Nodes.Text("preview", ControlState.READY, "http://127.0.0.1:8080", Nodes.TextRole.MONO_CAPTION)),
                List.of(new Nodes.Button("cancel", ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, "cancel", ""),
                    new Nodes.Button("save", valid, "Add Server", "", Nodes.ButtonKind.PRIMARY, "save", "")),
                "cancel"));
        }
    }
    // example:uiform:end

    // example:uipanel:start
    /** A docked panel: inline count, search, two-line rows with a chip and a row switch, and a full-body empty state. */
    static ViewSpec serversPanel(List<String> names, Set<String> running, String query) {
        var header = new Nodes.Header("servers.header", ControlState.READY, "File Servers", OptionalInt.of(names.size()),
            Optional.empty(), Optional.of(new Nodes.Button("servers.add", ControlState.READY, "", "add", Nodes.ButtonKind.GHOST, "add", "New server")),
            List.of());
        if (names.isEmpty())
            return new ViewSpec(new Nodes.Panel("servers", ControlState.READY, header, Optional.empty(),
                new Nodes.Empty("servers.empty", ControlState.READY, new Nodes.Icon("servers.empty.icon", ControlState.READY, "server",
                    Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "File servers"), "No File Servers",
                    "Share a local folder over HTTP with devices on your network.",
                    List.of(new Nodes.Button("servers.share", ControlState.READY, "Share Folder...", "", Nodes.ButtonKind.SECONDARY, "add", ""))),
                Optional.empty()));
        var rows = new ArrayList<ListItem>();
        for (String name : names) {
            if (!name.contains(query)) continue;
            rows.add(new ListItem(name, Optional.of(new Nodes.Icon(name + ".icon", ControlState.READY, "folder.filled", Nodes.Hue.BLUE, Nodes.IconSize.CONTROL, "Folder")),
                List.of(new Nodes.Text(name + ".name", ControlState.READY, name, Nodes.TextRole.BODY)),
                List.of(new Nodes.Chip(name + ".protocol", ControlState.READY, "HTTP", Nodes.Hue.BLUE),
                    new Nodes.Text(name + ".endpoint", ControlState.READY, "127.0.0.1:8080", Nodes.TextRole.MONO_SMALL)),
                // A row switch is its own focusable control: toggling it never activates the row.
                List.of(new Nodes.Toggle(name + ".run", ControlState.READY, "Run " + name, running.contains(name), "run", true))));
        }
        var search = new Nodes.Field("servers.search", ControlState.READY, Nodes.FieldKind.SEARCH, query, "Search servers", "Search servers", "");
        return new ViewSpec(new Nodes.Panel("servers", ControlState.READY, header, Optional.of(search),
            new Nodes.ItemList("servers.rows", ControlState.READY, Nodes.ListKind.DOUBLE, rows, Set.of(), "open", List.of(),
                Nodes.SelectionMode.SINGLE, Optional.empty()),
            Optional.of(new Nodes.SettingRow("servers.follow", ControlState.READY, "Start servers with Jasper", "",
                new Nodes.Toggle("servers.autostart", ControlState.READY, "Start servers with Jasper", true, "autostart", true), false))));
    }
    // example:uipanel:end

    @Test void formSnippetDrivesItsPrimaryFromTheDraft() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var kit = new FakeKit("com.example.servers")) {
                var saved = new ArrayList<String>();
                var form = new ServerForm(kit, saved::add, () -> { });
                var view = (FakeKitView) form.view();
                assertThat(button(view, "save").state().enabled()).isFalse();
                view.emit(new UiEvent.Edited("name", " Downloads "));
                assertThat(button(view, "save").state().enabled()).isTrue();
                view.emit(new UiEvent.Action("save"));
                assertThat(saved).containsExactly("Downloads");
                assertThat(kit.failures()).isEmpty();
            }
        });
    }

    @Test void panelSnippetSwitchesBetweenEmptyAndPopulated() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var kit = new FakeKit("com.example.servers")) {
                var view = (FakeKitView) kit.create(serversPanel(List.of(), Set.of(), ""), event -> { });
                assertThat(((Nodes.Panel) view.spec().root()).body()).isInstanceOf(Nodes.Empty.class);
                view.update(serversPanel(List.of("tester"), Set.of("tester"), ""));
                var panel = (Nodes.Panel) view.spec().root();
                assertThat(panel.header().count()).hasValue(1);
                assertThat(((Nodes.ItemList) panel.body()).items()).singleElement().extracting(ListItem::id).isEqualTo("tester");
                assertThat(kit.failures()).isEmpty();
            }
        });
    }

    private static Nodes.Button button(FakeKitView view, String id) {
        var form = (Nodes.Form) view.spec().root();
        return form.actions().stream().filter(button -> button.id().equals(id)).findFirst().orElseThrow();
    }
}
