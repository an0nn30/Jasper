package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.KeyContext;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Parses and indexes application keybindings by action id, independently of the Swing action layer.
 * Built-in ids come from {@link ActionId}; namespaced (dotted) ids belong to contributed actions.
 * A user's binding for a dotted id is validated when the configuration loads but bound only once
 * {@link #withExtensions} learns that the action exists.
 * <p>Every action has a {@link KeyContext}. A stroke may belong to several actions whose contexts don't conflict;
 * {@link #resolve} picks the most specific one that applies where focus is.
 */
public final class KeyBindings {
    /**
     * A contributed action, the shortcuts its contributor would like and where they apply.
     *
     * @param defaultBinding the default as written, used as is on macOS
     * @param otherDefault the Linux and Windows default when the contributor named one ("none" for no default there);
     *                     empty means it follows from {@code defaultBinding}
     * @param context where the shortcut applies
     */
    public record Extension(String id, Optional<String> defaultBinding, Optional<String> otherDefault, KeyContext context) {
        public Extension {
            if (!extensionId(id)) throw new IllegalArgumentException("not a contributed action id: '" + id + "'");
            Objects.requireNonNull(defaultBinding, "defaultBinding");
            Objects.requireNonNull(otherDefault, "otherDefault");
            Objects.requireNonNull(context, "context");
        }

        /** A Global action with one default for every platform. */
        public Extension(String id, Optional<String> defaultBinding) {
            this(id, defaultBinding, Optional.empty(), KeyContext.GLOBAL);
        }

        /**
         * The default on this platform. macOS takes the default as written. Elsewhere an explicit other default wins;
         * without one, cmd means Ctrl+Shift and a default naming cmd and shift gains alt, as built-in defaults always
         * did, so {@code cmd+shift+x} does not land on the same keys as {@code cmd+x}.
         */
        public Optional<String> defaultFor(boolean macOs) {
            if (macOs) return defaultBinding;
            if (otherDefault.isPresent()) return otherDefault;
            return defaultBinding.map(KeyBindings::withAltRule);
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
    private final Map<String, KeyContext> contexts;
    private final Map<KeyStroke, List<String>> idsByStroke;
    private final Map<String, Optional<KeyStroke>> extensionOverrides;

    private KeyBindings(boolean macOs, Map<String, KeyStroke> strokesById, Map<String, KeyContext> contexts,
                        Map<String, Optional<KeyStroke>> extensionOverrides) {
        this.macOs = macOs;
        this.strokesById = Collections.unmodifiableMap(new LinkedHashMap<>(strokesById));
        this.contexts = Map.copyOf(contexts);
        Map<KeyStroke, List<String>> reverse = new HashMap<>();
        strokesById.forEach((id, stroke) -> reverse.computeIfAbsent(stroke, ignored -> new ArrayList<>()).add(id));
        reverse.replaceAll((stroke, ids) -> List.copyOf(ids));
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
            String binding = action.defaultBinding(macOs);
            Optional<KeyStroke> stroke = parse(binding, macOs);
            if (stroke.isPresent()) putWithoutConflict(strokes, Map.of(), action.id(), action.context(), stroke.get(), binding);
        }
        return new KeyBindings(macOs, strokes, Map.of(), Map.of());
    }

    /** A built-in's default for this platform as written; "none" when it has none there. */
    public static String effectiveDefaultBinding(ActionId action, boolean macOs) {
        return action.defaultBinding(macOs);
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
            parseFor(entry.getKey(), entry.getValue(), macOs).ifPresent(stroke -> putWithoutConflict(result, Map.of(),
                entry.getKey(), contextOf(entry.getKey(), Map.of()), stroke, entry.getValue()));
        }
        // A contributed action's shortcut must not collide with anything the user or the defaults claim, but it is
        // not bound here: the action may not exist in this launch. Its context is unknown until it registers, so the
        // check treats it as Global.
        Map<String, KeyStroke> claimed = new LinkedHashMap<>(result);
        Map<String, Optional<KeyStroke>> extensionOverrides = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : extensions.entrySet()) {
            Optional<KeyStroke> parsed = parseFor(entry.getKey(), entry.getValue(), macOs);
            parsed.ifPresent(stroke -> putWithoutConflict(claimed, Map.of(), entry.getKey(), KeyContext.GLOBAL, stroke, entry.getValue()));
            extensionOverrides.put(entry.getKey(), parsed);
        }
        return new KeyBindings(macOs, result, Map.of(), extensionOverrides);
    }

    /**
     * Adds contributed actions in registration order. The user's binding for an action wins; otherwise its default
     * for this platform applies unless it is invalid or an existing binding holds the stroke in a conflicting context.
     * Always call this on the result of {@link #withOverrides} or {@link #defaults}, never on an already extended
     * instance.
     */
    public Resolved withExtensions(List<Extension> extensions) {
        Map<String, KeyStroke> strokes = new LinkedHashMap<>();
        strokesById.forEach((id, stroke) -> { if (!extensionId(id)) strokes.put(id, stroke); });
        Map<String, KeyContext> registered = new LinkedHashMap<>();
        for (Extension extension : extensions) registered.putIfAbsent(extension.id(), extension.context());
        List<Problem> problems = new ArrayList<>();
        for (Map.Entry<String, Optional<KeyStroke>> override : extensionOverrides.entrySet()) {
            if (!registered.containsKey(override.getKey())) {
                problems.add(new Problem(Problem.Kind.UNKNOWN_ACTION, override.getKey(), "Unknown action; ignored."));
                continue;
            }
            override.getValue().ifPresent(stroke -> strokes.put(override.getKey(), stroke));
        }
        for (Extension extension : extensions) {
            if (extensionOverrides.containsKey(extension.id())) continue;
            Optional<String> wanted = extension.defaultFor(macOs);
            if (wanted.isEmpty()) continue;
            String binding = wanted.get();
            Optional<KeyStroke> parsed;
            try { parsed = parse(binding, macOs); }
            catch (IllegalArgumentException invalid) {
                problems.add(new Problem(Problem.Kind.DEFAULT_DROPPED, extension.id(),
                    "Default shortcut '" + binding + "' is not valid; the action is unbound."));
                continue;
            }
            if (parsed.isEmpty()) continue;
            String holder = conflicting(strokes, registered, extension.context(), parsed.get());
            if (holder != null) {
                problems.add(new Problem(Problem.Kind.DEFAULT_DROPPED, extension.id(), "Default shortcut '" + binding
                    + "' is already used by '" + holder + "'; bind the action under [keybindings] to give it one."));
                continue;
            }
            strokes.put(extension.id(), parsed.get());
        }
        return new Resolved(new KeyBindings(macOs, strokes, registered, extensionOverrides), problems);
    }

    /** The Global or Terminal action bound to a stroke, if any; actions in owned contexts are found with {@link #resolve}. */
    public Optional<String> idFor(KeyStroke stroke) {
        for (String id : idsFor(stroke)) if (contextFor(id).level() <= 1) return Optional.of(id);
        return Optional.empty();
    }

    /** Every action bound to a stroke: built-ins first, then contributed actions in registration order. */
    public List<String> idsFor(KeyStroke stroke) {
        return idsByStroke.getOrDefault(stroke, List.of());
    }

    /**
     * The action a stroke runs where focus is: of the actions bound to it whose context applies, the most specific
     * (an owned context, then Terminal, then Global). Empty when none applies.
     */
    public Optional<String> resolve(KeyStroke stroke, Predicate<KeyContext> applies) {
        String best = null;
        int level = -1;
        for (String id : idsFor(stroke)) {
            KeyContext context = contextFor(id);
            if (context.level() > level && applies.test(context)) { best = id; level = context.level(); }
        }
        return Optional.ofNullable(best);
    }

    /** Where an id's shortcut applies: a built-in's own context, a registered contributed action's, otherwise Global. */
    public KeyContext contextFor(String id) {
        return contextOf(id, contexts);
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

    /** Serializes physical keys without cmd's non-macOS modifier translation. */
    public static String format(KeyStroke stroke, boolean macOs) {
        Objects.requireNonNull(stroke, "stroke");
        var text = new StringBuilder();
        int mods = stroke.getModifiers();
        if ((mods & InputEvent.CTRL_DOWN_MASK) != 0) text.append("ctrl+");
        if ((mods & InputEvent.ALT_DOWN_MASK) != 0) text.append("alt+");
        if ((mods & InputEvent.SHIFT_DOWN_MASK) != 0) text.append("shift+");
        if ((mods & InputEvent.META_DOWN_MASK) != 0) text.append(macOs ? "cmd+" : "meta+");
        if ((mods & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) text.append("altgraph+");
        String key = NAMED_KEYS.entrySet().stream().filter(e -> e.getValue() == stroke.getKeyCode())
            .map(Map.Entry::getKey).sorted().findFirst().orElseGet(() -> KeyEvent.getKeyText(stroke.getKeyCode()).toLowerCase(Locale.ROOT));
        String binding = text.append(key).toString();
        if (!parse(binding, macOs).filter(stroke::equals).isPresent())
            throw new IllegalArgumentException("This key combination cannot be saved. Try another shortcut.");
        return binding;
    }

    /** Parses a saved binding; none means explicitly unassigned. */
    /** A stroke as menus and the palette show it: "\u21e7\u2318P" on macOS, "Ctrl+Shift+P" elsewhere, "" for none. */
    public static String display(KeyStroke stroke, boolean macOs) {
        if (stroke == null) return "";
        int modifiers = stroke.getModifiers();
        String key = stroke.getKeyCode() == 0
            ? String.valueOf(stroke.getKeyChar()) : java.awt.event.KeyEvent.getKeyText(stroke.getKeyCode());
        if (macOs) {
            var text = new StringBuilder();
            if ((modifiers & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0) text.append("\u2303");
            if ((modifiers & java.awt.event.InputEvent.ALT_DOWN_MASK) != 0) text.append("\u2325");
            if ((modifiers & java.awt.event.InputEvent.SHIFT_DOWN_MASK) != 0) text.append("\u21e7");
            if ((modifiers & java.awt.event.InputEvent.META_DOWN_MASK) != 0) text.append("\u2318");
            return text.append(key).toString();
        }
        var names = new java.util.ArrayList<String>();
        if ((modifiers & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0) names.add("Ctrl");
        if ((modifiers & java.awt.event.InputEvent.ALT_DOWN_MASK) != 0) names.add("Alt");
        if ((modifiers & java.awt.event.InputEvent.SHIFT_DOWN_MASK) != 0) names.add("Shift");
        if ((modifiers & java.awt.event.InputEvent.META_DOWN_MASK) != 0) names.add("Meta");
        names.add(key);
        return String.join("+", names);
    }

    public static Optional<KeyStroke> parse(String binding, boolean macOs) {
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
                case "meta" -> modifiers |= InputEvent.META_DOWN_MASK;
                case "altgraph" -> modifiers |= InputEvent.ALT_GRAPH_DOWN_MASK;
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

    /** The non-macOS rule for a contributed cmd default: one naming cmd and shift gains alt. */
    static String withAltRule(String binding) {
        List<String> tokens = Arrays.stream(binding.trim().toLowerCase(Locale.ROOT).split("\\+", -1)).map(String::trim).toList();
        return tokens.contains("cmd") && tokens.contains("shift") && !tokens.contains("alt") ? "alt+" + binding : binding;
    }

    private static KeyContext contextOf(String id, Map<String, KeyContext> contexts) {
        return ActionId.forId(id).map(ActionId::context).orElseGet(() -> contexts.getOrDefault(id, KeyContext.GLOBAL));
    }

    /** The first action holding the stroke in a context that would make it ambiguous with this one, or null. */
    private static String conflicting(Map<String, KeyStroke> strokes, Map<String, KeyContext> contexts,
                                      KeyContext context, KeyStroke stroke) {
        for (Map.Entry<String, KeyStroke> existing : strokes.entrySet())
            if (existing.getValue().equals(stroke) && contextOf(existing.getKey(), contexts).conflictsWith(context))
                return existing.getKey();
        return null;
    }

    private static void putWithoutConflict(Map<String, KeyStroke> strokes, Map<String, KeyContext> contexts, String id,
                                           KeyContext context, KeyStroke stroke, String binding) {
        String holder = conflicting(strokes, contexts, context, stroke);
        if (holder != null) {
            throw new IllegalArgumentException(
                "keybinding collision for '" + binding + "': actions '" + holder + "' and '" + id + "'");
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
