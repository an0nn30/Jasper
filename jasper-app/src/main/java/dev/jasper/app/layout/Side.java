package dev.jasper.app.layout;

/** A side of a group, where a split puts the new group; also a direction to a neighbouring group. */
public enum Side {
    LEFT, RIGHT, UP, DOWN;

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
