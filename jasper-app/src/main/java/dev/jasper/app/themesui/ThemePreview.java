package dev.jasper.app.themesui;

import dev.jasper.app.appearance.GeneratedTheme;
import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.appearance.Token;
import dev.jasper.terminal.config.TerminalOptions;
import java.awt.*;
import javax.swing.*;

/** A locally styled miniature; never installs a look and feel. EDT-owned by ThemesPage. */
final class ThemePreview implements AutoCloseable {
    private final TerminalOptions options;
    private final JPanel root = new JPanel(new BorderLayout());
    private final Strip title = new Strip("theme-preview-title", "Jasper", true);
    private final Strip sidebar = new Strip("theme-preview-sidebar", "Projects", false);
    private final Strip status = new Strip("theme-preview-status", "Ready", false);
    private final Strip tabs = new Strip("theme-preview-tabs", "Terminal     Sample.java", false);
    private final JPanel content = new JPanel(new GridLayout(1,2));
    final SyntaxSample editor = new SyntaxSample();
    SampleTerminal sample;
    ResolvedTheme themes;
    private boolean closed;
    ThemePreview(TerminalOptions options) {
        this.options = options;
        root.setPreferredSize(new Dimension(620, 240));
        title.setPreferredSize(new Dimension(1,34)); status.setPreferredSize(new Dimension(1,26));
        sidebar.setPreferredSize(new Dimension(90,1)); tabs.setPreferredSize(new Dimension(1,30));
        var workspace = new JPanel(new BorderLayout()); workspace.add(tabs,BorderLayout.NORTH); workspace.add(content);
        root.add(title,BorderLayout.NORTH); root.add(status,BorderLayout.SOUTH); root.add(sidebar,BorderLayout.WEST); root.add(workspace);
    }
    JComponent component() { return root; }
    void show(ResolvedTheme themes) {
        if(closed) return;
        this.themes = themes;
        if(sample == null) { sample = new SampleTerminal(options); content.add(sample.component()); content.add(editor); }
        var chrome = themes.chrome(); var work = themes.workspace();
        title.theme(chrome, Token.TITLE_BAR); sidebar.theme(chrome,Token.SIDEBAR); status.theme(chrome,Token.STATUS_BAR);
        tabs.theme(work,Token.TAB_BAR); tabs.active = work.tokens().get(Token.TAB_ACTIVE); tabs.foreground = work.tokens().get(Token.TAB_ACTIVE_TEXT);
        root.setBorder(BorderFactory.createLineBorder(chrome.tokens().get(Token.BORDER)));
        sample.palette(work.terminal()); editor.theme(work); root.repaint();
    }
    @Override public void close() { if(closed)return; closed=true; if(sample!=null)sample.close(); }
    private static final class Strip extends JComponent {
        private final String text; private final boolean search;
        private Color background, foreground, input, border, active;
        Strip(String name,String text,boolean search) { setName(name); this.text=text; this.search=search; getAccessibleContext(); }
        void theme(GeneratedTheme theme,Token token) {
            background=theme.tokens().get(token); foreground=theme.tokens().get(Token.TEXT);
            input=theme.tokens().get(Token.INPUT_BACKGROUND); border=theme.tokens().get(Token.BORDER);
        }
        @Override protected void paintComponent(Graphics g) {
            if(background==null)return;
            g.setColor(background); g.fillRect(0,0,getWidth(),getHeight());
            if(active!=null){g.setColor(active);g.fillRect(0,0,180,getHeight());}
            g.setColor(foreground); g.drawString(text,10,19);
            if(search){g.setColor(input);g.fillRect(110,5,Math.max(0,getWidth()-140),24);g.setColor(border);g.drawRect(110,5,Math.max(0,getWidth()-140),24);g.setColor(foreground);g.drawString("Search",120,22);}
        }
    }
}
