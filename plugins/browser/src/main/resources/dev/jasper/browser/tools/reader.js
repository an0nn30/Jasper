/*
 * The Browser's reader mode (spec section 10). Jasper runs this in a page's top frame, in the page's own world, never in
 * a sub-frame, a challenge frame or one of Jasper's own pages, right after Readability.js, which the same script keeps
 * under window[Symbol.for('dev.jasper.browser.readability')]. It is called with a command, the token of Jasper's check
 * and, to show, the overlay's CSS:
 *
 * - "check": once the page is idle, Readability parses a copy of the document, and Jasper is told, through
 *   jasperBridgeQuery (op "reader"), {check: token, length: n}, the length of the article's text with its white space
 *   collapsed. It is 0 when the document has more than 50,000 elements, which are then not copied; when Readability
 *   finds no article; and when it fails, as on a page that enforces Trusted Types.
 * - "show": Readability parses a copy of the document again, and the article is laid over the page in an element fixed
 *   over the whole viewport, topmost, on the document's root element. Its open shadow root holds the article's title,
 *   or "Article", then its content, styled by the CSS given and the overlay's own on a sheet the shadow root adopts,
 *   which no content security policy governs. The content is Readability's own node, never HTML text, without what
 *   would run anything or restyle the overlay: script, iframe, object, embed, form and the like, every on* attribute
 *   and every javascript: address. The overlay scrolls by itself, and the page's own scrolling is held until it
 *   closes. A capture-phase Escape closes it, is stopped, and tells Jasper {check: token, state: "off"}; so does a show
 *   that finds no article.
 * - "hide": the overlay closes, and the page scrolls again.
 *
 * The reader is made once per document, kept under window[Symbol.for('dev.jasper.browser.reader')], and calls the
 * built-ins as they were when it was made. The page's own scripts share this world, so they can see and undo all of
 * it, and what they send Jasper is untrusted.
 */
