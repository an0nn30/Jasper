package dev.jasper.app.pluginmanager;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.util.List;
import java.util.Optional;

/**
 * What the user reads before a plugin may load: who it is, what it asks for, and that this is no sandbox. A kit sheet
 * whose primary allows (Enter) and whose Cancel refuses (Escape). Kit text is literal, so plugin text is never markup.
 * EDT only; it closes with its dialog.
 */
final class ConsentView implements AutoCloseable {
    static final String ALLOW = "allow", CANCEL = "cancel";
    private final KitView view;
    private final String title, body;

    ConsentView(KitContext kit, String name, String version, String vendor, List<String> capabilities, boolean update, String allowLabel,
                Runnable onAllow, Runnable onCancel) {
        title = name + " " + version;
        var text = new StringBuilder();
        if (!vendor.isBlank()) text.append("From ").append(vendor).append("\n\n");
        if (update) text.append("This replaces the installed version when Jasper restarts.\n\n");
        if (capabilities.isEmpty()) text.append("It asks for no access to your terminals.");
        else {
            text.append("It asks to:");
            for (String capability : capabilities) text.append("\n  • ").append(Capabilities.describe(capability));
        }
        text.append("\n\nA plugin is unrestricted code running inside Jasper, with everything your account can reach. "
            + "The list above is what the plugin says it does; it is not a sandbox. Allow only plugins you trust.");
        body = text.toString();
        view = new KitView(kit, new ViewSpec(new Nodes.Form("consent", ControlState.READY, Nodes.FormKind.SHEET, title, Optional.empty(),
            List.of(new Nodes.Text("consent-text", ControlState.READY, body, Nodes.TextRole.BODY)), Optional.empty(),
            List.of(new Nodes.Button(CANCEL, ControlState.READY, "Cancel", "", Nodes.ButtonKind.SECONDARY, CANCEL, ""),
                new Nodes.Button(ALLOW, ControlState.READY, allowLabel, "", Nodes.ButtonKind.PRIMARY, ALLOW, "")), CANCEL)), event -> {
            if (!(event instanceof UiEvent.Action action)) return;
            if (action.id().equals(ALLOW)) onAllow.run();
            else if (action.id().equals(CANCEL)) onCancel.run();
        });
    }

    KitView view() { return view; }

    /** Everything the sheet says: the plugin, its vendor, what it asks for and the warning. */
    String text() { return title + "\n" + body; }

    @Override public void close() { view.close(); }
}
