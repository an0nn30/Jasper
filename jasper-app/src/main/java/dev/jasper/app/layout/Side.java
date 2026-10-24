package dev.jasper.app.layout;

/** A side of a group, where a split puts the new group; also a direction to a neighbouring group. */
public enum Side {
    /** The left side: a new group comes before the group, in a horizontal split. */
    LEFT,
    /** The right side: a new group comes after the group, in a horizontal split. */
    RIGHT,
    /** The upper side: a new group comes before the group, in a vertical split. */
    UP,
    /** The lower side: a new group comes after the group, in a vertical split. */
    DOWN;

    /**
     * The orientation of a split along this side.
     *
     * @return HORIZONTAL for LEFT and RIGHT, VERTICAL for UP and DOWN
     */
    public Orientation orientation() {
        return this == LEFT || this == RIGHT ? Orientation.HORIZONTAL : Orientation.VERTICAL;
    }

    /**
     * Whether a group put on this side comes first in its split.
     *
     * @return true for LEFT and UP
     */
    public boolean leading() {
        return this == LEFT || this == UP;
    }
}
