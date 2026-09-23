package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.sdk.ui.IconName;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;
import javax.swing.*;

/** One destination browser and a fixed source summary; never a second permanent explorer. */
public final class DestinationPanel extends JPanel implements AutoCloseable {
    private final JComboBox<RemoteHost> hosts;
    private final SftpPanel browser;
    private final SftpController controller;
    private final JButton copy=new JButton("Copy here");
    private CompletableFuture<ConnectionIdentity> resolving;
    private long generation;
    private boolean closed;
    public DestinationPanel(String sourceSummary,List<RemoteHost> saved,Path cache,Executor background,Executor ui,
                            Function<IconName,Icon> icons,Function<UUID,CompletableFuture<ConnectionIdentity>> resolve,
                            Function<ConnectionIdentity,CompletableFuture<FileEndpoint>> open,Consumer<SftpController.Capture> choose,
                            Consumer<SftpController.Capture> newFolder,Runnable cancelOperation,Runnable cancel) {
        super(new BorderLayout(0,6));setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        var summary=new JLabel(sourceSummary);summary.putClientProperty("html.disable",true);
        hosts=new JComboBox<>(saved.toArray(RemoteHost[]::new));hosts.setSelectedIndex(-1);hosts.getAccessibleContext().setAccessibleName("Destination host");
        hosts.setRenderer(new DefaultListCellRenderer() { @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus) { super.getListCellRendererComponent(list,value,index,selected,focus);putClientProperty("html.disable",true);setText(value instanceof RemoteHost host?host.name()+" — "+host.hostname():"Choose a destination host…");return this; } });
        var north=new JPanel(new BorderLayout(4,6));north.add(summary,BorderLayout.NORTH);north.add(hosts,BorderLayout.SOUTH);add(north,BorderLayout.NORTH);
        browser=new SftpPanel(icons);browser.destinationMode();controller=new SftpController(cache,background,ui,open,browser);controller.foldersOnly(true);
        Runnable nothing=()->{};controller.operations(new SftpController.Operations(nothing,nothing,nothing,()->controller.capture().ifPresent(newFolder),nothing,nothing,nothing,cancelOperation));
        browser.onLoaded(()->copy.setEnabled(controller.capture().filter(c->hosts.getSelectedItem() instanceof RemoteHost host && host.id().equals(c.identity().host().id())).isPresent()));add(browser,BorderLayout.CENTER);
        var buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));var close=new JButton("Cancel");close.addActionListener(event->cancel.run());buttons.add(close);buttons.add(copy);copy.setEnabled(false);add(buttons,BorderLayout.SOUTH);
        hosts.addActionListener(event-> {
            if(closed) return;long token=++generation;copy.setEnabled(false);controller.cancel();if(resolving!=null) resolving.cancel(true);
            if(!(hosts.getSelectedItem() instanceof RemoteHost host)) return;
            browser.busy(true,"Connecting…");resolving=resolve.apply(host.id());resolving.whenComplete((identity,failure)->ui.execute(()-> {
                if(closed || token!=generation) return;
                if(failure!=null) browser.error("Cannot connect: "+String.valueOf(failure.getMessage()));else controller.open(host.id(),identity,"",true);
            }));
        });
        copy.addActionListener(event->controller.capture().filter(c->hosts.getSelectedItem() instanceof RemoteHost host && host.id().equals(c.identity().host().id())).ifPresent(choose));
        setPreferredSize(new Dimension(500,450));
    }
    public void refresh() { controller.refresh(); }
    public void status(boolean busy,String text) { if(!closed) browser.busy(busy,text); }
    @Override public void close() { if(closed) return;closed=true;generation++;if(resolving!=null) resolving.cancel(true);controller.close(); }
}
