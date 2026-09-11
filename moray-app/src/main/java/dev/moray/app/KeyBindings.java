package dev.moray.app;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Parses and indexes application keybindings independently of the Swing action layer. */
final class KeyBindings {
    private static final Map<String, Integer> NAMED_KEYS = namedKeys();

    private final Map<ActionId, KeyStroke> strokesByAction;
    private final Map<KeyStroke, ActionId> actionsByStroke;

    private KeyBindings(Map<ActionId, KeyStroke> strokesByAction) {
        this.strokesByAction = Map.copyOf(strokesByAction);
        Map<KeyStroke, ActionId> reverse = new HashMap<>();
        for (Map.Entry<ActionId, KeyStroke> entry : strokesByAction.entrySet()) {
            reverse.put(entry.getValue(), entry.getKey());
        }
        actionsByStroke = Map.copyOf(reverse);
    }

    static KeyBindings defaults(boolean macOs) {
        EnumMap<ActionId, KeyStroke> strokes = new EnumMap<>(ActionId.class);
        for (ActionId action : ActionId.values()) {
            String binding = action.defaultBinding();
            KeyStroke stroke = parse(binding, macOs).orElseThrow();
            if (!macOs && binding.contains("cmd+shift")) {
                stroke = KeyStroke.getKeyStroke(
                    stroke.getKeyCode(), stroke.getModifiers() | InputEvent.ALT_DOWN_MASK);
            }
            putWithoutCollision(strokes, action, stroke, binding);
        }
        return new KeyBindings(strokes);
    }

    static KeyBindings withOverrides(boolean macOs, Map<String, String> overrides) {
        if (overrides == null) {
            throw new IllegalArgumentException("keybinding overrides must not be null");
        }
        KeyBindings defaults = defaults(macOs);
        EnumMap<ActionId, KeyStroke> result = new EnumMap<>(defaults.strokesByAction);
        Map<ActionId, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String actionName = entry.getKey();
            ActionId action = actionForId(actionName);
            resolved.put(action, entry.getValue());
        }

        for (ActionId action : resolved.keySet()) {
            result.remove(action);
        }
        for (Map.Entry<ActionId, String> entry : resolved.entrySet()) {
            ActionId action = entry.getKey();
            String binding = entry.getValue();
            Optional<KeyStroke> parsed;
            try {
                parsed = parse(binding, macOs);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                    "invalid keybinding for action '" + action.id() + "': '" + binding + "' ("
                        + exception.getMessage() + ")",
                    exception);
            }
            parsed.ifPresent(stroke -> putWithoutCollision(result, action, stroke, binding));
        }
        return new KeyBindings(result);
    }

    Optional<ActionId> actionFor(KeyStroke stroke) {
        return Optional.ofNullable(actionsByStroke.get(stroke));
    }

    Optional<KeyStroke> strokeFor(ActionId action) {
        return Optional.ofNullable(strokesByAction.get(action));
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

    private static ActionId actionForId(String id) {
        if (id != null) {
            for (ActionId action : ActionId.values()) {
                if (action.id().equals(id)) {
                    return action;
                }
            }
        }
        throw new IllegalArgumentException("unknown action name: '" + id + "'");
    }

    private static void putWithoutCollision(
        Map<ActionId, KeyStroke> strokes, ActionId action, KeyStroke stroke, String binding
    ) {
        for (Map.Entry<ActionId, KeyStroke> existing : strokes.entrySet()) {
            if (existing.getValue().equals(stroke)) {
                throw new IllegalArgumentException(
                    "keybinding collision for '" + binding + "': actions '" + existing.getKey().id()
                        + "' and '" + action.id() + "'");
            }
        }
        strokes.put(action, stroke);
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
