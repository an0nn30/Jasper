package dev.jasper.browser.adblock;

import dev.jasper.browser.adblock.Rule.Action;
import dev.jasper.browser.adblock.Rule.LoadType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A WebKit content-blocker list compiled into the {@link Rule}s Jasper applies, as {@link BlockerJson#read} gives the
 * list. A rule is kept when its trigger has a {@code url-filter} and no keys but {@code url-filter},
 * {@code url-filter-is-case-sensitive}, {@code resource-type}, {@code load-type} and one of {@code if-domain} and
 * {@code unless-domain}, and its action is {@code block}, {@code css-display-none} with its {@code selector}, or
 * {@code ignore-previous-rules}. Every other rule is skipped and counted, as are the rules past the first 250,000
 * kept. The list may be hostile, so a kept rule's filter keeps to {@link UrlFilter}'s bounds, each domain list has at
 * most 1,024 hosts and the list's rules 500,000 in all, and a selector list has at most 5,000 selectors, its parts
 * between top-level commas ({@link #parts}), each of at most 1,024 characters, none of them a brace, {@code ;},
 * {@code @}, {@code <} or a control character, so that a rule can only hide what its selectors select.
 */
public final class ContentRules {
    /** How many rules a list keeps at most. */
    static final int MAX_RULES = 250_000;
    /** How many hosts an {@code if-domain} or {@code unless-domain} list has at most. */
    static final int MAX_DOMAINS = 1_024;
    /** How many hosts a list's {@code if-domain} and {@code unless-domain} lists have at most in all. */
    static final int MAX_LIST_DOMAINS = 500_000;
    /** How many characters a selector of a selector list has at most. */
    static final int MAX_SELECTOR = 1_024;
    /** How many selectors a selector list has at most. */
    static final int MAX_PARTS = 5_000;
    private static final String FILTER = "url-filter", CASE = "url-filter-is-case-sensitive", TYPES = "resource-type", LOAD = "load-type",
        IF_DOMAIN = "if-domain", UNLESS_DOMAIN = "unless-domain";
    private static final Set<String> RULE_KEYS = Set.of("trigger", "action"),
        TRIGGER_KEYS = Set.of(FILTER, CASE, TYPES, LOAD, IF_DOMAIN, UNLESS_DOMAIN);
    private static final String TYPE_BLOCK = "block", TYPE_HIDE = "css-display-none", TYPE_IGNORE = "ignore-previous-rules";
    private static final String FIRST = "first-party", THIRD = "third-party";

    private ContentRules() { }

    /**
     * A list's rules, compiled.
     *
     * @param rules the rules kept, in the list's order, each numbered by its place among them
     * @param skipped how many of the list's rules were not kept
     */
    public record Compiled(List<Rule> rules, int skipped) {
        /** Copies the rules. */
        public Compiled {
            rules = List.copyOf(rules);
        }
    }

    /**
     * Compiles a list.
     *
     * @param list the list's rules, as {@link BlockerJson#read} gives them
     * @return the rules kept, and how many were skipped
     */
    public static Compiled compile(List<Map<String, Object>> list) {
        var rules = new ArrayList<Rule>();
        int skipped = 0, domains = 0;
        for (Map<String, Object> item : list) {
            if (rules.size() == MAX_RULES) {
                skipped++;
                continue;
            }
            try {
                Rule rule = rule(item, rules.size());
                int hosts = rule.ifDomain().size() + rule.unlessDomain().size();
                require(domains + hosts <= MAX_LIST_DOMAINS);
                domains += hosts;
                rules.add(rule);
            } catch (Unsupported unsupported) {
                skipped++;
            }
        }
        return new Compiled(rules, skipped);
    }

    /**
     * Rules as a list writes them, for {@link BlockerJson#write}: {@link #compile} reads them back as they are. A
     * trigger leaves out what is its default: a filter that is not case-sensitive, every type, and any load.
     *
     * @param rules the rules, in their order
     * @return the list
     */
    public static List<Map<String, Object>> encode(List<Rule> rules) {
        var list = new ArrayList<Map<String, Object>>(rules.size());
        for (Rule rule : rules) {
            var trigger = new LinkedHashMap<String, Object>();
            trigger.put(FILTER, rule.filter().source());
            if (rule.filter().caseSensitive()) trigger.put(CASE, true);
            if (!rule.types().isEmpty()) trigger.put(TYPES, rule.types().stream().map(ResourceKind::webKitName).toList());
            switch (rule.load()) {
                case FIRST_PARTY -> trigger.put(LOAD, List.of(FIRST));
                case THIRD_PARTY -> trigger.put(LOAD, List.of(THIRD));
                case ANY -> { }
            }
            if (!rule.ifDomain().isEmpty()) trigger.put(IF_DOMAIN, rule.ifDomain());
            if (!rule.unlessDomain().isEmpty()) trigger.put(UNLESS_DOMAIN, rule.unlessDomain());
            var action = new LinkedHashMap<String, Object>();
            action.put("type", switch (rule.action()) {
                case BLOCK -> TYPE_BLOCK;
                case HIDE -> TYPE_HIDE;
                case IGNORE_PREVIOUS -> TYPE_IGNORE;
            });
            if (rule.action() == Action.HIDE) action.put("selector", rule.selector());
            var item = new LinkedHashMap<String, Object>();
            item.put("trigger", trigger);
            item.put("action", action);
            list.add(item);
        }
        return list;
    }

    private static Rule rule(Map<String, Object> item, int index) throws Unsupported {
        require(item.keySet().equals(RULE_KEYS));
        Map<String, Object> trigger = object(item.get("trigger"));
        require(TRIGGER_KEYS.containsAll(trigger.keySet()));
        boolean caseSensitive = trigger.containsKey(CASE) && flag(trigger.get(CASE));
        UrlFilter filter = UrlFilter.of(text(trigger.get(FILTER)), caseSensitive).orElseThrow(() -> Unsupported.UNSUPPORTED);
        Set<ResourceKind> types = trigger.containsKey(TYPES) ? types(trigger.get(TYPES)) : Set.of();
        LoadType load = trigger.containsKey(LOAD) ? load(trigger.get(LOAD)) : LoadType.ANY;
        List<String> ifDomain = domains(trigger, IF_DOMAIN), unlessDomain = domains(trigger, UNLESS_DOMAIN);
        require(ifDomain.isEmpty() || unlessDomain.isEmpty());
        Map<String, Object> action = object(item.get("action"));
        String type = text(action.get("type"));
        return switch (type) {
            case TYPE_BLOCK, TYPE_IGNORE -> {
                require(action.size() == 1);
                yield new Rule(index, filter, types, load, ifDomain, unlessDomain,
                    type.equals(TYPE_BLOCK) ? Action.BLOCK : Action.IGNORE_PREVIOUS, "");
            }
            case TYPE_HIDE -> {
                require(action.size() == 2);
                yield new Rule(index, filter, types, load, ifDomain, unlessDomain, Action.HIDE, selector(action.get("selector")));
            }
            default -> throw Unsupported.UNSUPPORTED;
        };
    }

    private static Set<ResourceKind> types(Object value) throws Unsupported {
        var types = EnumSet.noneOf(ResourceKind.class);
        for (Object name : array(value)) types.add(ResourceKind.named(text(name)).orElseThrow(() -> Unsupported.UNSUPPORTED));
        return types;
    }

    private static LoadType load(Object value) throws Unsupported {
        boolean first = false, third = false;
        for (Object name : array(value)) {
            switch (text(name)) {
                case FIRST -> first = true;
                case THIRD -> third = true;
                default -> throw Unsupported.UNSUPPORTED;
            }
        }
        return first && third ? LoadType.ANY : first ? LoadType.FIRST_PARTY : LoadType.THIRD_PARTY;
    }

    /** The trigger's hosts under {@code key}, in lower case; empty when it has none. */
    private static List<String> domains(Map<String, Object> trigger, String key) throws Unsupported {
        if (!trigger.containsKey(key)) return List.of();
        List<?> written = array(trigger.get(key));
        require(written.size() <= MAX_DOMAINS);
        var domains = new ArrayList<String>(written.size());
        for (Object value : written) {
            String domain = UrlFilter.fold(text(value));
            String host = domain.startsWith("*") ? domain.substring(1) : domain;
            require(!host.isEmpty());
            for (int at = 0; at < host.length(); at++) {
                char c = host.charAt(at);
                require(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.' || c == '-' || c == '_');
            }
            domains.add(domain);
        }
        return domains;
    }

    /**
     * The selectors of a selector list, as a page hides one line for each: its parts between the commas that are
     * outside parentheses, brackets and quotes and not escaped, each without the spaces around it. A list of more than
     * 5,000 is read only to the one after the 5,000th.
     *
     * @param list a {@code css-display-none} rule's {@code selector}
     * @return its parts, in order; one empty part for each comma with nothing beside it
     */
    static List<String> parts(String list) {
        var parts = new ArrayList<String>();
        int depth = 0, start = 0;
        char quote = 0;
        for (int at = 0; at < list.length() && parts.size() <= MAX_PARTS; at++) {
            char c = list.charAt(at);
            if (c == '\\') at++;
            else if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == '(' || c == '[') depth++;
            else if ((c == ')' || c == ']') && depth > 0) depth--;
            else if (c == ',' && depth == 0) {
                parts.add(trimmed(list, start, at));
                start = at + 1;
            }
        }
        if (parts.size() <= MAX_PARTS) parts.add(trimmed(list, start, list.length()));
        return parts;
    }

    /** The text from {@code start} to {@code end} without the spaces around it, but for an escaped one. */
    private static String trimmed(String list, int start, int end) {
        while (start < end && list.charAt(start) == ' ') start++;
        while (end > start && list.charAt(end - 1) == ' ' && !(end - 2 >= start && list.charAt(end - 2) == '\\')) end--;
        return list.substring(start, end);
    }

    private static String selector(Object value) throws Unsupported {
        String selector = text(value);
        List<String> parts = parts(selector);
        require(parts.size() <= MAX_PARTS);
        for (String part : parts) {
            require(!part.isEmpty() && part.length() <= MAX_SELECTOR);
            for (int at = 0; at < part.length(); at++) {
                char c = part.charAt(at);
                require(c >= 0x20 && c != 0x7F && "{};@<".indexOf(c) < 0);
            }
        }
        return selector;
    }

    /** A non-empty array. */
    private static List<?> array(Object value) throws Unsupported {
        if (!(value instanceof List<?> list) || list.isEmpty()) throw Unsupported.UNSUPPORTED;
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) throws Unsupported {
        if (!(value instanceof Map<?, ?> map)) throw Unsupported.UNSUPPORTED;
        return (Map<String, Object>) map;
    }

    private static String text(Object value) throws Unsupported {
        if (!(value instanceof String text)) throw Unsupported.UNSUPPORTED;
        return text;
    }

    private static boolean flag(Object value) throws Unsupported {
        if (!(value instanceof Boolean flag)) throw Unsupported.UNSUPPORTED;
        return flag;
    }

    private static void require(boolean supported) throws Unsupported {
        if (!supported) throw Unsupported.UNSUPPORTED;
    }

    /** A rule Jasper does not apply. Carries no stack: it only ends the rule's compiling. */
    private static final class Unsupported extends Exception {
        private static final Unsupported UNSUPPORTED = new Unsupported();

        private Unsupported() {
            super(null, null, false, false);
        }
    }
}
