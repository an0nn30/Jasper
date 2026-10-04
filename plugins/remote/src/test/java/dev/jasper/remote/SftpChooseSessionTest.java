package dev.jasper.remote;

import dev.jasper.remote.ui.sftp.SftpUi;
import dev.jasper.sdk.testing.FakePluginHost;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SftpChooseSessionTest {
    @Test void choosingSessionFromDisconnectedSidebarOpensTheExistingHostChooser(@TempDir Path root) throws Exception {
        try(var host=new FakePluginHost(root.resolve("home"))) {
            var plugin=new TestRemotePlugin(Runnable::run,context->Optional.empty(),(delay,work)->()->{},root.resolve("ssh"));
            host.start(RemotePluginTest.INFO,Set.of(),Set.of(),plugin);
            var window=host.addTerminalWindow();
            var sidebar=host.openPanel(SftpUi.PANEL,window);
            assertThat(host.windows()).isEmpty();
            SwingUtilities.invokeAndWait(()->button(sidebar,"Choose Session").doClick());
            var chooser=host.windowContent("dev.jasper.remote","Browse files on SSH host").orElseThrow();
            assertThat(find(chooser,JComboBox.class)).isPresent();
            assertThat(button(chooser,"Browse files").isEnabled()).as("no saved hosts").isFalse();
            assertThat(host.failures()).isEmpty();
        }
    }
    private static JButton button(Component component,String name) {
        if(component instanceof JButton button && (name.equals(button.getText()) || name.equals(button.getAccessibleContext().getAccessibleName()))) return button;
        if(component instanceof Container container) for(var child:container.getComponents()) {
            try { return button(child,name); }catch(IllegalArgumentException missing) { }
        }
        throw new IllegalArgumentException("Missing button "+name);
    }
    private static <T> Optional<T> find(Component component,Class<T> type) {
        if(type.isInstance(component)) return Optional.of(type.cast(component));
        if(component instanceof Container container) for(var child:container.getComponents()) { var found=find(child,type);if(found.isPresent()) return found; }
        return Optional.empty();
    }
}
