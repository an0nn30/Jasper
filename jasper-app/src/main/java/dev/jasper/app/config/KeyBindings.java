package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses and indexes application keybindings by action id, independently of the Swing action layer.
 * Built-in ids come from {@link ActionId}; namespaced (dotted) ids belong to contributed actions.
 * A user's binding for a dotted id is validated when the configuration loads but bound only once
 * {@link #withExtensions} learns that the action exists.
 */
public final class KeyBindings {
    /** A contributed action and the shortcut its contributor would like, if any. */
    public record Extension(String id, Optional<String> defaultBinding) {
        public Extension {
            if (!extensionId(id)) throw new IllegalArgumentException("not a contributed action id: '" + id + "'");
            Objects.requireNonNull(defaultBinding, "defaultBinding");
        }
    }

    /** Something {@link #withExtensions} could not honor. */
    public record Problem(Kind kind, String actionId, String message) {
        public enum Kind {
            /** The user bound an id that no registered action has: a configuration warning. */
            UNKNOWN_ACTION,
            /** A contributor's default lost to an existing binding or was invalid: logged, not a warning. */
            DEFAULT_DROPPED
        }
    }

    /** Effective bindings and what was dropped on the way. */
    public record Resolved(KeyBindings bindings, List<Problem> problems) {
        public Resolved { problems = List.copyOf(problems); }
    }

    private static final Map<String, Integer> NAMED_KEYS = namedKeys();
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    private final boolean macOs;
    private final Map<String, KeyStroke> strokesById;
    private final Map<KeyStroke, String> idsByStroke;
    private final Map<String, Optional<KeyStroke>> extensionOverrides;

    private KeyBindings(boolean macOs, Map<String, KeyStroke> strokesById, Map<String, Optional<KeyStroke>> extensionOverrides) {
        this.macOs = macOs;
        this.strokesById = Collections.unmodifiableMap(new LinkedHashMap<>(strokesById));
        Map<KeyStroke, String> reverse = new HashMap<>();
        for (Map.Entry<String, KeyStroke> entry : strokesById.entrySet()) reverse.put(entry.getValue(), entry.getKey());
        idsByStroke = Map.copyOf(reverse);
        this.extensionOverrides = Collections.unmodifiableMap(new LinkedHashMap<>(extensionOverrides));
    }

    /** Whether the id is well formed and namespaced with at least one dot, which no built-in id is. */
    public static boolean extensionId(String id) {
        return id != null && id.length() <= 128 && ID.matcher(id).matches();
    }

    public static KeyBindings defaults(boolean macOs) {
        Map<String, KeyStroke> strokes = new LinkedHashMap<>();
        for (ActionId action : ActionId.values()) {
            String binding = effectiveDefaultBinding(action, macOs);
            KeyStroke stroke = parse(binding, macOs).orElseThrow();
            putWithoutCollision(strokes, action.id(), stroke, binding);
        }
        return new KeyBindings(macOs, strokes, Map.of());
    }

    /** Text that round-trips the effective default, including non-macOS compatibility modifiers. */
    public static String effectiveDefaultBinding(ActionId action, boolean macOs) {
        String binding = action.defaultBinding(macOs);
        if (action == ActionId.COMMAND_PALETTE || action == ActionId.CLEAR_SCROLLBACK || action == ActionId.HISTORY_PALETTE
            || action == ActionId.SNIPPETS_PALETTE) return binding;
        return !macOs && binding.contains("cmd+shift") ? "alt+" + binding : binding;
    }

    public static KeyBindings withOverrides(boolean macOs, Map<String, String> overrides) {
        if (overrides == null) {
            throw new IllegalArgumentException("keybinding overrides must not be null");
        }
        Map<String, KeyStroke> result = new LinkedHashMap<>(defaults(macOs).strokesById);
        Map<String, String> builtIn = new LinkedHashMap<>();
        Map<String, String> extensions = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String id = entry.getKey();
            if (ActionId.forId(id).isPresent()) builtIn.put(id, entry.getValue());
            else if (extensionId(id)) extensions.put(id, entry.getValue());
            else throw new IllegalArgumentException("unknown action name: '" + id + "'");
        }
        builtIn.keySet().forEach(result::remove);
        for (Map.Entry<String, String> entry : builtIn.entrySet()) {
            parseFor(entry.getKey(), entry.getValue(), macOs)
                .ifPresent(stroke -> putWithoutCollision(result, entry.getKey(), stroke, entry.getValue()));
        }
        // A contributed action's shortcut must not collide with anything the user or the defaults claim,
        // but it is not bound here: the action may not exist in this launch.
        Map<String, KeyStroke> claimed = new LinkedHashMap<>(result);
        Map<String, Optional<KeyStroke>> extensionOverrides = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : extensions.entrySet()) {
            Optional<KeyStroke> parsed = parseFor(entry.getKey(), entry.getValue(), macOs);
            parsed.ifPresent(stroke -> putWithoutCollision(claimed, entry.getKey(), stroke, entry.getValue()));
            extensionOverrides.put(entry.getKey(), parsed);
        }
        return new KeyBindings(macOs, result, extensionOverrides);
    }

    /**
     * Adds contributed actions in registration order. The user's binding for an action wins; otherwise
     * its default applies unless the shortcut is already taken or invalid. Always call this on the
     * result of {@link #withOverrides} or {@link #defaults}, never on an already extended instance.
     */
    public Resolved withExtensions(List<Extension> extensions) {
        Map<String, KeyStroke> strokes = new LinkedHashMap<>();
        strokesById.forEach((id, stroke) -> { if (!extensionId(id)) strokes.put(id, stroke); });
        List<Problem> problems = new ArrayList<>();
        Set<String> registered = new LinkedHashSet<>();
        for (Extension extension : extensions) registered.add(extension.id());
        for (Map.Entry<String, Optional<KeyStroke>> override : extensionOverrides.entrySet()) {
            if (!registered.contains(override.getKey())) {
                problems.add(new Problem(Problem.Kind.UNKNOWN_ACTION, override.getKey(), "Unknown action; ignored."));
                continue;
            }
            override.getValue().ifPresent(stroke -> strokes.put(override.getKey(), stroke));
        }
        for (Extension extension : extensions) {
            if (extensionOverrides.containsKey(extension.id()) || extension.defaultBinding().isEmpty()) continue;
            String binding = extension.defaultBinding().get();
            Optional<KeyStroke> parsed;
            try { parsed = parse(binding, macOs); }
            catch (IllegalArgumentException invalid) {
                problems.add(new Problem(Problem.Kind.DEFAULT_DROPPED, extension.id(),
                    "Default shortcut '" + binding + "' is not valid; the action is unbound."));
                continue;
            }
            if (parsed.isEmpty()) continue;
            String holder = null;
            for (Map.Entry<String, KeyStroke> existing : strokes.entrySet())
                if (existing.getValue().equals(parsed.get())) holder = existing.getKey();
            if (holder != null) {
                problems.add(new Problem(Problem.Kind.DEFAULT_DROPPED, extension.id(), "Default shortcut '" + binding
                    + "' is already used by '" + holder + "'; bind the action under [keybindings] to give it one."));
                continue;
            }
            strokes.put(extension.id(), parsed.get());
        }
        return new Resolved(new KeyBindings(macOs, strokes, extensionOverrides), problems);
    }

    public Optional<String> idFor(KeyStroke stroke) {
        return Optional.ofNullable(idsByStroke.get(stroke));
    }

    public Optional<KeyStroke> strokeFor(String id) {
        return Optional.ofNullable(strokesById.get(id));
    }

    /** Every bound id with its shortcut, built-ins first, in a stable order. */
    public Map<String, KeyStroke> strokes() { return strokesById; }

    public Optional<ActionId> actionFor(KeyStroke stroke) {
        return idFor(stroke).flatMap(ActionId::forId);
    }

    public Optional<KeyStroke> strokeFor(ActionId action) {
        return strokeFor(action.id());
    }

    private static Optional<KeyStroke> parseFor(String id, String binding, boolean macOs) {
        try {
            return parse(binding, macOs);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "invalid keybinding for action '" + id + "': '" + binding + "' (" + exception.getMessage() + ")", exception);
        }
    }

    static Optional<KeyStroke> parse(String binding, boolean macOs) {
        if (binding == null) {
            throw new IllegalArgumentException("keybinding must not be null");
        }
        String normalized = binding.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("none")) {
            return Optional.empty();
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("keybinding must not be blank");
        }

        int modifiers = 0;
        Integer keyCode = null;
        for (String rawToken : normalized.split("\\+", -1)) {
            String token = rawToken.trim();
            switch (token) {
                case "cmd" -> modifiers |= macOs
                    ? InputEvent.META_DOWN_MASK
                    : InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
                case "ctrl" -> modifiers |= InputEvent.CTRL_DOWN_MASK;
                case "alt" -> modifiers |= InputEvent.ALT_DOWN_MASK;
                case "shift" -> modifiers |= InputEvent.SHIFT_DOWN_MASK;
                default -> {
                    if (token.equals("{") || token.equals("}")) {
                        modifiers |= InputEvent.SHIFT_DOWN_MASK;
                        token = token.equals("{") ? "[" : "]";
                    }
                    int parsedKey = keyCode(token);
                    if (keyCode != null) {
                        throw new IllegalArgumentException("keybinding has more than one key: " + binding);
                    }
                    keyCode = parsedKey;
                }
            }
        }
        if (keyCode == null) {
            throw new IllegalArgumentException("keybinding has no key: " + binding);
        }
        return Optional.of(KeyStroke.getKeyStroke(keyCode, modifiers));
    }

    private static void putWithoutCollision(Map<String, KeyStroke> strokes, String id, KeyStroke stroke, String binding) {
        for (Map.Entry<String, KeyStroke> existing : strokes.entrySet()) {
            if (existing.getValue().equals(stroke)) {
                throw new IllegalArgumentException(
                    "keybinding collision for '" + binding + "': actions '" + existing.getKey() + "' and '" + id + "'");
            }
        }
        strokes.put(id, stroke);
    }

    private static int keyCode(String token) {
        Integer named = NAMED_KEYS.get(token);
        if (named != null) {
            return named;
        }
        if (token.length() == 1) {
            int code = KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(token.charAt(0)));
            if (code != KeyEvent.VK_UNDEFINED) {
                return code;
            }
        }
        if (token.length() >= 2 && token.charAt(0) == 'f') {
            try {
                int number = Integer.parseInt(token.substring(1));
                if (number >= 1 && number <= 24) {
                    return number <= 12 ? KeyEvent.VK_F1 + number - 1 : KeyEvent.VK_F13 + number - 13;
                }
            } catch (NumberFormatException ignored) {
                // Report the complete token below.
            }
        }
        throw new IllegalArgumentException("unknown key: '" + token + "'");
    }

    private static Map<String, Integer> namedKeys() {
        Map<String, Integer> keys = new HashMap<>();
        keys.put("left", KeyEvent.VK_LEFT);
        keys.put("right", KeyEvent.VK_RIGHT);
        keys.put("up", KeyEvent.VK_UP);
        keys.put("down", KeyEvent.VK_DOWN);
        keys.put("enter", KeyEvent.VK_ENTER);
        keys.put("return", KeyEvent.VK_ENTER);
        keys.put("tab", KeyEvent.VK_TAB);
        keys.put("space", KeyEvent.VK_SPACE);
        keys.put("escape", KeyEvent.VK_ESCAPE);
        keys.put("esc", KeyEvent.VK_ESCAPE);
        keys.put("backspace", KeyEvent.VK_BACK_SPACE);
        keys.put("delete", KeyEvent.VK_DELETE);
        keys.put("insert", KeyEvent.VK_INSERT);
        keys.put("home", KeyEvent.VK_HOME);
        keys.put("end", KeyEvent.VK_END);
        keys.put("pageup", KeyEvent.VK_PAGE_UP);
        keys.put("pagedown", KeyEvent.VK_PAGE_DOWN);
        keys.put("[", KeyEvent.VK_OPEN_BRACKET);
        keys.put("]", KeyEvent.VK_CLOSE_BRACKET);
        keys.put("=", KeyEvent.VK_EQUALS);
        keys.put("-", KeyEvent.VK_MINUS);
        keys.put(",", KeyEvent.VK_COMMA);
        keys.put(".", KeyEvent.VK_PERIOD);
        keys.put("/", KeyEvent.VK_SLASH);
        keys.put("\\", KeyEvent.VK_BACK_SLASH);
        keys.put(";", KeyEvent.VK_SEMICOLON);
        keys.put("'", KeyEvent.VK_QUOTE);
        keys.put("`", KeyEvent.VK_BACK_QUOTE);
        return Map.copyOf(keys);
    }
}
