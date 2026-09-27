package dev.jasper.editor.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The Editor window's tabs, in order. UI thread only. */
public final class EditorModel {
    private final List<DocumentSession> sessions = new ArrayList<>();

    public List<DocumentSession> sessions() { return List.copyOf(sessions); }
    public Optional<DocumentSession> find(String key) { return sessions.stream().filter(session -> session.key().equals(key)).findFirst(); }
    public void add(DocumentSession session) { sessions.add(session); }
    public void remove(DocumentSession session) { sessions.remove(session); }
    public int indexOf(DocumentSession session) { return sessions.indexOf(session); }

    /** @return "Untitled", "Untitled 2", …: the lowest name no open untitled tab uses */
    public String nextUntitledName() {
        for (int n = 1; ; n++) {
            String name = n == 1 ? "Untitled" : "Untitled " + n;
            if (sessions.stream().noneMatch(session -> session.untitled() && session.name().equals(name))) return name;
        }
    }

    /**
     * @param session an open tab
     * @return the dim text after its name: the parent folder when another tab shares the name, and a remote file's host
     */
    public String detail(DocumentSession session) {
        boolean shared = sessions.stream().anyMatch(other -> other != session && other.name().equals(session.name()));
        List<String> parts = new ArrayList<>();
        if (shared && !session.parentName().isEmpty()) parts.add(session.parentName());
        session.host().ifPresent(parts::add);
        return String.join(" · ", parts);
    }

    /** @return tabs with unsaved edits or a save in progress */
    public List<DocumentSession> unsaved() { return sessions.stream().filter(session -> session.dirty() || session.saving()).toList(); }
}
