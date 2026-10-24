package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.swing.NativeStyles;
import java.awt.Dimension;
import java.util.Objects;
import javax.swing.JButton;

/**
 * A window's ⋮ (parent tabs spec 6), as IntelliJ's tool windows have one: it opens the window menu for the window's active
 * pane and parent tab ({@link WindowContent#windowMenu()}). The standard toolbar ends with one, styled as its overflow
 * button; a child window shows one alone, its compact toolbar, at the trailing end of its title row (workspace plan 7b
 * Rulings 16 and 17), or the classic ⋮ under a classic title. A toolbar pins it ({@link WindowToolbar#pin}): it never
 * moves into the overflow menu, which sits before it, and never shows a label. Its size is fixed. EDT only.
 */
final class WindowMenuButton extends JButton {
    /** Its name, by which tests find it. */
    static final String NAME = "windowMenu";
    /** Its tooltip, as IntelliJ names the menu. */
    static final String LABEL = "View Options";
    /** Its side outside a toolbar, in logical pixels: IntelliJ's icon button. */
    private static final int SIDE = 22;

    WindowMenuButton(WindowContent owner) {
        super(AppIcons.semantic("more.vertical"));
        Objects.requireNonNull(owner, "owner");
        setName(NAME);
        setText(null);
        // Named for the toolbar's lists; a toolbar applies no label to it in any mode.
        putClientProperty("label", LABEL);
        putClientProperty("keepLabel", false);
        setToolTipText(LABEL);
        getAccessibleContext().setAccessibleName(LABEL);
        setFocusable(false);
        WindowToolbar.pin(this);
        NativeStyles.installed().button(this, () -> ThemeZone.theme(this));
        addActionListener(event -> owner.showWindowMenu(this));
    }

    /** Square: the toolbar's button side in a toolbar, else IntelliJ's 22 px icon button. */
    @Override public Dimension getPreferredSize() {
        int side = getParent() instanceof WindowToolbar bar ? bar.buttonSide() : UIScale.scale(SIDE);
        return new Dimension(side, side);
    }

    /** As {@link #getPreferredSize()}: a title row's layout neither shrinks nor stretches it. */
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    /** As {@link #getPreferredSize()}: a title row's layout neither shrinks nor stretches it. */
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }
}
