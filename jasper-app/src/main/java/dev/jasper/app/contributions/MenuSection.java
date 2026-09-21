package dev.jasper.app.contributions;

import java.util.List;

/** One contributor's entries for one menu target; replaced as a whole. */
public final class MenuSection {
    private final Contributions owner;
    private final MenuTarget target;
    private List<MenuEntry> entries = List.of();
    private boolean closed;

    MenuSection(Contributions owner, MenuTarget target) { this.owner = owner; this.target = target; }

    public MenuTarget target() { return target; }
    public List<MenuEntry> entries() { return entries; }

    public void set(List<MenuEntry> next) {
        if (closed) return;
        entries = List.copyOf(next);
        owner.changed(Contributions.Kind.MENUS);
    }

    public void close() {
        if (closed) return;
        closed = true;
        entries = List.of();
        owner.remove(this);
    }
}
