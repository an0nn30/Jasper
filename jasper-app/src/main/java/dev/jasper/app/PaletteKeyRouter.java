package dev.jasper.app;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.KeyStroke;
import javax.swing.text.JTextComponent;

/** Owns palette key sequences, including their tails after execution changes focus. */
final class PaletteKeyRouter implements AutoCloseable {
    private final WindowCommandPalette palette;
    private final Supplier<KeyBindings> bindings;
    private final boolean macOs;
    private final Predicate<Component> belongsToOwner;
    private final Set<Integer> swallowed = new HashSet<>();
    private boolean closed;
    private boolean swallowTyped;

    PaletteKeyRouter(WindowCommandPalette palette, Supplier<KeyBindings> bindings, boolean macOs,
                     Predicate<Component> belongsToOwner) {
        this.palette = palette; this.bindings = bindings; this.macOs = macOs; this.belongsToOwner = belongsToOwner;
    }

    boolean dispatch(KeyEvent event) {
        if (dispatchTail(event)) return true;
        if (event.getID() != KeyEvent.KEY_PRESSED || closed || !belongsToOwner.test(event.getComponent())) return false;
        boolean consumed = route(KeyStroke.getKeyStrokeForEvent(event), event.getComponent(), swallowed::add);
        swallowTyped = consumed;
        if (consumed) event.consume();
        return consumed;
    }

    /** Callback-free ownership check, safe even after the command disposed its window. */
    boolean dispatchTail(KeyEvent event) {
        int type = event.getID(), code = event.getKeyCode();
        boolean consume;
        if (type == KeyEvent.KEY_RELEASED) consume = swallowed.remove(code);
        else if (type == KeyEvent.KEY_TYPED) consume = swallowTyped && !swallowed.isEmpty();
        else if (type == KeyEvent.KEY_PRESSED) {
            consume = swallowed.contains(code);
            // Typed events have no key code. The latest press determines provenance,
            // including fresh foreign/editor input while an older palette key is held.
            swallowTyped = consume;
        } else return false;
        if (consume) event.consume();
        return consume;
    }

    /** Root-map fallback shares the policy, but has no physical key sequence to retain. */
    boolean dispatchShortcut(KeyStroke stroke, Component source) { return route(stroke, source, code -> {}); }

    private boolean route(KeyStroke stroke, Component source, IntConsumer claim) {
        if (closed) return false;
        int code = stroke.getKeyCode();
        var action = bindings.get().actionFor(stroke);
        String scope = scopeFor(action.orElse(null));
        if (scope != null) {
            // An unregistered scope's shortcut is inert: it does not hold the physical key,
            // so it cannot swallow a later, differently-modified press of the same key.
            if (palette.hasScope(scope)) claim.accept(code);
            palette.open(scope);
            return true;
        }
        if (!palette.isOpen()) return false;
        // KeyStroke carries legacy bits as well as extended modifiers.
        int modifiers = stroke.getModifiers() & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
            | InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK);
        int primary = macOs ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
        if (palette.composing() && modifiers == 0 && code != KeyEvent.VK_ESCAPE) return false;
        boolean numbered = modifiers == primary && code >= KeyEvent.VK_1 && code <= KeyEvent.VK_5;
        boolean secondVerb = modifiers == primary && code == KeyEvent.VK_ENTER;
        boolean thirdVerb = modifiers == InputEvent.SHIFT_DOWN_MASK && code == KeyEvent.VK_ENTER;
        boolean backTab = modifiers == InputEvent.SHIFT_DOWN_MASK && code == KeyEvent.VK_TAB;
        Runnable operation = null;
        if (numbered) operation = () -> palette.executeNumber(code - KeyEvent.VK_1 + 1);
        else if (secondVerb) operation = () -> palette.enterPressed(1);
        else if (thirdVerb) operation = () -> palette.enterPressed(2);
        else if (backTab) operation = () -> palette.tabPressed(true);
        else if (modifiers == 0) operation = switch (code) {
            case KeyEvent.VK_ESCAPE -> palette::escape;
            case KeyEvent.VK_ENTER -> () -> palette.enterPressed(0);
            case KeyEvent.VK_TAB -> () -> palette.tabPressed(false);
            case KeyEvent.VK_UP -> () -> palette.moveSelection(-1);
            case KeyEvent.VK_DOWN -> () -> palette.moveSelection(1);
            default -> null;
        };
        if (operation != null) {
            if (code != KeyEvent.VK_UP && code != KeyEvent.VK_DOWN) claim.accept(code);
            operation.run(); return true;
        }
        boolean nativeClipboard = source instanceof JTextComponent
            && (action.orElse(null) == ActionId.COPY || action.orElse(null) == ActionId.PASTE);
        if (action.isPresent() && !nativeClipboard) { claim.accept(code); return true; }
        return false;
    }

    /** Which scope an opening action targets; null for every other action. */
    static String scopeFor(ActionId id) {
        if (id == null) return null;
        return switch (id) {
            case COMMAND_PALETTE -> PaletteScope.COMMANDS_ID;
            case HISTORY_PALETTE -> PaletteScope.HISTORY_ID;
            default -> null;
        };
    }

    void reset() { swallowed.clear(); swallowTyped = false; }
    boolean drained() { return swallowed.isEmpty(); }
    @Override public void close() { closed = true; }
}
