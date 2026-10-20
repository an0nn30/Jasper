package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.MarkdownMode;
import dev.jasper.sdk.ui.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Document-bar view controls; its owner handles actions and retains the per-tab state. */
public final class MarkdownToolbar {
    private MarkdownToolbar() { }

    /** Creates the right-aligned Markdown controls, hidden for an ineligible document. */
    public static Nodes.Stack node(MarkdownMode mode, boolean linked, boolean ready, boolean eligible, Presentation presentation) {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(presentation);
        var state = new ControlState(true, ready, false, "");
        String suffix = presentation.icons() == Presentation.Icons.CLASSIC ? ".classic" : "";
        var children = new ArrayList<Nodes.Node>(List.of(
            new Nodes.Text("markdown.fill", ControlState.READY, "", Nodes.TextRole.CAPTION),
            toggle("source", "Source", mode == MarkdownMode.SOURCE, suffix, state),
            toggle("split", "Source and preview", mode == MarkdownMode.SPLIT, suffix, state),
            toggle("preview", "Preview", mode == MarkdownMode.PREVIEW, suffix, state)));
        if (mode == MarkdownMode.SPLIT) children.add(toggle("scroll-sync", "Synchronize scrolling", linked, suffix, state));
        return new Nodes.Stack("markdown.toolbar", new ControlState(eligible, ready, false, ""), Nodes.LayoutRole.INLINE, children);
    }
    private static Nodes.Button toggle(String name, String tooltip, boolean selected, String suffix, ControlState state) {
        String id = "markdown." + name;
        return new Nodes.Button(id, state, "", "editor." + name + suffix, Nodes.ButtonKind.GHOST, id, tooltip,
            Nodes.ButtonPresentation.STANDARD, Optional.empty(), Optional.of(selected));
    }
}
