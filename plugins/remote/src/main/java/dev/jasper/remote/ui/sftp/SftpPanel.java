package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.ui.PanelStyle;
import dev.jasper.sdk.ui.WorkspacePalette;
import dev.jasper.sdk.ui.IconName;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.table.*;

/** Compact native file explorer. Icons are injected exclusively from the host's semantic catalog. */
public class SftpPanel extends JPanel {
    public record Actions(Consumer<String> navigate,Runnable up,Runnable refresh,Consumer<Boolean> follow,
                          Runnable upload,Runnable uploadFolder,Runnable download,Runnable newFolder,
                          Runnable delete,Runnable copyPath,Runnable copyToHost,Runnable cancel,Consumer<Long> page) {}
    /** What double-click, Enter and Open in Editor do with files and links; set only when the Editor is installed. */
    public record Editing(Consumer<FileEntry> activate,Runnable open) {}
    private final Function<IconName,Icon> icons;
    private final JLabel host=label("Select an SSH host to browse files"),message=label(""),pageLabel=label(""),operationResult=label("");
    private final JTextField path=new JTextField();
    private final JCheckBox follow=new JCheckBox("",true);
    private final JLabel followLabel=label("Follow terminal folder");
    private final JButton chooseSession=new JButton("Choose Session");
    private final CardLayout cards=new CardLayout();
    private final JPanel content=new JPanel(cards),browser=new JPanel(new BorderLayout(0,6));
    private final JPanel paging=new JPanel(new BorderLayout(4,0)),status=new JPanel(new BorderLayout(4,0));
    private final JPanel followFooter=new JPanel(new BorderLayout());
    private boolean destination;
    private Runnable choose=()->{};
    private final Rows model=new Rows();
    private final FileTable table=new FileTable();
    private final JButton previous, next, cancel;
    private final Map<String,JButton> buttons=new LinkedHashMap<>();
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
    private final TableColumn sizeColumn,modifiedColumn;
    private boolean details=true;
    public SftpPanel(Function<IconName,Icon> icons) {
        super(new BorderLayout());this.icons=icons;previous=new JButton(icons.apply(IconName.PREVIOUS));next=new JButton(icons.apply(IconName.NEXT));
        browser.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        chooseSession.setIcon(icons.apply(IconName.DROPDOWN));chooseSession.setHorizontalTextPosition(SwingConstants.LEFT);
        chooseSession.getAccessibleContext().setAccessibleName("Choose Session");chooseSession.addActionListener(event->choose.run());
        content.add(PanelStyle.emptyState(icons.apply(IconName.FOLDER),"Not Connected","Connect to an SSH session to browse its files.",chooseSession),"disconnected");
        content.add(browser,"connected");add(content,BorderLayout.CENTER);
        var north=new JPanel();north.setLayout(new BoxLayout(north,BoxLayout.Y_AXIS));
        host.setAlignmentX(LEFT_ALIGNMENT);north.add(host);north.add(Box.createVerticalStrut(6));
        addButton(toolbar,"Up",IconName.UP,()->actions.up().run());addButton(toolbar,"Download selected",IconName.DOWNLOAD,()->actions.download().run());
        addButton(toolbar,"Upload files",IconName.UPLOAD,()->actions.upload().run());addButton(toolbar,"Refresh",IconName.REFRESH,()->actions.refresh().run());
        addButton(toolbar,"New folder",IconName.NEW_FOLDER,()->actions.newFolder().run());addButton(toolbar,"Delete selected",IconName.DELETE,()->actions.delete().run());addButton(toolbar,"Copy paths",IconName.COPY,()->actions.copyPath().run());
        toolbar.setAlignmentX(LEFT_ALIGNMENT);north.add(toolbar);north.add(Box.createVerticalStrut(6));path.setAlignmentX(LEFT_ALIGNMENT);path.getAccessibleContext().setAccessibleName("Remote directory");north.add(path);browser.add(north,BorderLayout.NORTH);
        table.setModel(model);table.setAutoCreateRowSorter(false);table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);table.setFillsViewportHeight(true);table.setDefaultRenderer(Object.class,new Renderer());table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(240);sizeColumn=table.getColumnModel().getColumn(1);modifiedColumn=table.getColumnModel().getColumn(2);sizeColumn.setPreferredWidth(70);modifiedColumn.setPreferredWidth(130);
        var scroll=new JScrollPane(table);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);browser.add(scroll,BorderLayout.CENTER);
        var south=new JPanel();south.setLayout(new BoxLayout(south,BoxLayout.Y_AXIS));
        var controls=new dev.jasper.remote.ui.FlatToolBar();for(var chevron:List.of(previous,next)) { String name=chevron==previous?"Previous page":"Next page";chevron.setToolTipText(name);chevron.getAccessibleContext().setAccessibleName(name);controls.add(chevron); }paging.add(pageLabel,BorderLayout.CENTER);paging.add(controls,BorderLayout.EAST);paging.setBorder(BorderFactory.createEmptyBorder(0,8,0,8));paging.setVisible(false);south.add(paging);
        status.setBorder(BorderFactory.createEmptyBorder(4,16,4,16));status.setVisible(false);cancel=new JButton("Cancel",icons.apply(IconName.CLOSE));cancel.setVisible(false);status.add(message,BorderLayout.CENTER);status.add(cancel,BorderLayout.EAST);south.add(status);operationResult.setVisible(false);operationResult.setBorder(BorderFactory.createEmptyBorder(4,16,4,16));south.add(operationResult);
        configureFollow();south.add(followFooter);south.add(transfersSlot);for(var row:south.getComponents()) ((JComponent)row).setAlignmentX(LEFT_ALIGNMENT);add(south,BorderLayout.SOUTH);
        path.addActionListener(event->{if(actions!=null) actions.navigate().accept(path.getText());});follow.addActionListener(event->{if(actions!=null) actions.follow().accept(follow.isSelected());});
        previous.addActionListener(event->{if(actions!=null) actions.page().accept(Math.max(0,offset-200));});next.addActionListener(event->{if(actions!=null) actions.page().accept(offset+200);});cancel.addActionListener(event->{if(actions!=null) actions.cancel().run();});
        table.getSelectionModel().addListSelectionListener(event->selectionChanged());
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"browse");table.getActionMap().put("browse",new AbstractAction() { public void actionPerformed(ActionEvent event) { activate(); } });
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,InputEvent.ALT_DOWN_MASK),"parent");table.getActionMap().put("parent",new AbstractAction() { public void actionPerformed(ActionEvent event) { if(actions!=null) actions.up().run(); } });
        table.addMouseListener(new MouseAdapter() {
            private void selectPopup(MouseEvent event) { if(event.isPopupTrigger()) { int row=table.rowAtPoint(event.getPoint());if(row>=0 && !table.isRowSelected(row)) table.setRowSelectionInterval(row,row); } }
            @Override public void mousePressed(MouseEvent event) { selectPopup(event); }
            @Override public void mouseReleased(MouseEvent event) { selectPopup(event); }
            @Override public void mouseClicked(MouseEvent event) { if(event.getClickCount()==2 && SwingUtilities.isLeftMouseButton(event)) activate(); } });
        openInEditor=new JMenuItem("Open in Editor",icons.apply(IconName.FILE));openInEditor.addActionListener(event->{if(editing!=null) editing.open().run();});
        openInEditor.setVisible(false);editingSeparator.setVisible(false);popup.add(openInEditor);popup.add(editingSeparator);
        item("Upload folder…",IconName.UPLOAD,()->actions.uploadFolder().run());item("Download selected…",IconName.DOWNLOAD,()->actions.download().run());item("Copy to host…",IconName.NETWORK,()->actions.copyToHost().run());popup.addSeparator();item("Copy paths",IconName.COPY,()->actions.copyPath().run());item("Delete selected…",IconName.DELETE,()->actions.delete().run());table.setComponentPopupMenu(popup);
        buttons.get("Upload files").setComponentPopupMenu(popup);
        addComponentListener(new ComponentAdapter() { @Override public void componentResized(ComponentEvent event) { columns(); } });selectionChanged();
    }
    private static JLabel label(String value) { var label=new JLabel(value);label.putClientProperty("html.disable",true);return label; }
    private void addButton(JComponent bar,String name,IconName icon,Runnable action) {
        var button=new JButton(icons.apply(icon));button.setToolTipText(name);button.getAccessibleContext().setAccessibleName(name);button.setMargin(new Insets(4,4,4,4));button.addActionListener(event->{if(actions!=null) action.run();});buttons.put(name,button);bar.add(button);
    }
    private void item(String text,IconName name,Runnable action) { var item=new JMenuItem(text,icons.apply(name));item.addActionListener(event->{if(actions!=null) action.run();});popup.add(item); }
    public void actions(Actions actions) { this.actions=actions; }
    /** Opens the owning window's existing SSH host chooser from the disconnected state. */
    public void onChooseSession(Runnable action) { choose=Objects.requireNonNull(action); }
    /** Shows Open in Editor and sends files to {@code editing}: double-click and Enter on one file or link, and the menu item. */
    public void editing(Editing editing) { this.editing=editing;openInEditor.setVisible(true);editingSeparator.setVisible(true);selectionChanged(); }
    public FileTable table() { return table; }
    public List<FileEntry> selection() { var result=new ArrayList<FileEntry>();for(int row:table.getSelectedRows()) if(row>=0 && row<entries.size()) result.add(entries.get(row));return List.copyOf(result); }
    public String directory() { return path.getText(); }
    public boolean following() { return follow.isSelected(); }
    public void following(boolean value) { follow.setSelected(value); }
    public void destinationMode() { destination=true;followFooter.setVisible(false);showBrowser();for(String name:List.of("Download selected","Upload files","Delete selected","Copy paths")) buttons.get(name).getParent().remove(buttons.get(name));table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setComponentPopupMenu(null); }
    public void showPage(String hostname,String directory,List<FileEntry> values,long offset,long total,boolean focus) {
        this.entries=List.copyOf(values);this.offset=offset;this.total=total;loaded=true;showBrowser();host.setText(hostname);path.setText(directory);model.fireTableDataChanged();
        pageLabel.setText(total==0?"Empty folder":(offset+1)+"–"+(offset+values.size())+" of "+total);previous.setEnabled(offset>0);next.setEnabled(offset+values.size()<total);
        if(!entries.isEmpty()) table.setRowSelectionInterval(0,0);selectionChanged();buttons.get("Up").setEnabled(!directory.equals("/"));busy(false,"");onLoaded.run();if(focus) table.requestFocusInWindow();
    }
    public void busy(boolean busy,String text) { chooseSession.setEnabled(!busy);status.setVisible(busy || !text.isEmpty());cancel.setVisible(busy);table.setEnabled(!busy);path.setEnabled(!busy);buttons.get("Up").setEnabled(!busy && loaded && !path.getText().equals("/"));previous.setEnabled(!busy && offset>0);next.setEnabled(!busy && offset+entries.size()<total);message.setText(text);message.setToolTipText(text);revalidate();repaint(); }
    public void error(String text) { busy(false,text); }
    /** Empties the view: no host, folder or rows, and nothing for file actions to act on. */
    public void clear(String text) {
        entries=List.of();offset=0;total=0;loaded=false;host.setText("Select an SSH host to browse files");path.setText("");model.fireTableDataChanged();pageLabel.setText("");
        selectionChanged();showBrowser();busy(false,text);
    }
    public void operationStatus(boolean running,String text) {
        operationResult.setText(running?"":text);operationResult.setToolTipText(running?null:text);operationResult.setVisible(!running && !text.isEmpty());
        busy(running,running?text:"");
    }
    private void showBrowser() {
        cards.show(content,loaded || destination?"connected":"disconnected");paging.setVisible(loaded || destination);
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
                g.setColor(!follow.isEnabled()?palette.surfaceSunken():follow.isSelected()?palette.accent():palette.border());
                g.fillRoundRect(2,2,30,16,16,16);
                g.setColor(follow.isEnabled()?palette.selectionText():palette.textMuted());g.fillOval(follow.isSelected()?18:4,4,12,12);
                if(follow.hasFocus()) { g.setColor(palette.accent());g.drawRoundRect(0,0,33,19,19,19); }
            }finally { g.dispose(); }
        }
    }
    String operationMessage() { return operationResult.getText(); }
    String hostText() { return host.getText(); }
    String messageText() { return message.getText(); }
    public void enableBrowsing(boolean enabled) { path.setEnabled(enabled);follow.setEnabled(enabled);followLabel.setEnabled(enabled);buttons.get("Refresh").setEnabled(enabled); }
    private void activate() {
        if(actions==null) return;var selected=selection();if(selected.size()!=1) return;var entry=selected.getFirst();
        if(entry.kind()==FileEntry.Kind.DIRECTORY || entry.kind()==FileEntry.Kind.LINK && editing==null) actions.navigate().accept(entry.name());
        else if(editing!=null && (entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK)) editing.activate().accept(entry);
    }
    private void selectionChanged() {
        var selection=selection();boolean selected=!selection.isEmpty();for(String name:List.of("Download selected","Delete selected","Copy paths")) buttons.get(name).setEnabled(selected);buttons.get("Upload files").setEnabled(loaded);buttons.get("New folder").setEnabled(loaded);
        openInEditor.setEnabled(selection.stream().anyMatch(entry->entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK));
    }
    private void columns() {
        boolean nextDetails=getWidth()>=460;if(nextDetails==details) return;details=nextDetails;
        if(details) { table.addColumn(sizeColumn);table.addColumn(modifiedColumn); }else { table.removeColumn(sizeColumn);table.removeColumn(modifiedColumn); }
    }
    public final class FileTable extends JTable {
        @Override public void setFont(Font font) { super.setFont(font);updateRowHeight(); }
        @Override public JToolTip createToolTip() { var tip=super.createToolTip();tip.putClientProperty("html.disable",true);return tip; }
        @Override public void updateUI() { super.updateUI();updateRowHeight(); }
        public void updateRowHeight() { if(getFont()!=null) setRowHeight(Math.max(20,getFontMetrics(getFont()).getHeight()+4)); }
        @Override public String getToolTipText(MouseEvent event) { int row=rowAtPoint(event.getPoint());if(row<0 || row>=entries.size()) return null;var file=entries.get(row);return file.name()+" — "+formatSize(file)+" — "+modified(file); }
    }
    private final class Rows extends AbstractTableModel {
        public int getRowCount() { return entries.size(); }public int getColumnCount() { return 3; }
        public String getColumnName(int column) { return switch(column) { case 0->"Name";case 1->"Size";default->"Modified"; }; }
        public Object getValueAt(int row,int column) { var file=entries.get(row);return switch(column) { case 0->file.name();case 1->formatSize(file);default->modified(file); }; }
    }
    private final class Renderer extends DefaultTableCellRenderer {
        Renderer() { putClientProperty("html.disable",true);setBorder(BorderFactory.createEmptyBorder(0,3,0,3)); }
        @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int column) {
            super.getTableCellRendererComponent(table,value,selected,focus,row,column);setIcon(null);
            if(table.convertColumnIndexToModel(column)==0 && row<entries.size()) setIcon(icons.apply(switch(entries.get(row).kind()) { case DIRECTORY->IconName.FOLDER;case LINK->IconName.LINK;default->IconName.FILE; }));return this;
        }
    }
    private static String formatSize(FileEntry entry) { if(entry.kind()==FileEntry.Kind.DIRECTORY) return "Folder";long value=entry.size();if(value<0) return "Unknown";if(value<1024) return value+" B";if(value<1024*1024) return String.format(Locale.ROOT,"%.1f KiB",value/1024.0);if(value<1024L*1024*1024) return String.format(Locale.ROOT,"%.1f MiB",value/(1024.0*1024));return String.format(Locale.ROOT,"%.1f GiB",value/(1024.0*1024*1024)); }
    private static String modified(FileEntry entry) { return entry.modifiedMillis()<0?"":DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.modifiedMillis())); }
}
