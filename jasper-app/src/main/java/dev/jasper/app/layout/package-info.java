/**
 * The workspace layout model (workspace tabs spec, section 7; tabs and panes spec 4): tab and group ids, a tab's panes
 * (groups, one tab each) in nested weighted splits, its active and maximized pane and the panes' order of use, the
 * operations that return a new
 * {@code Layout}, each group's region of the unit square, and what a window holds (tabs and panes spec 4): its tabs
 * ({@code ParentTabs}). JDK-only: nothing from Swing, AWT or a
 * file format; {@code verifyApplicationArchitecture} enforces it. Immutable values, safe on any thread; whoever holds a
 * layout owns replacing it.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.layout;
