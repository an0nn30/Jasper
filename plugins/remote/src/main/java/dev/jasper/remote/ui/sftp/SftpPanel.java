package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.ui.PanelStyle;
import dev.jasper.sdk.ui.WorkspacePalette;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PanelHeader;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.table.*;

/** Compact file explorer. Controls use host artwork; directory rows use the reference's blue folder glyph. */
public class SftpPanel extends JPanel {
    public record Actions(Consumer<String> navigate,Runnable up,Runnable refresh,Consumer<Boolean> follow,
                          Runnable upload,Runnable uploadFolder,Runnable download,Runnable newFolder,
                          Runnable delete,Runnable copyPath,Runnable copyToHost,Runnable cancel,Consumer<Long> page) {}
    /** What double-click, Enter and Open in Editor do with files and links; set only when the Editor is installed. */
    public record Editing(Consumer<FileEntry> activate,Runnable open) {}
    private final Function<IconName,Icon> icons;
    private final Icon directoryIcon,documentIcon;
    private final JLabel host=label("Select an SSH host to browse files"),message=label(""),pageLabel=label(""),operationResult=label("");
    private final JTextField path=new PathField();
    private final JCheckBox follow=new JCheckBox("",true);
    private final JLabel followLabel=label("Follow terminal folder");
    private final JButton chooseSession=new JButton("Choose Session");
    private final CardLayout cards=new CardLayout();
    private final JPanel content=new JPanel(cards),browser=new JPanel(new BorderLayout());
    private final JPanel paging=new JPanel(new BorderLayout(4,0)),status=new JPanel(new BorderLayout(4,0));
    private final JPanel followFooter=new JPanel(new BorderLayout());
    private boolean destination;
    private Runnable choose=()->{};
    private final Rows model=new Rows();
    private final FileTable table=new FileTable();
    private final JButton previous, next, cancel;
    private Runnable back=()->{},forward=()->{};
    private boolean canBack,canForward,working,browsingEnabled=true;
    private Consumer<PanelHeader> headerChanged=header->{};
    private final Map<String,AbstractButton> buttons=new LinkedHashMap<>();
    private final JPopupMenu popup=new JPopupMenu();
    private final JToolBar toolbar=new dev.jasper.remote.ui.FlatToolBar();
    private final JMenuItem openInEditor;
    private final JPopupMenu.Separator editingSeparator=new JPopupMenu.Separator();
    private Editing editing;
    private List<FileEntry> entries=List.of();
    private Actions actions;
    private long offset,total;
    private boolean loaded;
    private Runnable onLoaded=()->{};
    public void onLoaded(Runnable listener) { onLoaded=listener; }
    private final JPanel transfersSlot=new JPanel(new BorderLayout());
    /** The window's transfer strip, shown at the bottom of the sidebar. */
    public void transfers(JComponent strip) { transfersSlot.removeAll();transfersSlot.add(strip,BorderLayout.CENTER);transfersSlot.revalidate(); }
    private final TableColumn sizeColumn;
    public SftpPanel(Function<IconName,Icon> icons) {
        super(new BorderLayout());this.icons=icons;directoryIcon=new FolderIcon(icons.apply(IconName.FOLDER));documentIcon=new DocumentIcon(icons.apply(IconName.FILE));previous=new JButton(icons.apply(IconName.PREVIOUS));next=new JButton(icons.apply(IconName.NEXT));
        browser.setBorder(BorderFactory.createEmptyBorder());
        chooseSession.setIcon(icons.apply(IconName.DROPDOWN));chooseSession.setHorizontalTextPosition(SwingConstants.LEFT);
        chooseSession.getAccessibleContext().setAccessibleName("Choose Session");chooseSession.addActionListener(event->choose.run());
        content.add(PanelStyle.emptyState(icons.apply(IconName.FOLDER),"Not Connected","Connect to an SSH session to browse its files.",chooseSession),"disconnected");
        content.add(browser,"connected");add(content,BorderLayout.CENTER);
        var north=new JPanel(new BorderLayout(4,0));
        north.setBorder(new javax.swing.border.AbstractBorder() {
            @Override public Insets getBorderInsets(Component component,Insets insets) { insets.set(PanelStyle.scale(component,8),PanelStyle.scale(component,10),PanelStyle.scale(component,9),PanelStyle.scale(component,10));return insets; }
            @Override public void paintBorder(Component component,Graphics graphics,int x,int y,int width,int height) { graphics.setColor(WorkspacePalette.current(component).border());graphics.fillRect(x,y+height-1,width,1); }
        });
        addButton(toolbar,"Back",IconName.PREVIOUS,()->back.run());
        addButton(toolbar,"Forward",IconName.NEXT,()->forward.run());
        toolbar.setBorder(new javax.swing.border.AbstractBorder() {
            @Override public Insets getBorderInsets(Component component,Insets insets) { insets.set(1,1,1,1);return insets; }
            @Override public void paintBorder(Component component,Graphics graphics,int x,int y,int width,int height) {
                var g=(Graphics2D)graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(WorkspacePalette.current(component).border());int arc=PanelStyle.scale(component,8);
                    g.drawRoundRect(x,y,width-1,height-1,arc,arc);g.drawLine(x+width/2,y+1,x+width/2,y+height-2);
                } finally { g.dispose(); }
            }
        });toolbar.setBorderPainted(true);
        north.add(toolbar,BorderLayout.WEST);
        path.getAccessibleContext().setAccessibleName("Remote directory");path.putClientProperty("JTextField.minimumWidth",0);
        north.add(path,BorderLayout.CENTER);
        var fileActions=new dev.jasper.remote.ui.FlatToolBar();
        addButton(fileActions,"Refresh",IconName.REFRESH,()->actions.refresh().run());
        buttons.get("Refresh").setIcon(new RefreshIcon(icons.apply(IconName.REFRESH)));
        addButton(fileActions,"Upload files",IconName.UPLOAD,()->actions.upload().run());
        north.add(fileActions,BorderLayout.EAST);browser.add(north,BorderLayout.NORTH);
        table.setModel(model);table.setAutoCreateRowSorter(false);table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);table.setFillsViewportHeight(true);table.setDefaultRenderer(Object.class,new Renderer());table.getTableHeader().setReorderingAllowed(false);
        table.setShowGrid(false);table.setIntercellSpacing(new Dimension(0,0));
        table.getColumnModel().getColumn(0).setPreferredWidth(240);
        sizeColumn=table.getColumnModel().getColumn(1);sizeColumn.setPreferredWidth(68);sizeColumn.setMinWidth(58);sizeColumn.setMaxWidth(90);
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        var scroll=new JScrollPane(table);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);browser.add(scroll,BorderLayout.CENTER);
        var south=new JPanel();south.setLayout(new BoxLayout(south,BoxLayout.Y_AXIS));
        var controls=new dev.jasper.remote.ui.FlatToolBar();for(var chevron:List.of(previous,next)) { String name=chevron==previous?"Previous page":"Next page";chevron.setToolTipText(name);chevron.getAccessibleContext().setAccessibleName(name);controls.add(chevron); }paging.add(pageLabel,BorderLayout.CENTER);paging.add(controls,BorderLayout.EAST);paging.setBorder(BorderFactory.createEmptyBorder(0,8,0,8));paging.setVisible(false);south.add(paging);
        status.setBorder(BorderFactory.createEmptyBorder(4,16,4,16));status.setVisible(false);cancel=new JButton("Cancel",icons.apply(IconName.CLOSE));cancel.setVisible(false);status.add(message,BorderLayout.CENTER);status.add(cancel,BorderLayout.EAST);south.add(status);operationResult.setVisible(false);operationResult.setBorder(BorderFactory.createEmptyBorder(4,16,4,16));south.add(operationResult);
        configureFollow();south.add(followFooter);south.add(transfersSlot);for(var row:south.getComponents()) ((JComponent)row).setAlignmentX(LEFT_ALIGNMENT);add(south,BorderLayout.SOUTH);
        path.addActionListener(event->{if(actions!=null) actions.navigate().accept(path.getText());});follow.addActionListener(event->{if(actions!=null) actions.follow().accept(follow.isSelected());});
        previous.addActionListener(event->{if(actions!=null) actions.page().accept(Math.max(0,offset-200));});next.addActionListener(event->{if(actions!=null) actions.page().accept(offset+200);});cancel.addActionListener(event->{if(actions!=null) actions.cancel().run();});
        table.getSelectionModel().addListSelectionListener(event->selectionChanged());
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"browse");table.getActionMap().put("browse",new AbstractAction() { public void actionPerformed(ActionEvent event) { activate(); } });
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,InputEvent.ALT_DOWN_MASK),"parent");table.getActionMap().put("parent",new AbstractAction() { public void actionPerformed(ActionEvent event) { if(actions!=null && loaded && !working) actions.up().run(); } });
        table.addMouseListener(new MouseAdapter() {
            private void selectPopup(MouseEvent event) { if(event.isPopupTrigger()) { int row=table.rowAtPoint(event.getPoint());if(row>=0 && !table.isRowSelected(row)) table.setRowSelectionInterval(row,row); } }
            @Override public void mousePressed(MouseEvent event) { selectPopup(event); }
            @Override public void mouseReleased(MouseEvent event) { selectPopup(event); }
            @Override public void mouseClicked(MouseEvent event) { if(event.getClickCount()==2 && SwingUtilities.isLeftMouseButton(event)) activate(); } });
        openInEditor=new JMenuItem("Open in Editor",icons.apply(IconName.FILE));openInEditor.addActionListener(event->{if(editing!=null) editing.open().run();});
        openInEditor.setVisible(false);editingSeparator.setVisible(false);popup.add(openInEditor);popup.add(editingSeparator);
        item("Up",IconName.UP,()->actions.up().run());item("New folder…",IconName.NEW_FOLDER,()->actions.newFolder().run());popup.addSeparator();
        item("Upload folder…",IconName.UPLOAD,()->actions.uploadFolder().run());item("Download selected…",IconName.DOWNLOAD,()->actions.download().run());item("Copy to host…",IconName.NETWORK,()->actions.copyToHost().run());popup.addSeparator();item("Copy paths",IconName.COPY,()->actions.copyPath().run());item("Delete selected…",IconName.DELETE,()->actions.delete().run());table.setComponentPopupMenu(popup);
        buttons.get("Upload files").setComponentPopupMenu(popup);
        bindNavigation("back",KeyEvent.VK_LEFT,()-> { if(browsingEnabled && canBack && !working) back.run(); });
        bindNavigation("forward",KeyEvent.VK_RIGHT,()-> { if(browsingEnabled && canForward && !working) forward.run(); });
        selectionChanged();
    }
    private static JLabel label(String value) { var label=new JLabel(value);label.putClientProperty("html.disable",true);return label; }
    private void addButton(JComponent bar,String name,IconName icon,Runnable action) {
        var button=new JButton(icons.apply(icon));button.putClientProperty("JButton.minimumWidth",0);button.setToolTipText(name);button.getAccessibleContext().setAccessibleName(name);button.setMargin(new Insets(4,4,4,4));button.addActionListener(event->{if(actions!=null && !working) action.run();});buttons.put(name,button);bar.add(button);
    }
    private void item(String text,IconName name,Runnable action) { var item=new JMenuItem(text,icons.apply(name));item.addActionListener(event->{if(actions!=null) action.run();});popup.add(item);buttons.put(text,item); }
    public void actions(Actions actions) { this.actions=actions; }
    /** Navigation commands apply to the currently connected browser's history. */
    public void navigation(Runnable back,Runnable forward) { this.back=Objects.requireNonNull(back);this.forward=Objects.requireNonNull(forward); }
    public void navigationAvailable(boolean back,boolean forward) { canBack=back;canForward=forward;updateControls(); }
    /** Publishes connection detail to the owning app header, including the current state immediately. */
    public void onHeaderChanged(Consumer<PanelHeader> listener) { headerChanged=Objects.requireNonNull(listener);publishHeader(); }
    private void publishHeader() { if(!destination) headerChanged.accept(new PanelHeader(loaded?host.getText():"",loaded,List.of())); }
    private void bindNavigation(String name,int key,Runnable command) {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key,InputEvent.ALT_DOWN_MASK),name);
        getActionMap().put(name,new AbstractAction() { @Override public void actionPerformed(ActionEvent event) { command.run(); } });
    }
    /** Opens the owning window's existing SSH host chooser from the disconnected state. */
    public void onChooseSession(Runnable action) { choose=Objects.requireNonNull(action); }
    /** Shows Open in Editor and sends files to {@code editing}: double-click and Enter on one file or link, and the menu item. */
    public void editing(Editing editing) { this.editing=editing;openInEditor.setVisible(true);editingSeparator.setVisible(true);selectionChanged(); }
    public FileTable table() { return table; }
    public List<FileEntry> selection() { var result=new ArrayList<FileEntry>();for(int row:table.getSelectedRows()) if(row>=0 && row<entries.size()) result.add(entries.get(row));return List.copyOf(result); }
    public String directory() { return path.getText(); }
    public boolean following() { return follow.isSelected(); }
    public void following(boolean value) { follow.setSelected(value); }
    public void destinationMode() {
        destination=true;followFooter.setVisible(false);showBrowser();
        buttons.get("Upload files").setVisible(false);
        var newFolder=new JButton(icons.apply(IconName.NEW_FOLDER));
        newFolder.setToolTipText("New folder");newFolder.getAccessibleContext().setAccessibleName("New folder");
        newFolder.putClientProperty("JButton.minimumWidth",0);newFolder.setMargin(new Insets(4,4,4,4));
        newFolder.addActionListener(event-> { if(actions!=null && !working) actions.newFolder().run(); });
        ((JComponent)buttons.get("Refresh").getParent()).add(newFolder);buttons.put("Destination new folder",newFolder);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setComponentPopupMenu(null);updateControls();
    }
    public void showPage(String hostname,String directory,List<FileEntry> values,long offset,long total,boolean focus) {
        this.entries=List.copyOf(values);this.offset=offset;this.total=total;loaded=true;showBrowser();host.setText(hostname);path.setText(directory);path.setToolTipText(directory);model.fireTableDataChanged();
        pageLabel.setText(total==0?"Empty folder":(offset+1)+"–"+(offset+values.size())+" of "+total);previous.setEnabled(offset>0);next.setEnabled(offset+values.size()<total);
        if(!entries.isEmpty()) table.setRowSelectionInterval(0,0);selectionChanged();busy(false,"");publishHeader();onLoaded.run();if(focus) table.requestFocusInWindow();
    }
    public void busy(boolean busy,String text) {
        working=busy;chooseSession.setEnabled(!busy);status.setVisible(busy || !text.isEmpty());cancel.setVisible(busy);
        table.setEnabled(!busy);path.setEnabled(browsingEnabled && !busy);updateControls();
        message.setText(text);message.setToolTipText(text);revalidate();repaint();
    }
    public void error(String text) { busy(false,text); }
    /** Empties the view: no host, folder or rows, and nothing for file actions to act on. */
    public void clear(String text) {
        entries=List.of();offset=0;total=0;loaded=false;host.setText("Select an SSH host to browse files");path.setText("");model.fireTableDataChanged();pageLabel.setText("");
        canBack=false;canForward=false;selectionChanged();showBrowser();busy(false,text);publishHeader();
    }
    public void operationStatus(boolean running,String text) {
        operationResult.setText(running?"":text);operationResult.setToolTipText(running?null:text);operationResult.setVisible(!running && !text.isEmpty());
        busy(running,running?text:"");
    }
    private void showBrowser() {
        cards.show(content,loaded || destination?"connected":"disconnected");paging.setVisible(loaded && total>200);
        revalidate();repaint();
    }
    private void configureFollow() {
        follow.getAccessibleContext().setAccessibleName("Follow terminal folder");
        follow.getAccessibleContext().setAccessibleDescription("Keep the SFTP browser in the active SSH terminal's folder");
        follow.setToolTipText("Follow terminal folder");follow.setOpaque(false);follow.setBorder(BorderFactory.createEmptyBorder());follow.setMargin(new Insets(0,0,0,0));
        var icon=new FollowIcon();follow.setIcon(icon);follow.setSelectedIcon(icon);follow.setDisabledIcon(icon);follow.setDisabledSelectedIcon(icon);follow.setFocusPainted(false);
        followFooter.setBorder(new javax.swing.border.AbstractBorder() {
            @Override public Insets getBorderInsets(Component component,Insets insets) { insets.set(PanelStyle.scale(component,12),PanelStyle.scale(component,16),PanelStyle.scale(component,12),PanelStyle.scale(component,16));return insets; }
            @Override public void paintBorder(Component component,Graphics graphics,int x,int y,int width,int height) { graphics.setColor(WorkspacePalette.current(component).border());graphics.fillRect(x,y,width,1); }
        });
        followLabel.setLabelFor(follow);followFooter.add(followLabel,BorderLayout.CENTER);followFooter.add(follow,BorderLayout.EAST);
    }
    private final class FollowIcon implements Icon {
        @Override public int getIconWidth() { return PanelStyle.scale(follow,34); }
        @Override public int getIconHeight() { return PanelStyle.scale(follow,20); }
        @Override public void paintIcon(Component component,Graphics graphics,int x,int y) {
            var g=(Graphics2D)graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.translate(x,y);double factor=getIconWidth()/34.0;g.scale(factor,factor);var palette=WorkspacePalette.current(component);
                g.setColor(!follow.isEnabled()?palette.surfaceSunken():follow.isSelected()?PanelStyle.accent(follow):palette.border());
                g.fillRoundRect(2,2,30,16,16,16);
                g.setColor(follow.isEnabled()?Color.WHITE:palette.textMuted());g.fillOval(follow.isSelected()?18:4,4,12,12);
                if(follow.hasFocus()) { g.setColor(PanelStyle.accent(follow));g.drawRoundRect(0,0,33,19,19,19); }
            }finally { g.dispose(); }
        }
    }
    String operationMessage() { return operationResult.getText(); }
    String hostText() { return host.getText(); }
    String messageText() { return message.getText(); }
    public void enableBrowsing(boolean enabled) { browsingEnabled=enabled;path.setEnabled(enabled && !working);follow.setEnabled(enabled);followLabel.setEnabled(enabled);updateControls(); }
    private void activate() {
        if(actions==null || working || !loaded) return;var selected=selection();if(selected.size()!=1) return;var entry=selected.getFirst();
        if(entry.kind()==FileEntry.Kind.DIRECTORY || entry.kind()==FileEntry.Kind.LINK && editing==null) actions.navigate().accept(entry.name());
        else if(editing!=null && (entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK)) editing.activate().accept(entry);
    }
    private void selectionChanged() { updateControls(); }
    private void updateControls() {
        boolean ready=loaded && !working,selected=ready && !selection().isEmpty();
        for(String name:List.of("Download selected…","Delete selected…","Copy paths","Copy to host…")) {
            var button=buttons.get(name);if(button!=null) button.setEnabled(selected);
        }
        for(String name:List.of("Upload files","Upload folder…","New folder…","Destination new folder")) {
            var button=buttons.get(name);if(button!=null) button.setEnabled(ready);
        }
        var up=buttons.get("Up");if(up!=null) up.setEnabled(ready && !path.getText().equals("/"));
        buttons.get("Refresh").setEnabled(browsingEnabled && ready);
        buttons.get("Back").setEnabled(browsingEnabled && ready && canBack);
        buttons.get("Forward").setEnabled(browsingEnabled && ready && canForward);
        previous.setEnabled(ready && offset>0);next.setEnabled(ready && offset+entries.size()<total);
        if(openInEditor!=null) openInEditor.setEnabled(ready && selection().stream().anyMatch(entry->entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK));
    }
    public final class FileTable extends JTable {
        @Override public void setFont(Font font) { super.setFont(font);updateRowHeight(); }
        @Override public JToolTip createToolTip() { var tip=super.createToolTip();tip.putClientProperty("html.disable",true);return tip; }
        @Override public void updateUI() { super.updateUI();updateRowHeight(); }
        public void updateRowHeight() { if(getFont()!=null) setRowHeight(Math.max(27,Math.max(PanelStyle.scale(this,27),getFontMetrics(getFont()).getHeight()+8))); }
        @Override public void doLayout() {
            if(sizeColumn!=null) {
                int width=getFontMetrics(getFont()).stringWidth("999.9 GB")+PanelStyle.scale(this,28);
                sizeColumn.setMinWidth(width);sizeColumn.setMaxWidth(width);sizeColumn.setPreferredWidth(width);
            }
            super.doLayout();
        }
        @Override public String getToolTipText(MouseEvent event) { int row=rowAtPoint(event.getPoint());if(row<0 || row>=entries.size()) return null;var file=entries.get(row);return file.name()+" — "+formatSize(file)+" — "+modified(file); }
    }
    private final class Rows extends AbstractTableModel {
        public int getRowCount() { return entries.size(); }public int getColumnCount() { return 2; }
        public String getColumnName(int column) { return switch(column) { case 0->"Name";case 1->"Size";default->"Modified"; }; }
        public Object getValueAt(int row,int column) { var file=entries.get(row);return switch(column) { case 0->file.name();case 1->formatSize(file);default->modified(file); }; }
    }
    private final class Renderer extends DefaultTableCellRenderer {
        Renderer() { putClientProperty("html.disable",true);setBorder(BorderFactory.createEmptyBorder(0,3,0,3)); }
        @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int column) {
            super.getTableCellRendererComponent(table,value,selected,focus,row,column);setIcon(null);setHorizontalAlignment(column==1?SwingConstants.RIGHT:SwingConstants.LEFT);setBorder(BorderFactory.createEmptyBorder(0,PanelStyle.scale(table,14),0,PanelStyle.scale(table,14)));
            if(!selected) setForeground(column==1?WorkspacePalette.current(table).textMuted():WorkspacePalette.current(table).text());
            if(table.convertColumnIndexToModel(column)==0 && row<entries.size()) setIcon(entries.get(row).kind()==FileEntry.Kind.DIRECTORY?directoryIcon:entries.get(row).kind()==FileEntry.Kind.LINK?icons.apply(IconName.LINK):documentIcon);return this;
        }
    }
    /** Local artwork follows the size of the host's semantic folder; no global icon mapping changes. */
    private final class FolderIcon implements Icon {
        private final Icon size;
        FolderIcon(Icon size) { this.size=size; }
        @Override public int getIconWidth() { return size==null?PanelStyle.scale(table,16):size.getIconWidth(); }
        @Override public int getIconHeight() { return size==null?PanelStyle.scale(table,16):size.getIconHeight(); }
        @Override public void paintIcon(Component component,Graphics graphics,int x,int y) {
            var g=(Graphics2D)graphics.create();
            try {
                g.translate(x,y);g.scale(getIconWidth()/16.0,getIconHeight()/16.0);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                var shape=new java.awt.geom.Path2D.Double();
                shape.moveTo(2,3);shape.lineTo(6.3,3);shape.lineTo(8.2,5);shape.lineTo(14,5);
                shape.quadTo(15,5,15,6);shape.lineTo(15,12);shape.quadTo(15,13,14,13);
                shape.lineTo(2,13);shape.quadTo(1,13,1,12);shape.lineTo(1,4);shape.quadTo(1,3,2,3);shape.closePath();
                var background=WorkspacePalette.current(table).background();
                boolean dark=background.getRed()*.299+background.getGreen()*.587+background.getBlue()*.114<128;
                g.setColor(new Color(dark?0x5e9bed:0x397ac4));g.fill(shape);
            } finally { g.dispose(); }
        }
    }
    private abstract class SizedIcon implements Icon {
        private final Icon size;
        SizedIcon(Icon size) { this.size=size; }
        @Override public int getIconWidth() { return size==null?PanelStyle.scale(table,16):size.getIconWidth(); }
        @Override public int getIconHeight() { return size==null?PanelStyle.scale(table,16):size.getIconHeight(); }
        @Override public final void paintIcon(Component component,Graphics graphics,int x,int y) {
            var g=(Graphics2D)graphics.create();
            try {
                g.translate(x,y);g.scale(getIconWidth()/16.0,getIconHeight()/16.0);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(1.25f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.setColor(WorkspacePalette.current(table).textMuted());paintGlyph(g);
            } finally { g.dispose(); }
        }
        abstract void paintGlyph(Graphics2D graphics);
    }
    private final class DocumentIcon extends SizedIcon {
        DocumentIcon(Icon size) { super(size); }
        @Override void paintGlyph(Graphics2D g) {
            var outline=new java.awt.geom.Path2D.Double();outline.moveTo(3,1.5);outline.lineTo(9,1.5);
            outline.lineTo(13,5.5);outline.lineTo(13,14.5);outline.lineTo(3,14.5);outline.closePath();g.draw(outline);
            g.drawLine(9,2,9,5);g.drawLine(9,5,12,5);g.drawLine(6,8,10,8);g.drawLine(6,11,10,11);
        }
    }
    private final class RefreshIcon extends SizedIcon {
        RefreshIcon(Icon size) { super(size); }
        @Override void paintGlyph(Graphics2D g) {
            g.draw(new java.awt.geom.Arc2D.Double(2.5,2.5,11,11,45,290,java.awt.geom.Arc2D.OPEN));
            g.drawLine(9,3,13,3);g.drawLine(13,3,13,7);
        }
    }
    private final class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() { putClientProperty("html.disable",true); }
        @Override public Component getTableCellRendererComponent(JTable owner,Object value,boolean selected,boolean focus,int row,int column) {
            super.getTableCellRendererComponent(owner,value,false,false,row,column);
            setOpaque(true);var palette=WorkspacePalette.current(owner);setBackground(palette.background());setForeground(palette.textMuted());
            setFont(owner.getFont());setHorizontalAlignment(column==1?SwingConstants.RIGHT:SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,palette.border()),BorderFactory.createEmptyBorder(PanelStyle.scale(owner,7),PanelStyle.scale(owner,14),PanelStyle.scale(owner,6),PanelStyle.scale(owner,14))));
            return this;
        }
    }
    /** The underlying value always stays a valid server path; focus exposes the ordinary text editor. */
    private final class PathField extends JTextField {
        PathField() { putClientProperty("FlatLaf.style","arc: 10");addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { repaint(); }
            @Override public void focusLost(FocusEvent event) { repaint(); }
        }); }
        @Override public Dimension getMinimumSize() { return new Dimension(0,getPreferredSize().height); }
        @Override public Dimension getPreferredSize() { var size=super.getPreferredSize();size.height=Math.max(size.height,PanelStyle.scale(this,28));return size; }
        @Override protected void paintComponent(Graphics graphics) {
            if(hasFocus()) { super.paintComponent(graphics);return; }
            var g=(Graphics2D)graphics.create();
            try {
                var palette=WorkspacePalette.current(this);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(palette.surfaceSunken());int arc=PanelStyle.scale(this,10);g.fillRoundRect(0,0,getWidth(),getHeight(),arc,arc);
                g.setColor(isEnabled()?palette.text():palette.textMuted());g.setFont(getFont());
                var metrics=g.getFontMetrics();int inset=PanelStyle.scale(this,9);g.clipRect(inset,0,Math.max(0,getWidth()-2*inset),getHeight());
                String value=getText();String crumbs=value.equals("/")?"/":value.startsWith("/")?"/ "+value.substring(1).replace("/","  ›  "):value;
                int available=Math.max(0,getWidth()-2*inset);
                if(metrics.stringWidth(crumbs)>available && value.contains("/")) crumbs="… › "+value.substring(value.lastIndexOf('/')+1);
                if(metrics.stringWidth(crumbs)>available) {
                    while(!crumbs.isEmpty() && metrics.stringWidth(crumbs+"…")>available) crumbs=crumbs.substring(0,crumbs.offsetByCodePoints(crumbs.length(),-1));
                    crumbs=crumbs.isEmpty()?"":crumbs+"…";
                }
                g.drawString(crumbs,inset,(getHeight()-metrics.getHeight())/2+metrics.getAscent());
            } finally { g.dispose(); }
        }
    }
    private static String formatSize(FileEntry entry) { if(entry.kind()==FileEntry.Kind.DIRECTORY) return "—";long value=entry.size();if(value<0) return "—";if(value<1000) return value+" B";if(value<1000*1000) return String.format(Locale.ROOT,"%.1f KB",value/1000.0);if(value<1000L*1000*1000) return String.format(Locale.ROOT,"%.1f MB",value/(1000.0*1000));return String.format(Locale.ROOT,"%.1f GB",value/(1000.0*1000*1000)); }
    private static String modified(FileEntry entry) { return entry.modifiedMillis()<0?"":DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.modifiedMillis())); }
}
