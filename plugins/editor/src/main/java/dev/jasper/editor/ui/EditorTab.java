package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.sdk.Variant;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

/** One tab's view (spec 2, 2.3): the text area bound to its session, the loading and failure states, and the banner. */
public final class EditorTab extends JPanel {
    private static final String TEXT = "text", STATE = "state";
    private final DocumentSession session;
    private final RSyntaxTextArea area = new RSyntaxTextArea();
    private final RTextScrollPane scroll;
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JLabel stateText = new JLabel();
    private final JButton retry = new JButton("Retry");
    private final JButton dismiss = new JButton("Close");
    private final JPanel banner = new JPanel(new BorderLayout(8, 0));
    private final JLabel bannerText = new JLabel();
    private final JButton bannerFirst = new JButton();
    private final JButton bannerSecond = new JButton();
    private final Timer edits;
    private final boolean debounce;
    private boolean replacing;
    private String showing = STATE;

    /**
     * @param session the tab's document
     * @param close closes this tab (Cancel, Close and the deleted banner's Close)
     * @param debounce whether edits reach the session 300 ms after typing stops; tests pass false for at once
     */
    public EditorTab(DocumentSession session, Consumer<EditorTab> close, boolean debounce) {
        super(new BorderLayout());
        this.session = session;
        this.debounce = debounce;
        area.setCodeFoldingEnabled(true);
        area.setMarkOccurrences(true);
        area.setBracketMatchingEnabled(true);
        area.setAnimateBracketMatching(false);
        area.setHighlightCurrentLine(true);
        area.setAntiAliasingEnabled(true);
        scroll = new RTextScrollPane(area);
        scroll.setLineNumbersEnabled(true);
        scroll.setFoldIndicatorEnabled(true);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        edits = new Timer(300, event -> session.textEdited());
        edits.setRepeats(false);
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { edited(); }
            @Override public void removeUpdate(DocumentEvent event) { edited(); }
            @Override public void changedUpdate(DocumentEvent event) { }
        });
        session.attach(area::getText, this::replaceText);
        stateText.putClientProperty("html.disable", true);
        bannerText.putClientProperty("html.disable", true);
        stateText.setAlignmentX(CENTER_ALIGNMENT);
        var buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        buttons.setOpaque(false);
        buttons.add(retry);
        buttons.add(dismiss);
        var stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        stack.add(stateText);
        stack.add(buttons);
        var state = new JPanel(new GridBagLayout());
        state.add(stack);
        body.add(scroll, TEXT);
        body.add(state, STATE);
        retry.addActionListener(event -> session.load());
        dismiss.addActionListener(event -> { session.cancelLoad(); close.accept(this); });
        var bannerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        bannerButtons.setOpaque(false);
        bannerButtons.add(bannerFirst);
        bannerButtons.add(bannerSecond);
        banner.add(bannerText, BorderLayout.CENTER);
        banner.add(bannerButtons, BorderLayout.EAST);
        bannerFirst.addActionListener(event -> {
            if (session.banner() == DocumentSession.Banner.DELETED) close.accept(this);
            else session.reloadFromBanner();
        });
        bannerSecond.addActionListener(event -> {
            if (session.banner() == DocumentSession.Banner.DELETED) session.keepDeleted();
            else session.keepMine();
        });
        add(banner, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        session.addListener(this::refresh);
        refresh();
    }

    public DocumentSession session() { return session; }
    public RSyntaxTextArea area() { return area; }
    public RTextScrollPane scroll() { return scroll; }

    /**
     * @param settings font and tab size
     * @param theme the colours
     * @param variant Jasper's light or dark look
     */
    public void apply(EditorSettings settings, EditorTheme theme, Variant variant) {
        theme.apply(area, scroll, variant);
        Font font = settings.font();
        area.setFont(font);
        scroll.getGutter().setLineNumberFont(font);
        area.setTabSize(settings.tabSize());
        Color rule = UIManager.getColor("Separator.foreground");
        banner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, rule == null ? Color.GRAY : rule), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
    }

    /** @param commands bound in the text area's own map, which overrides RSyntaxTextArea's defaults for the same strokes */
    public void bind(List<Command> commands) {
        for (Command command : commands) {
            if (!command.bound() || command.stroke().isEmpty()) continue;
            area.getInputMap(JComponent.WHEN_FOCUSED).put(command.stroke().get(), command.id());
            area.getActionMap().put(command.id(), action(command));
        }
    }

    /** @param commands added below RSyntaxTextArea's own right-click items */
    public void addToPopup(List<Command> commands) {
        JPopupMenu popup = area.getPopupMenu();
        popup.addSeparator();
        for (Command command : commands) {
            var item = new JMenuItem(command.title());
            command.stroke().ifPresent(item::setAccelerator);
            item.addActionListener(event -> command.run().run());
            popup.add(item);
        }
    }

    /**
     * @param command a command
     * @return a Swing action running it
     */
    public static Action action(Command command) {
        return new AbstractAction(command.title()) {
            @Override public void actionPerformed(ActionEvent event) { command.run().run(); }
        };
    }

    boolean showingText() { return showing.equals(TEXT); }
    String stateText() { return stateText.getText(); }
    JButton retryButton() { return retry; }
    JButton dismissButton() { return dismiss; }
    boolean bannerVisible() { return banner.isVisible(); }
    String bannerText() { return bannerText.getText(); }
    JButton bannerFirst() { return bannerFirst; }
    JButton bannerSecond() { return bannerSecond; }

    private void edited() {
        if (replacing) return;
        if (debounce) edits.restart();
        else session.textEdited();
    }

    private void replaceText(String text, boolean keepPosition) {
        int line = keepPosition ? area.getCaretLineNumber() : 0;
        replacing = true;
        try { area.setText(text); }
        finally { replacing = false; }
        area.discardAllEdits();
        try { area.setCaretPosition(area.getLineStartOffset(Math.min(line, area.getLineCount() - 1))); }
        catch (BadLocationException impossible) { area.setCaretPosition(0); }
    }

    private void refresh() {
        switch (session.state()) {
            case LOADING -> {
                stateText.setText("Loading " + session.name() + session.host().map(host -> " from " + host).orElse("") + "…");
                retry.setVisible(false);
                dismiss.setText("Cancel");
                showCard(STATE);
            }
            case FAILED -> {
                stateText.setText(session.failure());
                retry.setVisible(session.retryable());
                dismiss.setText("Close");
                showCard(STATE);
            }
            case READY -> showCard(TEXT);
        }
        if (!area.getSyntaxEditingStyle().equals(session.style())) area.setSyntaxEditingStyle(session.style());
        area.setLineWrap(session.wrap());
        area.setWrapStyleWord(true);
        area.setTabsEmulated(!session.tabs());
        switch (session.banner()) {
            case NONE -> banner.setVisible(false);
            case CHANGED -> banner(session.name() + " changed on disk.", "Reload", "Keep Mine");
            case DELETED -> banner(session.name() + " was deleted.", "Close", "Keep");
        }
    }

    private void showCard(String card) {
        showing = card;
        cards.show(body, card);
    }

    private void banner(String text, String first, String second) {
        bannerText.setText(text);
        bannerFirst.setText(first);
        bannerSecond.setText(second);
        banner.setVisible(true);
    }
}
