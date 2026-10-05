package dev.jasper.sdk.ui;
import dev.jasper.sdk.Subscription;
import java.util.function.Consumer;
/** Plugin-owned declarative UI factory. Creation and registration require EDT. */
public interface UiKit {
 /** Create an owned view.
 * @param spec immutable tree
 * @param events contained event handler
 * @return owned view */
 UiView create(ViewSpec spec, Consumer<UiEvent> events);
 /** Register monochrome path artwork in this plugin's namespace.
 * @param asset artwork
 * @return owned registration */
 Subscription registerIcon(IconAsset asset);
}
