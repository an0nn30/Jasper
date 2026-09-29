package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.sdk.ui.IconName;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;
import javax.swing.*;

/**
 * Open Remote File: a host, a browsable listing of files and folders, and Open. What opening does is the caller's.
 * Choosing a host only selects it; Connect (or Enter in the host dropdown) signs in. Usable without a mouse: focus
 * starts in the host dropdown, Tab moves through every control, Enter presses a focused button and Esc cancels.
 */
public final class RemoteFilePicker extends JPanel implements AutoCloseable {
    private final JComboBox<RemoteHost> hosts;
    private final SftpPanel browser;
    private final SftpController controller;
    private final JButton connect=new JButton("Connect"),open=new JButton("Open"),cancel=new JButton("Cancel");
    private final Executor ui;
    private final Function<UUID,CompletableFuture<ConnectionIdentity>> resolve;
    private CompletableFuture<ConnectionIdentity> resolving;
    private long generation;
    private boolean closed;
    /**
     * @param saved the saved hosts, in the dropdown's order
     * @param cache the listing cache folder
     * @param background where listings run
     * @param ui the UI thread
     * @param icons the host's icons
     * @param resolve signs in to a chosen host
     * @param endpoints opens a file endpoint on a connection
     * @param activate double-click or Enter on one file or link
     * @param openSelected Open on the selection
     * @param cancelled Cancel or Esc
     */
    public RemoteFilePicker(List<RemoteHost> saved,Path cache,Executor background,Executor ui,Function<IconName,Icon> icons,
                            Function<UUID,CompletableFuture<ConnectionIdentity>> resolve,Function<ConnectionIdentity,CompletableFuture<FileEndpoint>> endpoints,
                            BiConsumer<SftpController.Capture,FileEntry> activate,Consumer<SftpController.Capture> openSelected,Runnable cancelled) {
        super(new BorderLayout(0,6));this.ui=ui;this.resolve=resolve;setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        hosts=new JComboBox<>(saved.toArray(RemoteHost[]::new));hosts.setSelectedIndex(-1);hosts.getAccessibleContext().setAccessibleName("Host");
        hosts.setRenderer(new DefaultListCellRenderer() { @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus) { super.getListCellRendererComponent(list,value,index,selected,focus);putClientProperty("html.disable",true);setText(value instanceof RemoteHost host?host.name()+" — "+host.hostname():"Choose a host…");return this; } });
        var north=new JPanel(new BorderLayout(6,0));north.add(hosts,BorderLayout.CENTER);north.add(connect,BorderLayout.EAST);add(north,BorderLayout.NORTH);
        browser=new SftpPanel(icons);browser.pickMode();controller=new SftpController(cache,background,ui,endpoints,browser);
        browser.editing(new SftpPanel.Editing(entry->controller.capture().ifPresent(capture->activate.accept(capture,entry)),()->controller.capture().ifPresent(openSelected)));
        browser.table().getSelectionModel().addListSelectionListener(event->openEnabled());browser.onLoaded(this::openEnabled);add(browser,BorderLayout.CENTER);
        var buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));cancel.addActionListener(event->cancelled.run());buttons.add(cancel);buttons.add(open);open.setEnabled(false);
        open.addActionListener(event->controller.capture().ifPresent(openSelected));add(buttons,BorderLayout.SOUTH);
        connect.setEnabled(false);connect.addActionListener(event->{ if(!closed && hosts.getSelectedItem() instanceof RemoteHost host) choose(host); });
        hosts.addActionListener(event->connect.setEnabled(!closed && hosts.getSelectedItem() instanceof RemoteHost));
        bind(hosts,JComponent.WHEN_FOCUSED,KeyEvent.VK_ENTER,"connect",()->{ if(hosts.isPopupVisible()) hosts.setPopupVisible(false);else connect.doClick(); });
        for(JButton button:List.of(connect,cancel,open)) bind(button,JComponent.WHEN_FOCUSED,KeyEvent.VK_ENTER,"press",button::doClick);
        bind(this,JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,KeyEvent.VK_ESCAPE,"cancel-picker",cancelled);
        setFocusTraversalPolicyProvider(true);
        setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() { @Override public Component getDefaultComponent(Container root) { return hosts; } });
        setPreferredSize(new Dimension(620,480));
    }
    private static void bind(JComponent component,int condition,int key,String name,Runnable action) {
        component.getInputMap(condition).put(KeyStroke.getKeyStroke(key,0),name);
        component.getActionMap().put(name,new AbstractAction() { @Override public void actionPerformed(ActionEvent event) { action.run(); } });
    }
    /**
     * Starts on {@code identity} at {@code directory} (the home folder when blank), selecting its host without signing
     * in again. The listing loads without taking focus from the host dropdown.
     */
    public void start(ConnectionIdentity identity,String directory) {
        if(closed) return;generation++;if(resolving!=null) resolving.cancel(true);
        for(int i=0;i<hosts.getItemCount();i++) if(hosts.getItemAt(i).id().equals(identity.host().id())) { hosts.setSelectedIndex(i);break; }
        controller.open(identity.host().id(),identity,directory==null?"":directory,false);
    }
    /** Enters {@code path}, absolute or relative to the folder shown. */
    public void navigate(String path) { if(!closed) controller.manual(path); }
    /** Shows {@code text} in the listing's status line. */
    public void error(String text) { if(!closed) browser.error(text); }
    /** @return the connection, folder and selection shown, once a folder has loaded */
    public Optional<SftpController.Capture> capture() { return controller.capture(); }
    boolean closed() { return closed; }
    /** @return the host dropdown */
    public JComboBox<RemoteHost> hosts() { return hosts; }
    /** @return the listing */
    public SftpPanel browser() { return browser; }
    /** @return the Connect button */
    public JButton connectButton() { return connect; }
    /** @return the Open button */
    public JButton openButton() { return open; }
    /** @return the Cancel button */
    public JButton cancelButton() { return cancel; }
    /** Connect: signs in to {@code host} and lists its home folder, which then takes focus. */
    private void choose(RemoteHost host) {
        long token=++generation;open.setEnabled(false);controller.cancel();if(resolving!=null) resolving.cancel(true);
        browser.busy(true,"Connecting…");resolving=resolve.apply(host.id());resolving.whenComplete((identity,failure)->ui.execute(()-> {
            if(closed || token!=generation) return;
            if(failure!=null) browser.error("Cannot connect: "+EditorOpening.message(failure));else controller.open(host.id(),identity,"",true);
        }));
    }
    private void openEnabled() { open.setEnabled(!closed && controller.capture().map(capture->capture.selection().stream().anyMatch(entry->entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK)).orElse(false)); }
    @Override public void close() { if(closed) return;closed=true;generation++;if(resolving!=null) resolving.cancel(true);controller.close(); }
}
