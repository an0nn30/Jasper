package dev.jasper.browser.adblock;

import dev.jasper.browser.adblock.Rule.Action;
import dev.jasper.browser.adblock.Rule.LoadType;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * A compiled list, applied: which requests it blocks, and which of a page's elements it hides. A rule matches a
 * request when its filter matches the request's address, its types meet the request's kinds, its load type fits (a
 * first-party request is one to the page's own site, as {@link BaseDomain} names sites), and its domain lists fit the
 * page's host. A request is blocked when the last rule it matches that blocks or ignores the earlier rules is one that
 * blocks; a page hides what its matching hide rules after its last matching ignore rule select, its own document
 * taken as the request.
 *
 * <p>Requests are answered on Chromium's IO thread and sheets on its UI thread, so neither scans the rules: an index
 * keys each rule by the hosts of its {@code if-domain}, which a page's host or one of its parent domains must be, or
 * else by the rarest 4-character run of its filter's literal, counted over every rule's literal, which the address
 * must hold; only the rest are checked for every request. No rule's expression runs on an address that lacks its
 * literal. The hide rules that cover every page's document are joined once, as the sheet every page shares. A
 * request's and a page's address are read to their first 8 KiB. A rule set is immutable, and safe on any thread.
 */
public final class RuleSet {
    private static final Set<ResourceKind> PAGE = Collections.unmodifiableSet(EnumSet.of(ResourceKind.DOCUMENT));
    /** A list with no rules, which blocks and hides nothing. */
    public static final RuleSet EMPTY = of(List.of());
    /** How many characters a page's sheet has at most. */
    static final int SHEET_LIMIT = 2 * 1024 * 1024;
    private static final String HIDDEN = "{display:none!important}\n";
    private static final int GRAM = 4;

    private final Entry[] entries;
    private final Index requests, pages;
    private final BiPredicate<UrlFilter, String> expression;
    // The sheet every page shares, and for each of its lines the place of its rule and where the line starts.
    private final String generic;
    private final int[] genericPlaces, genericStarts;

    private RuleSet(List<Rule> rules, BiPredicate<UrlFilter, String> expression) {
        this.expression = expression;
        entries = rules.stream().sorted(Comparator.comparingInt(Rule::index)).map(Entry::of).toArray(Entry[]::new);
        Map<String, Integer> rarity = rarity(entries);
        requests = Index.of(entries, entry -> entry.rule().action() != Action.HIDE, rarity);
        pages = Index.of(entries, entry -> entry.rule().action() == Action.IGNORE_PREVIOUS || entry.rule().action() == Action.HIDE && !entry.generic(),
            rarity);
        var sheet = new StringBuilder();
        var places = new Ints();
        var starts = new Ints();
        for (int at = 0; at < entries.length; at++) {
            Rule rule = entries[at].rule();
            if (rule.action() != Action.HIDE || !entries[at].generic()) continue;
            if (sheet.length() + rule.selector().length() + HIDDEN.length() > SHEET_LIMIT) break;
            places.add(at);
            starts.add(sheet.length());
            sheet.append(rule.selector()).append(HIDDEN);
        }
        generic = sheet.toString();
        genericPlaces = places.toArray();
        genericStarts = starts.toArray();
    }

    /**
     * The rule set of {@code rules}.
     *
     * @param rules the rules, as {@link ContentRules#compile} keeps them; they apply in the order of their indexes
     * @return the rule set
     */
    public static RuleSet of(List<Rule> rules) {
        return of(rules, UrlFilter::matches);
    }

    /** The rule set of {@code rules}, whose expressions run through {@code expression}: a test's counting seam. */
    static RuleSet of(List<Rule> rules, BiPredicate<UrlFilter, String> expression) {
        return new RuleSet(rules, expression);
    }

    /**
     * How many rules the set has.
     *
     * @return the count
     */
    public int size() {
        return entries.length;
    }

    /**
     * Whether the request is blocked.
     *
     * @param url the request's address
     * @param kinds the kinds it loads; none, as a main frame's load has, is never blocked
     * @param pageUrl the address of the page that makes it; null or one with no host, such as {@code about:blank}, fits
     *     no {@code if-domain}, every {@code unless-domain}, and no first party
     * @return whether its last matching rule that blocks or ignores the earlier rules is one that blocks
     */
    public boolean blocks(String url, Set<ResourceKind> kinds, String pageUrl) {
        if (url == null || kinds == null || kinds.isEmpty()) return false;
        Request request = Request.of(url, kinds, pageUrl);
        int[] candidates = requests.candidates(request);
        for (int at = candidates.length - 1; at >= 0; at--) {
            Entry entry = entries[candidates[at]];
            if (matches(entry, request)) return entry.rule().action() == Action.BLOCK;
        }
        return false;
    }

    /** {@link #blocks}, by a scan of every rule: what the index must agree with. */
    boolean scan(String url, Set<ResourceKind> kinds, String pageUrl) {
        if (url == null || kinds == null || kinds.isEmpty()) return false;
        Request request = Request.of(url, kinds, pageUrl);
        int lastBlock = -1, lastIgnore = -1;
        for (int at = 0; at < entries.length; at++) {
            Action action = entries[at].rule().action();
            if (action == Action.HIDE || !fits(entries[at], request) || !entries[at].rule().filter().matches(request.url())) continue;
            if (action == Action.BLOCK) lastBlock = at;
            else lastIgnore = at;
        }
        return lastBlock > lastIgnore;
    }

    /**
     * What the page hides: one {@code selector{display:none!important}} line for each hide rule that matches the page's
     * own document and comes after the last ignore rule that does, the shared sheet's lines first, at most 2 MiB of
     * them in all.
     *
     * @param pageUrl the page's address
     * @return the lines, each ended by a line feed; empty when it hides nothing
     */
    public String stylesheet(String pageUrl) {
        return sheet(pageUrl, true);
    }

    /**
     * The part of {@link #stylesheet} every page shares: the lines of the hide rules that cover every page's document,
     * after the last ignore rule that matches this page's.
     *
     * @param pageUrl the page's address
     * @return the lines, each ended by a line feed; empty when they hide nothing
     */
    public String genericStylesheet(String pageUrl) {
        return sheet(pageUrl, false);
    }

    /** {@link #stylesheet}, by a scan of every rule and without its bound: what the index must agree with. */
    String scanStylesheet(String pageUrl) {
        Request page = Request.page(pageUrl);
        var sheet = new StringBuilder();
        for (Entry entry : entries) {
            Action action = entry.rule().action();
            if (action == Action.BLOCK || !fits(entry, page) || !entry.rule().filter().matches(page.url())) continue;
            if (action == Action.IGNORE_PREVIOUS) sheet.setLength(0);
            else sheet.append(entry.rule().selector()).append(HIDDEN);
        }
        return sheet.toString();
    }

    private String sheet(String pageUrl, boolean own) {
        Request page = Request.page(pageUrl);
        int[] candidates = pages.candidates(page);
        int ignored = -1;
        var hidden = new Ints();
        for (int at = candidates.length - 1; at >= 0; at--) {
            Entry entry = entries[candidates[at]];
            boolean ignore = entry.rule().action() == Action.IGNORE_PREVIOUS;
            if (!(ignore || own) || !matches(entry, page)) continue;
            if (ignore) {
                ignored = candidates[at];
                break;
            }
            hidden.add(candidates[at]);
        }
        int first = Arrays.binarySearch(genericPlaces, ignored + 1);
        if (first < 0) first = -first - 1;
        String shared = first == 0 ? generic : first == genericPlaces.length ? "" : generic.substring(genericStarts[first]);
        if (hidden.size() == 0) return shared;
        var sheet = new StringBuilder(shared);
        for (int at = hidden.size() - 1; at >= 0; at--) {
            String selector = entries[hidden.get(at)].rule().selector();
            if (sheet.length() + selector.length() + HIDDEN.length() > SHEET_LIMIT) break;
            sheet.append(selector).append(HIDDEN);
        }
        return sheet.toString();
    }

    /** Whether the rule matches the request: its trigger fits, and the address holds its literal and matches it. */
    private boolean matches(Entry entry, Request request) {
        if (!fits(entry, request)) return false;
        UrlFilter filter = entry.rule().filter();
        return (filter.caseSensitive() ? request.url() : request.folded()).contains(filter.literalText())
            && expression.test(filter, request.url());
    }

    /** Whether the rule's types, load type and domain lists fit the request. */
    private static boolean fits(Entry entry, Request request) {
        Rule rule = entry.rule();
        if (!meets(rule.types(), request.kinds())) return false;
        boolean party = switch (rule.load()) {
            case ANY -> true;
            case FIRST_PARTY -> request.firstParty();
            case THIRD_PARTY -> !request.firstParty();
        };
        if (!party) return false;
        if (!entry.ifDomain().isEmpty() && !entry.ifDomain().covers(request.pageHost())) return false;
        return entry.unlessDomain().isEmpty() || !entry.unlessDomain().covers(request.pageHost());
    }

    /** Whether a rule's types, none meaning every type, meet a request's kinds. */
    private static boolean meets(Set<ResourceKind> types, Set<ResourceKind> kinds) {
        if (kinds.isEmpty()) return false;
        if (types.isEmpty()) return true;
        for (ResourceKind kind : kinds) if (types.contains(kind)) return true;
        return false;
    }

    /** For each 4-character run of the rules' literals, how many literals hold it. */
    private static Map<String, Integer> rarity(Entry[] entries) {
        var rarity = new HashMap<String, Integer>();
        var grams = new HashSet<String>();
        for (Entry entry : entries) {
            String literal = entry.rule().filter().literalText();
            grams.clear();
            for (int at = 0; at + GRAM <= literal.length(); at++) grams.add(literal.substring(at, at + GRAM));
            for (String gram : grams) rarity.merge(gram, 1, Integer::sum);
        }
        return rarity;
    }

    private static String capped(String url) {
        return url.length() > UrlFilter.URL_LIMIT ? url.substring(0, UrlFilter.URL_LIMIT) : url;
    }

    /**
     * A rule, with its domain lists ready to look a host up in.
     *
     * @param rule the rule
     * @param ifDomain the hosts of its {@code if-domain}
     * @param unlessDomain the hosts of its {@code unless-domain}
     */
    private record Entry(Rule rule, Domains ifDomain, Domains unlessDomain) {
        static Entry of(Rule rule) {
            return new Entry(rule, Domains.of(rule.ifDomain()), Domains.of(rule.unlessDomain()));
        }

        /** Whether a hide rule covers every page's own document, and so belongs to the sheet every page shares. */
        boolean generic() {
            return rule.filter().matchesEverything() && ifDomain.isEmpty() && unlessDomain.isEmpty() && meets(rule.types(), PAGE)
                && rule.load() != LoadType.THIRD_PARTY;
        }
    }

    /**
     * A domain list's hosts.
     *
     * @param exactly the hosts it names alone
     * @param withSubdomains the hosts it names with a {@code *}, which covers their subdomains too
     */
    private record Domains(Set<String> exactly, Set<String> withSubdomains) {
        private static final Domains NONE = new Domains(Set.of(), Set.of());

        static Domains of(List<String> written) {
            if (written.isEmpty()) return NONE;
            var exactly = new HashSet<String>();
            var withSubdomains = new HashSet<String>();
            for (String domain : written) {
                if (domain.startsWith("*")) withSubdomains.add(domain.substring(1));
                else exactly.add(domain);
            }
            return new Domains(Set.copyOf(exactly), Set.copyOf(withSubdomains));
        }

        boolean isEmpty() {
            return exactly.isEmpty() && withSubdomains.isEmpty();
        }

        boolean covers(String host) {
            if (host.isEmpty()) return false;
            if (exactly.contains(host) || withSubdomains.contains(host)) return true;
            if (withSubdomains.isEmpty()) return false;
            for (int dot = host.indexOf('.'); dot >= 0; dot = host.indexOf('.', dot + 1))
                if (withSubdomains.contains(host.substring(dot + 1))) return true;
            return false;
        }
    }

    /**
     * A request, as the rules read it.
     *
     * @param url its address's first 8 KiB
     * @param folded that, its ASCII capital letters made small
     * @param kinds the kinds it loads
     * @param pageHost the host of the page that makes it; empty when it has none
     * @param firstParty whether its site is the page's
     */
    private record Request(String url, String folded, Set<ResourceKind> kinds, String pageHost, boolean firstParty) {
        static Request of(String url, Set<ResourceKind> kinds, String pageUrl) {
            String address = capped(url), pageHost = BaseDomain.host(capped(pageUrl == null ? "" : pageUrl)), site = BaseDomain.of(pageHost);
            return new Request(address, UrlFilter.fold(address), kinds, pageHost,
                !site.isEmpty() && site.equals(BaseDomain.of(BaseDomain.host(address))));
        }

        /** A page's own document, which is of its own site. */
        static Request page(String pageUrl) {
            String address = capped(pageUrl == null ? "" : pageUrl);
            return new Request(address, UrlFilter.fold(address), PAGE, BaseDomain.host(address), true);
        }
    }

    /**
     * The rules of one use, a request's or a page's, keyed for a request to find those it may match: by the hosts of a
     * rule's {@code if-domain}; else by the rarest 4-character run of its literal, folded unless it is case-sensitive;
     * else not at all, which leaves it for every request.
     */
    private static final class Index {
        private final Map<String, int[]> domains, folded, exact;
        private final int[] always;

        private Index(Map<String, int[]> domains, Map<String, int[]> folded, Map<String, int[]> exact, int[] always) {
            this.domains = domains;
            this.folded = folded;
            this.exact = exact;
            this.always = always;
        }

        static Index of(Entry[] entries, Predicate<Entry> member, Map<String, Integer> rarity) {
            var domains = new HashMap<String, Ints>();
            var folded = new HashMap<String, Ints>();
            var exact = new HashMap<String, Ints>();
            var always = new Ints();
            for (int at = 0; at < entries.length; at++) {
                if (!member.test(entries[at])) continue;
                Rule rule = entries[at].rule();
                String literal = rule.filter().literalText();
                if (!rule.ifDomain().isEmpty()) {
                    for (String domain : rule.ifDomain()) add(domains, domain.startsWith("*") ? domain.substring(1) : domain, at);
                } else if (literal.length() >= GRAM) add(rule.filter().caseSensitive() ? exact : folded, rarest(literal, rarity), at);
                else always.add(at);
            }
            return new Index(frozen(domains), frozen(folded), frozen(exact), always.toArray());
        }

        /** The places of the rules the request may match, in ascending order, each once. */
        int[] candidates(Request request) {
            var found = new Ints();
            found.addAll(always);
            Set<int[]> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            grams(folded, request.folded(), found, seen);
            grams(exact, request.url(), found, seen);
            if (!domains.isEmpty() && !request.pageHost().isEmpty()) {
                for (String host = request.pageHost(); ; ) {
                    int[] places = domains.get(host);
                    if (places != null && seen.add(places)) found.addAll(places);
                    int dot = host.indexOf('.');
                    if (dot < 0) break;
                    host = host.substring(dot + 1);
                }
            }
            return found.sortedOnce();
        }

        private static void grams(Map<String, int[]> index, String text, Ints found, Set<int[]> seen) {
            if (index.isEmpty()) return;
            for (int at = 0; at + GRAM <= text.length(); at++) {
                int[] places = index.get(text.substring(at, at + GRAM));
                if (places != null && seen.add(places)) found.addAll(places);
            }
        }

        private static String rarest(String literal, Map<String, Integer> rarity) {
            String rarest = null;
            int fewest = Integer.MAX_VALUE;
            for (int at = 0; at + GRAM <= literal.length(); at++) {
                String gram = literal.substring(at, at + GRAM);
                int count = rarity.get(gram);
                if (count < fewest) {
                    rarest = gram;
                    fewest = count;
                }
            }
            return rarest;
        }

        private static void add(Map<String, Ints> index, String key, int place) {
            index.computeIfAbsent(key, unused -> new Ints()).add(place);
        }

        private static Map<String, int[]> frozen(Map<String, Ints> index) {
            var frozen = new HashMap<String, int[]>(index.size() * 4 / 3 + 1);
            index.forEach((key, places) -> frozen.put(key, places.sortedOnce()));
            return frozen;
        }
    }

    /** Places of rules, as many as are added. */
    private static final class Ints {
        private int[] values = new int[4];
        private int size;

        void add(int value) {
            if (size == values.length) values = Arrays.copyOf(values, size * 2);
            values[size++] = value;
        }

        void addAll(int[] more) {
            if (size + more.length > values.length) values = Arrays.copyOf(values, Math.max(values.length * 2, size + more.length));
            System.arraycopy(more, 0, values, size, more.length);
            size += more.length;
        }

        int size() {
            return size;
        }

        int get(int at) {
            return values[at];
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }

        /** The places in ascending order, each once. */
        int[] sortedOnce() {
            int[] sorted = toArray();
            Arrays.sort(sorted);
            int kept = 0;
            for (int value : sorted) if (kept == 0 || sorted[kept - 1] != value) sorted[kept++] = value;
            return kept == sorted.length ? sorted : Arrays.copyOf(sorted, kept);
        }
    }
}
