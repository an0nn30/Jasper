package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.platform.AppIcons;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;

/**
 * One notification as a card (spec section 3): icon, wrapping bold title with a muted repeat count, at most five lines
 * of content, up to three action links with the rest under More, and a close button shown on hover (always for a
 * sticky card). Clicking the body runs the notification's default action. Plain text only. EDT only.
 */
final class NotificationCard extends JPanel {
    static final int WIDTH = 360;
    static final int CONTENT_LINES = 5;
    static final int VISIBLE_ACTIONS = 3;
    static final int ARC = 10;
    private static final int PAD = 12, ICON = 16, GAP = 8, CLOSE = 16;

    private final NotificationCenter center;
    private final boolean sticky;
    private Notification notification;
    private final JLabel icon = new JLabel();
    private final TextBlock title = new TextBlock("Jasper.notification.foreground", true, 0);
    private final JLabel count = new JLabel();
    private final TextBlock content = new TextBlock("Jasper.notification.foreground", false, CONTENT_LINES);
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    private final JButton close = new JButton();
    private final MouseAdapter hover;
    private JPopupMenu more;
    private boolean hovered;
    /** Told when the pointer enters or leaves the card, so its countdown pauses. */
    Consumer<Boolean> onHover = value -> {};

    NotificationCard(NotificationCenter center, Notification notification, boolean sticky) {
        this.center = center;
        this.sticky = sticky;
        setLayout(null);
        setOpaque(false);
        setFocusable(false);
        count.putClientProperty("html.disable", Boolean.TRUE);
        actions.setOpaque(false);
        close.setIcon(AppIcons.bundled("general/closeSmall"));
        close.setRolloverIcon(AppIcons.bundled("general/closeSmallHovered"));
        close.putClientProperty("JButton.buttonType", "borderless");
        close.setFocusable(false);
        close.setToolTipText("Close");
        close.getAccessibleContext().setAccessibleName("Close notification");
        close.addActionListener(event -> center.markRead(this.notification.id()));
        hover = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { setHovered(true); }
            @Override public void mouseExited(MouseEvent event) { if (!pointerInside()) setHovered(false); }
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getSource() == NotificationCard.this && SwingUtilities.isLeftMouseButton(event))
                    center.activate(NotificationCard.this.notification.id());
            }
        };
        addMouseListener(hover);
        close.addMouseListener(hover);
        add(icon); add(title); add(count); add(content); add(actions); add(close);
        update(notification);
        restyle();
    }

    Notification notification() { return notification; }

    boolean sticky() { return sticky; }

    /** Shows {@code next}: the same entry with a new count, or new text. */
    void update(Notification next) {
        notification = next;
        var request = next.request();
        icon.setIcon(NotificationStyle.icon(next));
        title.setText(request.title());
        count.setText(next.count() > 1 ? "×" + next.count() : "");
        count.setVisible(next.count() > 1);
        content.setText(request.content());
        content.setVisible(!request.content().isEmpty());
        actions.removeAll();
        more = null;
        List<NotificationAction> all = request.actions();
        fillActions(all);
        actions.setVisible(!all.isEmpty());
        setCursor(request.activate().isPresent() ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        close.setVisible(sticky || hovered);
        getAccessibleContext().setAccessibleName(NotificationStyle.severityName(request.severity()) + ": " + request.title());
        getAccessibleContext().setAccessibleDescription(request.content());
        arrange(true);
        revalidate();
        repaint();
    }

    void setHovered(boolean value) {
        if (hovered == value) return;
        hovered = value;
        close.setVisible(sticky || hovered);
        onHover.accept(value);
    }

    List<String> titleLines() { return title.lines(); }
    List<String> contentLines() { return content.lines(); }
    String countText() { return count.getText(); }
    List<JButton> actionButtons() { return Arrays.stream(actions.getComponents()).map(JButton.class::cast).toList(); }
    JPopupMenu moreMenu() { return more; }
    JButton closeButton() { return close; }
    Color titleColor() { return title.getForeground(); }
    JPanel actionsPanel() { return actions; }

    private boolean pointerInside() {
        try { return getMousePosition(true) != null; }
        catch (HeadlessException headless) { return false; }
    }

    /**
     * At most {@link #VISIBLE_ACTIONS} leading links, all of them when they fit one row; otherwise as many as fit beside
     * More (possibly none), with every other action under More. Wrapping onto a second row would hide More.
     */
    private void fillActions(List<NotificationAction> all) {
        int candidates = Math.min(all.size(), VISIBLE_ACTIONS);
        List<JButton> links = new ArrayList<>();
        for (int i = 0; i < candidates; i++) links.add(actionLink(i, all.get(i).label()));
        int shown = candidates;
        if (all.size() > candidates || widthOf(links, candidates) > textWidth()) {
            int moreWidth = moreButton(true).getPreferredSize().width;
            shown = Math.min(candidates, all.size() - 1);
            while (shown > 0 && widthOf(links, shown) + moreWidth > textWidth()) shown--;
        }
        for (int i = 0; i < shown; i++) actions.add(links.get(i));
        if (shown < all.size()) actions.add(moreLink(all, shown));
    }

    private static int widthOf(List<JButton> links, int count) {
        int width = 0;
        for (int i = 0; i < count; i++) width += links.get(i).getPreferredSize().width;
        return width;
    }

    private JButton actionLink(int index, String label) {
        JButton link = NotificationStyle.link(label, () -> center.runAction(notification.id(), index));
        link.setBorder(linkBorder(index > 0));
        link.addMouseListener(hover);
        return link;
    }

    private JButton moreLink(List<NotificationAction> all, int first) {
        var menu = new JPopupMenu();
        for (int i = first; i < all.size(); i++) {
            int index = i;
            var item = new JMenuItem();
            // Before the text, which is when markup would be parsed.
            item.putClientProperty("html.disable", Boolean.TRUE);
            item.setText(all.get(i).label());
            item.addActionListener(event -> center.runAction(notification.id(), index));
            menu.add(item);
        }
        more = menu;
        JButton link = moreButton(first > 0);
        link.addActionListener(event -> menu.show(link, 0, link.getHeight()));
        link.addMouseListener(hover);
        return link;
    }

    private static JButton moreButton(boolean gapBefore) {
        JButton link = NotificationStyle.link("More ▾", () -> {});
        link.setBorder(linkBorder(gapBefore));
        return link;
    }

    private static Border linkBorder(boolean gapBefore) {
        return BorderFactory.createEmptyBorder(2, gapBefore ? UIScale.scale(12) : 0, 2, 0);
    }

    /** The width of the text column: the card less its padding, icon and the gap after the icon. */
    private static int textWidth() {
        return UIScale.scale(WIDTH) - UIScale.scale(PAD) - UIScale.scale(ICON) - UIScale.scale(GAP) - UIScale.scale(PAD);
    }

    /** Places the parts and returns the height they need; with {@code apply} false it only measures. */
    private int arrange(boolean apply) {
        int pad = UIScale.scale(PAD), iconSize = UIScale.scale(ICON), gap = UIScale.scale(GAP), closeSize = UIScale.scale(CLOSE);
        int width = UIScale.scale(WIDTH);
        int textX = pad + iconSize + gap;
        int titleWidth = width - textX - pad - closeSize - UIScale.scale(4);
        int textWidth = textWidth();
        title.setWrapWidth(titleWidth);
        content.setWrapWidth(textWidth);
        int y = pad;
        Dimension titleSize = title.getPreferredSize();
        int titleHeight = titleSize.height;
        if (count.isVisible()) {
            Dimension countSize = count.getPreferredSize();
            int lineHeight = title.lineHeight();
            int lines = Math.max(1, title.lines().size());
            int countX = textX + title.lastLineWidth() + UIScale.scale(6);
            int countY = y + (lines - 1) * lineHeight;
            if (countX + countSize.width > textX + titleWidth) {
                countX = textX;
                countY = y + lines * lineHeight;
                titleHeight += lineHeight;
            }
            if (apply) count.setBounds(countX, countY + (lineHeight - countSize.height) / 2, countSize.width, countSize.height);
        }
        if (apply) {
            icon.setBounds(pad, pad, iconSize, iconSize);
            title.setBounds(textX, y, titleSize.width, titleSize.height);
            close.setBounds(width - pad - closeSize, pad, closeSize, closeSize);
        }
        y += Math.max(titleHeight, iconSize);
        if (content.isVisible()) {
            y += UIScale.scale(4);
            Dimension contentSize = content.getPreferredSize();
            if (apply) content.setBounds(textX, y, contentSize.width, contentSize.height);
            y += contentSize.height;
        }
        if (actions.isVisible()) {
            y += gap;
            Dimension actionSize = actions.getPreferredSize();
            if (apply) actions.setBounds(textX, y, textWidth, actionSize.height);
            y += actionSize.height;
        }
        return y + pad;
    }

    @Override public void doLayout() { arrange(true); }
    @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(WIDTH), arrange(false)); }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = UIScale.scale(ARC);
            g.setColor(NotificationStyle.color("Jasper.notification.background", Color.WHITE));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.setColor(NotificationStyle.color("Jasper.notification.borderColor", Color.GRAY));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        } finally { g.dispose(); }
    }

    @Override public void updateUI() {
        super.updateUI();
        // JPanel's constructor calls this before the fields exist; the constructor styles the card itself.
        if (close != null) restyle();
    }

    private void restyle() {
        count.setForeground(NotificationStyle.color("Jasper.notification.mutedForeground", Color.GRAY));
        for (Component link : actions.getComponents()) link.setForeground(NotificationStyle.color("Jasper.notification.linkForeground", Color.BLUE));
        close.setBorder(BorderFactory.createEmptyBorder());
    }
}
