package dev.jasper.browser.adblock;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One rule of a content-blocker list, as {@link ContentRules#compile} keeps it: when its trigger fits a request, its
 * action applies, unless a later rule's does.
 *
 * @param index its place among its list's rules, from 0; a rule with a greater index comes after
 * @param filter what the request's address must match, the trigger's {@code url-filter}
 * @param types the kinds of request it covers; empty for every kind
 * @param load whether it covers requests to the page's own site, to other sites, or to any
 * @param ifDomain the page hosts it alone covers, as the list writes them, in lower case: one that begins with
 *     {@code *} also covers its subdomains; empty when it names none
 * @param unlessDomain the page hosts it never covers, written the same way; empty when it names none
 * @param action what it does
 * @param selector the CSS selector of the elements a {@link Action#HIDE} rule hides; empty for any other action
 */
public record Rule(int index, UrlFilter filter, Set<ResourceKind> types, LoadType load, List<String> ifDomain,
    List<String> unlessDomain, Action action, String selector) {
    /** Copies the kinds and the hosts, so the rule is immutable. */
    public Rule {
        Objects.requireNonNull(filter, "filter");
        types = Collections.unmodifiableSet(types.isEmpty() ? EnumSet.noneOf(ResourceKind.class) : EnumSet.copyOf(types));
        Objects.requireNonNull(load, "load");
        ifDomain = List.copyOf(ifDomain);
        unlessDomain = List.copyOf(unlessDomain);
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(selector, "selector");
    }

    /** Which requests a rule covers by their site and the page's, by {@link BaseDomain}. */
    public enum LoadType {
        /** Every request. */
        ANY,
        /** A request to the page's own site: {@code first-party}. */
        FIRST_PARTY,
        /** A request to another site: {@code third-party}. */
        THIRD_PARTY
    }

    /** What a rule does. */
    public enum Action {
        /** The request is not made: {@code block}. */
        BLOCK,
        /** The page's elements its selector selects are hidden: {@code css-display-none}. */
        HIDE,
        /** The earlier rules' actions do not apply: {@code ignore-previous-rules}. */
        IGNORE_PREVIOUS
    }
}