(function (command, token, css) {
  'use strict';
  const KEY = Symbol.for('dev.jasper.browser.reader');
  let reader = null;
  if (Reflect.apply(Object.prototype.hasOwnProperty, window, [KEY])) {
    reader = window[KEY];
  } else {
    reader = make();
    if (reader === null) return;
    try {
      Object.defineProperty(window, KEY, {value: reader, writable: false, enumerable: false, configurable: false});
    } catch (taken) {
      return;
    }
  }
  if (reader === null || typeof reader !== 'object') return;
  if (command === 'check') reader.check(token);
  else if (command === 'show') reader.show(css, token);
  else if (command === 'hide') reader.hide();

  /** The reader of this document, or null where the built-ins it needs are missing. */
  function make() {
    const READABILITY = Symbol.for('dev.jasper.browser.readability');
    // The most elements a document may have to be read; Readability's own limit too.
    const LIMIT = 50000;
    // How long the check waits for the page to be idle at most, in milliseconds.
    const IDLE_WAIT = 2000;
    // The class of the overlay's root, which the CSS Jasper gives selects for the theme's colours.
    const ROOT = 'reader';
    const DROPPED = 'script, iframe, frame, frameset, object, embed, form, style, link, meta, base';
    const DIRECTIONS = ['ltr', 'rtl', 'auto'];
    const OVERFLOW = ['overflow-x', 'overflow-y'];
    const WHITE = /\s+/g, IGNORED = /[\x00-\x20\x7f]+/g, SCRIPTED = /^javascript:/i;
    const LAYOUT = `
:host {
  all: initial !important; display: block !important; position: fixed !important; inset: 0 !important;
  z-index: 2147483647 !important;
}
.reader {
  position: absolute; inset: 0; overflow: auto; overscroll-behavior: contain; outline: none;
  background: var(--background); color: var(--text);
  font: 18px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
}
article { max-width: 680px; margin: 0 auto; padding: 48px 24px 96px; overflow-wrap: break-word; }
h1 { margin: 0 0 24px; font-size: 32px; line-height: 1.25; font-weight: 600; }
h2, h3, h4, h5, h6 { margin: 32px 0 12px; line-height: 1.3; font-weight: 600; }
p, ul, ol, dl, blockquote, pre, figure, table { margin: 0 0 18px; }
a { color: var(--accent); }
a:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
img, video, svg, picture, canvas { max-width: 100%; height: auto; }
figure { margin-left: 0; margin-right: 0; }
figcaption { color: var(--muted); font-size: 14px; }
blockquote { margin-left: 0; padding-left: 16px; border-left: 3px solid var(--border); color: var(--muted); }
pre, code, kbd, samp { font: 15px/1.5 ui-monospace, Menlo, Consolas, monospace; }
pre { overflow: auto; padding: 12px 16px; border: 1px solid var(--border); border-radius: 6px; background: var(--card); }
table { display: block; overflow-x: auto; border-collapse: collapse; }
th, td { padding: 4px 8px; border: 1px solid var(--border); }
hr { border: 0; border-top: 1px solid var(--border); }
`;

    const apply = Reflect.apply, describe = Object.getOwnPropertyDescriptor, freeze = Object.freeze;
    const isSafeInteger = Number.isSafeInteger, stringify = JSON.stringify, Json = JSON;
    const query = window.jasperBridgeQuery, idle = window.requestIdleCallback, later = window.setTimeout;
    // The page's interfaces, by their constructors.
    const Sheets = window.CSSStyleSheet, ShadowRoots = window.ShadowRoot, Documents = window.Document, Elements = window.Element;
    const HtmlElements = window.HTMLElement, Nodes = window.Node, Targets = window.EventTarget, KeyEvents = window.KeyboardEvent;
    const Styles = window.CSSStyleDeclaration, Events = window.Event;
    const constructors = [Sheets, ShadowRoots, Documents, Elements, HtmlElements, Nodes, Targets, KeyEvents, Styles, Events];
    if (typeof query !== 'function') return null;
    for (let at = 0; at < constructors.length; at++) if (typeof constructors[at] !== 'function') return null;
    const replaceSync = Sheets.prototype.replaceSync, adopted = describe(ShadowRoots.prototype, 'adoptedStyleSheets');
    const getElementsByTagName = Documents.prototype.getElementsByTagName, createElement = Documents.prototype.createElement;
    const rootOf = describe(Documents.prototype, 'documentElement'), bodyOf = describe(Documents.prototype, 'body');
    const attachShadow = Elements.prototype.attachShadow, querySelectorAll = Elements.prototype.querySelectorAll,
      getAttributeNames = Elements.prototype.getAttributeNames, getAttribute = Elements.prototype.getAttribute,
      setAttribute = Elements.prototype.setAttribute, removeAttribute = Elements.prototype.removeAttribute,
      remove = Elements.prototype.remove;
    const styleOf = describe(HtmlElements.prototype, 'style'), focus = HtmlElements.prototype.focus;
    const cloneNode = Nodes.prototype.cloneNode, appendChild = Nodes.prototype.appendChild,
      textOf = describe(Nodes.prototype, 'textContent');
    const listen = Targets.prototype.addEventListener, unlisten = Targets.prototype.removeEventListener;
    const keyOf = describe(KeyEvents.prototype, 'key');
    const stop = Events.prototype.stopImmediatePropagation, prevent = Events.prototype.preventDefault;
    const setProperty = Styles.prototype.setProperty, removeProperty = Styles.prototype.removeProperty,
      getPropertyValue = Styles.prototype.getPropertyValue, getPropertyPriority = Styles.prototype.getPropertyPriority;
    const replace = String.prototype.replace, trim = String.prototype.trim, slice = String.prototype.slice,
      toLowerCase = String.prototype.toLowerCase, test = RegExp.prototype.test, includes = Array.prototype.includes;
    const page = document;
    if (typeof replaceSync !== 'function' || !adopted || typeof adopted.set !== 'function' || !rootOf || !bodyOf
        || !styleOf || !textOf || !keyOf || typeof attachShadow !== 'function') return null;

    // The overlay while it shows: its element, the token its Escape reports and the page's scrolling it holds.
    let shown = null;

    /** Tells Jasper {@code fields}, through the opener bridge; a failure tells nothing, and the page goes on. */
    function report(fields) {
      try {
        apply(query, window, [{request: apply(stringify, Json, [fields]), persistent: false, onSuccess() {}, onFailure() {}}]);
      } catch (unsent) {
        // Jasper hears nothing.
      }
    }

    function valid(value) {
      return typeof value === 'number' && isSafeInteger(value) && value > 0;
    }

    /** Once the page is idle, tells Jasper how much text Readability finds, for {@code token}. */
    function check(token) {
      if (!valid(token)) return;
      const run = () => report({op: 'reader', check: token, length: articleLength()});
      try {
        if (typeof idle === 'function') apply(idle, window, [run, {timeout: IDLE_WAIT}]);
        else apply(later, window, [run, 0]);
      } catch (unscheduled) {
        report({op: 'reader', check: token, length: 0});
      }
    }

    /** The length of the article's text, its white space collapsed; 0 for none, past the limit and on failure. */
    function articleLength() {
      try {
        const article = parsed({maxElemsToParse: LIMIT});
        return article === null ? 0 : apply(trim, apply(replace, article.textContent, [WHITE, ' ']), []).length;
      } catch (failed) {
        return 0;
      }
    }

    /**
     * What Readability makes of a copy of the document, with {@code options}: null for a document past the limit, which
     * is not copied, and for no article.
     */
    function parsed(options) {
      if (apply(getElementsByTagName, page, ['*']).length > LIMIT) return null;
      const Readability = window[READABILITY];
      if (typeof Readability !== 'function') return null;
      const article = new Readability(apply(cloneNode, page, [true]), options).parse();
      return article !== null && typeof article === 'object' && typeof article.textContent === 'string' ? article : null;
    }

    /** Lays the article over the page, in {@code css} and the overlay's own; Escape will report {@code token}. */
    function show(css, token) {
      if (typeof css !== 'string' || !valid(token)) return;
      if (shown !== null) {
        shown.token = token;
        return;
      }
      let article = null;
      try {
        article = parsed({maxElemsToParse: LIMIT, serializer: node => node});
      } catch (failed) {
        article = null;
      }
      if (article === null || !(article.content instanceof Elements)) {
        report({op: 'reader', check: token, state: 'off'});
        return;
      }
      let host = null;
      const held = [];
      try {
        host = apply(createElement, page, ['div']);
        const root = apply(attachShadow, host, [{mode: 'open'}]);
        const sheet = new Sheets();
        apply(replaceSync, sheet, [css + '\n' + LAYOUT]);
        apply(adopted.set, root, [[sheet]]);
        const scroller = apply(createElement, page, ['div']);
        apply(setAttribute, scroller, ['class', ROOT]);
        apply(setAttribute, scroller, ['tabindex', '-1']);
        const body = apply(createElement, page, ['article']);
        if (apply(includes, DIRECTIONS, [article.dir])) apply(setAttribute, body, ['dir', article.dir]);
        if (typeof article.lang === 'string' && article.lang !== '') apply(setAttribute, body, ['lang', article.lang]);
        const heading = apply(createElement, page, ['h1']);
        apply(textOf.set, heading, [title(article.title)]);
        apply(appendChild, body, [heading]);
        apply(appendChild, body, [cleaned(article.content)]);
        apply(appendChild, scroller, [body]);
        apply(appendChild, root, [scroller]);
        hold(held);
        apply(appendChild, apply(rootOf.get, page, []), [host]);
        shown = {host, token, held};
        apply(listen, window, ['keydown', onKey, true]);
        apply(listen, window, ['pagehide', hide, true]);
        apply(focus, scroller, [{preventScroll: true}]);
      } catch (failed) {
        shown = null;
        end(host, held);
        report({op: 'reader', check: token, state: 'off'});
      }
    }

    /** The overlay closes, and the page scrolls again. */
    function hide() {
      const now = shown;
      if (now === null) return;
      shown = null;
      end(now.host, now.held);
    }

    function end(host, held) {
      try {
        apply(unlisten, window, ['keydown', onKey, true]);
        apply(unlisten, window, ['pagehide', hide, true]);
      } catch (kept) {
        // A listener the page took away is gone already.
      }
      try {
        if (host !== null) apply(remove, host, []);
      } catch (kept) {
        // An overlay the page took away is gone already.
      }
      restore(held);
    }

    /** Escape closes the overlay, the page hears nothing of it, and Jasper is told. */
    function onKey(event) {
      if (shown === null || event.isTrusted !== true || apply(keyOf.get, event, []) !== 'Escape') return;
      apply(stop, event, []);
      apply(prevent, event, []);
      const token = shown.token;
      hide();
      report({op: 'reader', check: token, state: 'off'});
    }

    /** {@code value} trimmed, or "Article" for none. */
    function title(value) {
      const text = typeof value === 'string' ? apply(trim, value, []) : '';
      return text === '' ? 'Article' : text;
    }

    /** {@code content} without what would run or restyle anything: the dropped elements, on* attributes and javascript: addresses. */
    function cleaned(content) {
      const dropped = apply(querySelectorAll, content, [DROPPED]);
      for (let at = 0; at < dropped.length; at++) apply(remove, dropped[at], []);
      attributes(content);
      const all = apply(querySelectorAll, content, ['*']);
      for (let at = 0; at < all.length; at++) attributes(all[at]);
      return content;
    }

    function attributes(element) {
      const names = apply(getAttributeNames, element, []);
      for (let at = 0; at < names.length; at++) {
        const name = names[at], value = apply(getAttribute, element, [name]);
        if (apply(toLowerCase, apply(slice, name, [0, 2]), []) === 'on'
            || (typeof value === 'string' && apply(test, SCRIPTED, [apply(replace, value, [IGNORED, ''])])))
          apply(removeAttribute, element, [name]);
      }
    }

    /**
     * Holds the page's own scrolling, on its root and body elements, each property kept in {@code held} before it is
     * set, so a failure part way restores what it set.
     */
    function hold(held) {
      const elements = [apply(rootOf.get, page, []), apply(bodyOf.get, page, [])];
      for (let at = 0; at < elements.length; at++) {
        if (!(elements[at] instanceof HtmlElements)) continue;
        const style = apply(styleOf.get, elements[at], []);
        for (let each = 0; each < OVERFLOW.length; each++) {
          const name = OVERFLOW[each];
          held[held.length] = {style, name, value: apply(getPropertyValue, style, [name]), priority: apply(getPropertyPriority, style, [name])};
          apply(setProperty, style, [name, 'hidden', 'important']);
        }
      }
    }

    /** The page's own scrolling as it was before {@code hold}, the last property set restored first. */
    function restore(held) {
      for (let at = held.length - 1; at >= 0; at--) {
        const kept = held[at];
        try {
          if (kept.value === '') apply(removeProperty, kept.style, [kept.name]);
          else apply(setProperty, kept.style, [kept.name, kept.value, kept.priority]);
        } catch (changed) {
          // A style the page replaced keeps the page's.
        }
      }
    }

    return freeze({check, show, hide});
  }
})
