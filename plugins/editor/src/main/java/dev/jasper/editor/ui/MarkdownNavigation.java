package dev.jasper.editor.ui;

import dev.jasper.editor.markdown.resources.Destination;
import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;

/** Explicit link activation through the originating provider and the editor's existing open flow. */
final class MarkdownNavigation {
    private final EditorWindow window;
    private final Consumer<URI> browse;
    MarkdownNavigation(EditorWindow window, Consumer<URI> browse) {
        this.window = Objects.requireNonNull(window); this.browse = Objects.requireNonNull(browse);
    }
    void follow(EditorTab origin, Destination destination) {
        if (origin.closed()) return;
        origin.navigationNotice("");
        switch (destination) {
            case Destination.Unsupported unsupported -> origin.navigationNotice(unsupported.reason());
            case Destination.Web web -> {
                try { browse.accept(web.uri()); }
                catch (RuntimeException failed) { origin.navigationNotice("Could not open link in browser"); }
            }
            case Destination.Fragment fragment -> fragment(origin, fragment.id());
            case Destination.Relative relative -> {
                var source = origin.session().document();
                if (source.isEmpty()) { origin.navigationNotice("Save this document before opening relative links"); return; }
                try {
                    var document = source.get().resolveRelative(relative.path());
                    if (document.isEmpty()) { origin.navigationNotice("This document cannot resolve relative links"); return; }
                    var target = window.openLinked(origin, document.get());
                    relative.fragment().ifPresent(id -> fragment(target, id));
                } catch (RuntimeException failed) { origin.navigationNotice("Could not open linked document"); }
            }
        }
    }
    private void fragment(EditorTab tab, String id) {
        if (tab.markdown() != null) tab.markdown().navigate(id);
    }
}
