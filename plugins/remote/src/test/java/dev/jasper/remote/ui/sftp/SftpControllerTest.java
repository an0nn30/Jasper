package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.*;
import dev.jasper.remote.sftp.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SftpControllerTest {
    @TempDir Path root;
    static ConnectionIdentity host(String name) { var host=RemoteHost.create(name,"example.invalid",22,"user",Auth.AGENT,"",Optional.empty());return new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(host,"user"))); }
    @Test void obsoleteListingCannotReplaceNewHostAndManualNavigationDisablesOnlyThatPanesFollow() throws Exception {
        root=root.toRealPath();Path a=Files.createDirectory(root.resolve("a")),b=Files.createDirectory(root.resolve("b"));Files.writeString(a.resolve("old"),"a");Files.writeString(b.resolve("new"),"b");Files.createDirectory(b.resolve("child"));
        var first=new CompletableFuture<FileEndpoint>();var second=new CompletableFuture<FileEndpoint>();var identityA=host("A");var identityB=host("B");
        var holder=new SftpController[1];var panel=new SftpPanel[1];var paneA=UUID.randomUUID();var paneB=UUID.randomUUID();
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            SwingUtilities.invokeAndWait(()-> { panel[0]=new SftpPanel(icon->new ImageIcon(new java.awt.image.BufferedImage(16,16,2)));holder[0]=new SftpController(root,workers,SwingUtilities::invokeLater,identity->identity.equals(identityA)?first:second,panel[0]);holder[0].open(paneA,identityA,a.toString(),true);holder[0].open(paneB,identityB,b.toString(),true); });
            second.complete(new LocalEndpoint());awaitRows(panel[0],"child","new");first.complete(new LocalEndpoint());
            SwingUtilities.invokeAndWait(()-> { assertThat(holder[0].capture().orElseThrow().identity()).isEqualTo(identityB);holder[0].manual("child");assertThat(panel[0].following()).isFalse();holder[0].follow(paneA,a.toString());assertThat(holder[0].capture().orElseThrow().identity()).isEqualTo(identityB);holder[0].visible(false);holder[0].close(); });
            holder[0].stopped().get(5,TimeUnit.SECONDS);
        }
    }
    static void awaitRows(SftpPanel panel,String... names) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) { var rows=new ArrayList<String>();SwingUtilities.invokeAndWait(()-> { for(int i=0;i<panel.table().getRowCount();i++) rows.add(panel.table().getValueAt(i,0).toString()); });if(rows.equals(List.of(names))) return;Thread.sleep(10); }
        throw new AssertionError("Directory rows did not arrive");
    }
}
