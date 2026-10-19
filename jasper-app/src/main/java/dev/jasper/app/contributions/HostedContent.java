package dev.jasper.app.contributions;

import java.util.Objects;
import javax.swing.JComponent;

/**
 * A plugin's view as a tab's content (workspace tabs spec 9.2). The plugin releases its view through the tab's
 * {@link Tab#onClosed(Runnable)}, so disposing it does nothing more.
 *
 * @param component the plugin's mounted view
 */
public record HostedContent(JComponent component) implements TabContent {
    /** Requires the component. */
    public HostedContent { Objects.requireNonNull(component, "component"); }

    @Override public void dispose() { }
}
