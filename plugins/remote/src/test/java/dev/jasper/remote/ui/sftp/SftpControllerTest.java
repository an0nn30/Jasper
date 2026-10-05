package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.*;
import dev.jasper.remote.sftp.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SftpControllerTest {
    @TempDir Path root;
    static ConnectionIdentity host(String name) { var host=RemoteHost.create(name,"example.invalid",22,"user",Auth.AGENT,"",Optional.empty());return new ConnectionIdentity(List.of(new ConnectionIdentity.Hop(host,"user"))); }
    /** The browser speaks SFTP's POSIX paths; LocalEndpoint stands in for SFTP only where local paths are POSIX too. */
    @Test @DisabledOnOs(OS.WINDOWS) void obsoleteListingCannotReplaceNewHostAndManualNavigationDisablesOnlyThatPanesFollow() throws Exception {
        root=root.toRealPath();Path a=Files.createDirectory(root.resolve("a")),b=Files.createDirectory(root.resolve("b"));Files.writeString(a.resolve("old"),"a");Files.writeString(b.resolve("new"),"b");Files.createDirectory(b.resolve("child"));
        var first=new CompletableFuture<FileEndpoint>();var second=new CompletableFuture<FileEndpoint>();var identityA=host("A");var identityB=host("B");
        var holder=new SftpController[1];var panel=new SftpPanel[1];var paneA=UUID.randomUUID();var paneB=UUID.randomUUID();
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            SwingUtilities.invokeAndWait(()-> { panel[0]=new SftpPanel(icon->new ImageIcon(new java.awt.image.BufferedImage(16,16,2)));holder[0]=new SftpController(root,workers,SwingUtilities::invokeLater,identity->identity.equals(identityA)?first:second,panel[0]);holder[0].open(paneA,identityA,a.toString(),true);holder[0].open(paneB,identityB,b.toString(),true); });
            try {
            second.complete(new LocalEndpoint());awaitRows(panel[0],"child","new");first.complete(new LocalEndpoint());
            SwingUtilities.invokeAndWait(()-> { assertThat(holder[0].capture().orElseThrow().identity()).isEqualTo(identityB);holder[0].manual("child");assertThat(panel[0].following()).isFalse();holder[0].follow(paneA,a.toString());assertThat(holder[0].capture().orElseThrow().identity()).isEqualTo(identityB);holder[0].visible(false);holder[0].close(); });
            } finally { SwingUtilities.invokeAndWait(holder[0]::close);holder[0].stopped().get(5,TimeUnit.SECONDS); }
        }
    }

    /** The browser speaks SFTP's POSIX paths; LocalEndpoint stands in for SFTP only where local paths are POSIX too. */
    @Test @DisabledOnOs(OS.WINDOWS) void explicitDirectoryLinkNavigationShowsResolvedPathAndDoesNotChangeNoFollowOperations() throws Exception {
        root=root.toRealPath();Path real=Files.createDirectory(root.resolve("real"));Files.writeString(real.resolve("inside"),"payload");Files.createSymbolicLink(root.resolve("alias"),Path.of("real"));
        var holder=new SftpController[1];var panel=new SftpPanel[1];
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            SwingUtilities.invokeAndWait(()-> { panel[0]=new SftpPanel(icon->new ImageIcon(new java.awt.image.BufferedImage(16,16,2)));holder[0]=new SftpController(root.resolve("cache"),workers,SwingUtilities::invokeLater,identity->CompletableFuture.completedFuture(new LocalEndpoint()),panel[0]);holder[0].open(UUID.randomUUID(),host("links"),root.toString(),true); });
            try {
                awaitRows(panel[0],"cache","real","alias");
                SwingUtilities.invokeAndWait(()-> {panel[0].table().setRowSelectionInterval(2,2);panel[0].table().getActionMap().get("browse").actionPerformed(new java.awt.event.ActionEvent(panel[0],0,"browse"));});
                awaitRows(panel[0],"inside");
                SwingUtilities.invokeAndWait(()->assertThat(panel[0].directory()).isEqualTo(real.toString()));
                try(var endpoint=new LocalEndpoint()) { assertThatThrownBy(()->endpoint.list(root.resolve("alias").toString(),entry->{})).isInstanceOf(java.io.IOException.class); }
            } finally { SwingUtilities.invokeAndWait(holder[0]::close);holder[0].stopped().get(5,TimeUnit.SECONDS); }
        }
    }
    @Test void reportsWhichPaneItFollowsAndAsksForItsFolder() throws Exception {
        var identity=host("A");var pane=UUID.randomUUID();var other=UUID.randomUUID();var requests=new ArrayList<UUID>();var holder=new SftpController[1];
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            SwingUtilities.invokeAndWait(()-> {
                var panel=new SftpPanel(icon->new ImageIcon(new java.awt.image.BufferedImage(16,16,2)));
                holder[0]=new SftpController(root,workers,SwingUtilities::invokeLater,id->new CompletableFuture<>(),panel);
                holder[0].onFollowRequested(requests::add);
                holder[0].open(pane,identity,"",true);
                assertThat(holder[0].following(pane)).isTrue();
                assertThat(holder[0].following(other)).isFalse();
                assertThat(requests).containsExactly(pane);
                holder[0].manual("/tmp");
                assertThat(holder[0].following(pane)).as("manual navigation stops following").isFalse();
                holder[0].following(true);
                assertThat(requests).containsExactly(pane,pane);
                holder[0].visible(false);
                assertThat(holder[0].following(pane)).as("hidden").isFalse();
                holder[0].visible(true);
                assertThat(requests).containsExactly(pane,pane,pane);
                holder[0].notice(pane,DirectoryFollower.UNSUPPORTED);
                holder[0].close();
                assertThat(holder[0].following(pane)).isFalse();
            });
        }
    }

    /** The browser speaks SFTP's POSIX paths; LocalEndpoint stands in for SFTP only where local paths are POSIX too. */
    @Test @DisabledOnOs(OS.WINDOWS) void endingTheShownPanesSessionClearsTheView() throws Exception {
        root=root.toRealPath();Path a=Files.createDirectory(root.resolve("a"));Files.writeString(a.resolve("file"),"x");
        var identity=host("A");var pane=UUID.randomUUID();var other=UUID.randomUUID();var holder=new SftpController[1];var panel=new SftpPanel[1];
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            SwingUtilities.invokeAndWait(()-> { panel[0]=new SftpPanel(icon->new ImageIcon(new java.awt.image.BufferedImage(16,16,2)));holder[0]=new SftpController(root,workers,SwingUtilities::invokeLater,id->CompletableFuture.completedFuture(new LocalEndpoint()),panel[0]);holder[0].open(pane,identity,a.toString(),true); });
            try {
            awaitRows(panel[0],"file");
            SwingUtilities.invokeAndWait(()-> {
                holder[0].ended(other);
                assertThat(panel[0].table().getRowCount()).as("another pane's session leaves this view").isEqualTo(1);
                assertThat(holder[0].capture()).isPresent();
                holder[0].ended(pane);
                assertThat(panel[0].table().getRowCount()).isZero();
                assertThat(panel[0].directory()).isEmpty();
                assertThat(panel[0].hostText()).isEqualTo("Select an SSH host to browse files");
                assertThat(panel[0].messageText()).isEqualTo("SSH session closed");
                assertThat(holder[0].capture()).as("no captured folder for uploads or new folders").isEmpty();
                assertThat(holder[0].following(pane)).isFalse();
                holder[0].refresh();
                assertThat(panel[0].messageText()).as("nothing left to refresh").isEqualTo("SSH session closed");
            });
            } finally { SwingUtilities.invokeAndWait(()->holder[0].close()); }
        }
    }
    @Test @DisabledOnOs(OS.WINDOWS) void successfulNavigationBuildsHistoryAndNewNavigationTruncatesForwardHistory() throws Exception {
        root=root.toRealPath();Path home=Files.createDirectory(root.resolve("home"));
        Path child=Files.createDirectory(home.resolve("child"));Files.writeString(child.resolve("child-file"),"x");
        Path other=Files.createDirectory(home.resolve("other"));Files.writeString(other.resolve("other-file"),"x");
        var holder=new SftpController[1];var panel=new SftpPanel[1];
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            SwingUtilities.invokeAndWait(()-> {
                panel[0]=new SftpPanel(dev.jasper.sdk.testing.FakeNamedIcon::new);
                holder[0]=new SftpController(root.resolve("cache"),workers,SwingUtilities::invokeLater,id->CompletableFuture.completedFuture(new LocalEndpoint()),panel[0]);
                holder[0].open(UUID.randomUUID(),host("A"),home.toString(),true);
            });
            try {
                awaitRows(panel[0],"child","other");
                SwingUtilities.invokeAndWait(()->holder[0].manual("child"));awaitRows(panel[0],"child-file");
                SwingUtilities.invokeAndWait(()-> { assertThat(SftpPanelTest.named(panel[0],"Back").isEnabled()).isTrue();holder[0].back(); });
                awaitRows(panel[0],"child","other");
                SwingUtilities.invokeAndWait(()-> { assertThat(panel[0].following()).isFalse();assertThat(SftpPanelTest.named(panel[0],"Forward").isEnabled()).isTrue();holder[0].forward(); });
                awaitRows(panel[0],"child-file");
                SwingUtilities.invokeAndWait(holder[0]::back);awaitRows(panel[0],"child","other");
                SwingUtilities.invokeAndWait(()->holder[0].manual("missing"));awaitMessage(panel[0],"missing");
                SwingUtilities.invokeAndWait(()-> { assertThat(SftpPanelTest.named(panel[0],"Forward").isEnabled()).as("failed navigation preserves forward history").isTrue();holder[0].manual("other"); });
                awaitRows(panel[0],"other-file");
                SwingUtilities.invokeAndWait(()-> { assertThat(SftpPanelTest.named(panel[0],"Forward").isEnabled()).isFalse();holder[0].back(); });
                awaitRows(panel[0],"child","other");
            } finally { SwingUtilities.invokeAndWait(holder[0]::close);holder[0].stopped().get(5,TimeUnit.SECONDS); }
        }
    }

    @Test @DisabledOnOs(OS.WINDOWS) void switchingConnectionsClearsStaleRowsAndNeverReusesAnotherHostsHistory() throws Exception {
        root=root.toRealPath();Path a=Files.createDirectory(root.resolve("a"));Path child=Files.createDirectory(a.resolve("child"));Files.writeString(child.resolve("a-file"),"x");
        Path b=Files.createDirectory(root.resolve("b"));Files.writeString(b.resolve("b-file"),"x");
        var identityA=host("A");var identityB=host("B");var paneA=UUID.randomUUID();var paneB=UUID.randomUUID();
        var pending=new CompletableFuture<FileEndpoint>();var firstB=new java.util.concurrent.atomic.AtomicBoolean(true);var holder=new SftpController[1];var panel=new SftpPanel[1];
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            SwingUtilities.invokeAndWait(()-> {
                panel[0]=new SftpPanel(dev.jasper.sdk.testing.FakeNamedIcon::new);
                holder[0]=new SftpController(root.resolve("cache"),workers,SwingUtilities::invokeLater,id->id.equals(identityB) && firstB.getAndSet(false)?pending:CompletableFuture.completedFuture(new LocalEndpoint()),panel[0]);
                holder[0].open(paneA,identityA,a.toString(),true);
            });
            try {
                awaitRows(panel[0],"child");SwingUtilities.invokeAndWait(()->holder[0].manual("child"));awaitRows(panel[0],"a-file");
                SwingUtilities.invokeAndWait(()-> {
                    holder[0].open(paneB,identityB,b.toString(),true);
                    assertThat(holder[0].capture()).isEmpty();assertThat(panel[0].table().getRowCount()).isZero();
                    assertThat(SftpPanelTest.named(panel[0],"Back").isEnabled()).isFalse();holder[0].back();
                });
                pending.complete(new LocalEndpoint());awaitRows(panel[0],"b-file");
                SwingUtilities.invokeAndWait(()-> { assertThat(SftpPanelTest.named(panel[0],"Back").isEnabled()).isFalse();holder[0].open(paneA,identityA,a.toString(),true); });
                awaitRows(panel[0],"a-file");SwingUtilities.invokeAndWait(holder[0]::back);awaitRows(panel[0],"child");
                SwingUtilities.invokeAndWait(()->holder[0].open(paneA,identityB,b.toString(),true));awaitRows(panel[0],"b-file");
                SwingUtilities.invokeAndWait(()->assertThat(SftpPanelTest.named(panel[0],"Back").isEnabled()).isFalse());
            } finally { SwingUtilities.invokeAndWait(holder[0]::close);holder[0].stopped().get(5,TimeUnit.SECONDS); }
        }
    }
    private static void awaitMessage(SftpPanel panel,String part) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) {
            var message=new String[1];SwingUtilities.invokeAndWait(()->message[0]=panel.messageText());
            if(message[0].contains(part)) return;Thread.sleep(10);
        }
        throw new AssertionError("Expected status containing "+part);
    }
    static void awaitRows(SftpPanel panel,String... names) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) { var rows=new ArrayList<String>();SwingUtilities.invokeAndWait(()-> { for(int i=0;i<panel.table().getRowCount();i++) rows.add(panel.table().getValueAt(i,0).toString()); });if(rows.equals(List.of(names))) return;Thread.sleep(10); }
        var shown=new String[1];SwingUtilities.invokeAndWait(()->shown[0]=panel.messageText());throw new AssertionError("Directory rows did not arrive: "+shown[0]);
    }
}
