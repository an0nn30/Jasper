package dev.jasper.app.platform;

import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.icons.IconRegistration;
import dev.jasper.ui.swing.KitContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.swing.Icon;

/** Receiving-view lifetime for immutable presentation artwork. No plugin owner, resource loader or paint callback
 * crosses into the kit registry. The receiver resets this registry when replacing its model and closes it with its view.
 */
public final class PresentationIcons implements AutoCloseable {
    private final IconRegistry registry = new IconRegistry();
    private final Map<String, IconRegistration> registrations = new HashMap<>();
    public KitContext context(KitContext source) {
        return new KitContext(source.binding(), source.route(), source.metrics(), registry, source.animationsEnabled());
    }
    public Optional<String> name(Icon icon) {
        if (!(icon instanceof PresentationIcon named)) return AppIcons.kitName(icon);
        if (named.pathData() != null) registrations.computeIfAbsent(named.name(), ignored -> registry.register(named.name(), named.pathData()));
        return Optional.of(named.name());
    }
    public void reset() { registrations.values().forEach(IconRegistration::close); registrations.clear(); }
    @Override public void close() { reset(); registry.close(); }
}
