package dev.jasper.app.contributions;

import javax.swing.Icon;

/** A contributed status item, rendered identically in every window. */
public final class StatusEntry {
    private final Contributions owner;
    private final String id;
    private final boolean left;
    private final int priority;
    private String text = "";
    private Icon icon;
    private String tooltip;
    private String actionId;
    private boolean visible = true;
    private boolean closed;

    StatusEntry(Contributions owner, String id, boolean left, int priority) {
        this.owner = owner; this.id = id; this.left = left; this.priority = priority;
    }

    public String id() { return id; }
    public boolean left() { return left; }
    public int priority() { return priority; }
    public String text() { return text; }
    /** The icon, or null. */
    public Icon icon() { return icon; }
    /** The tooltip, or null. */
    public String tooltip() { return tooltip; }
    /** The contributed action a click invokes, or null. */
    public String actionId() { return actionId; }
    public boolean visible() { return visible && !closed; }

    public void setText(String value) { if (!closed) { text = value == null ? "" : value.replace("\r", "").replace("\n", " "); notifyOwner(); } }
    public void setIcon(Icon value) { if (!closed) { icon = value; notifyOwner(); } }
    public void setTooltip(String value) { if (!closed) { tooltip = value; notifyOwner(); } }
    public void setActionId(String value) { if (!closed) { actionId = value; notifyOwner(); } }
    public void setVisible(boolean value) { if (!closed) { visible = value; notifyOwner(); } }

    private void notifyOwner() { owner.changed(Contributions.Kind.STATUS); }

    public void close() {
        if (closed) return;
        closed = true;
        owner.remove(this);
    }
}
