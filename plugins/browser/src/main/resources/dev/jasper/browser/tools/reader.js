/*
 * The Browser's reader mode (spec section 10). Jasper runs this in a page's top frame, in the page's own world, never in
 * a sub-frame, a challenge frame or one of Jasper's own pages, right after Readability.js, which the same script keeps
 * under window[Symbol.for('dev.jasper.browser.readability')]. It is called with a command, the token of Jasper's check
 * and, to show, the overlay's CSS:
 *
 * - "check": once the page is idle, Readability parses a copy of the document, and Jasper is told, through
 *   jasperBridgeQuery (op "reader"), {check: token, length: n}, the length of the article's text with its white space
 *   collapsed. It is 0 when the document has more than 50,000 elements, which are then not copied; when Readability
 *   finds no article; and when it fails. On a page that enforces Trusted Types, Readability fails where it parses HTML
 *   text into its copy, which it does only on a retry pass over a page whose first pass found too little text, and for
 *   an image inside a noscript element.
 * - "show": Readability parses a copy of the document again, and the article is laid over the page in an element fixed
 *   over the whole viewport, topmost, on the document's root element, outside its body. Its open shadow root holds the
 *   article's title, or "Article", then its content, styled by the CSS given and the overlay's own on a sheet the
 *   shadow root adopts, which no content security policy governs. The content is Readability's own node, never HTML
 *   text, without what would run anything or restyle the overlay: script, iframe, object, embed, form, SMIL's animate
 *   and set and the like, every on* attribute and every javascript: address; a custom element, or one with an is
 *   attribute, gives way to its children, so none of the page's element definitions runs inside the overlay. The
 *   overlay scrolls by itself and takes the focus, and a link to a fragment of the document scrolls the overlay to its
 *   target there. Until the overlay closes, the page's own scrolling is held, its body is inert, so neither Tab nor a
 *   click reaches it, and every key the user presses stops at the window's capture listeners, its default action
 *   kept, so the page's own shortcuts do not act behind the overlay. A capture listener the page added to the window
 *   earlier still hears the keys first. Each pointer, mouse, click and touch event in the overlay, and each copy, cut,
 *   dragstart, selectstart, focusin and focusout there, stops at its shadow root, its default action kept, so the
 *   page's bubbling listeners never hear it, though a capture listener the page added to the window, the document or
 *   the root element hears it first; a wheel goes on. Escape closes the overlay, is stopped and prevented, and Jasper
 *   is told {check: token, state: "off"}, as for a show that finds no article; the overlay's listeners stay until
 *   Escape's keyup, which they swallow, or until their end, as the page goes or the reader shows again, whichever
 *   comes first, and meanwhile every Escape keydown the user makes, as a held Escape repeats, is stopped and prevented
 *   too, whether or not it says it repeats.
 * - "hide": the overlay closes; the page scrolls, takes keys and clicks again, and its earlier focus comes back.
 *
 * A report the bridge refuses because too many of the page's requests wait is sent again a little later, a few times.
 *
 * The reader is made once per document, kept under window[Symbol.for('dev.jasper.browser.reader')], and calls the
 * built-ins as they were when it was made; its root() is the overlay's shadow root while it shows, else null, where
 * Jasper's find in page (find.js) then searches, and its sheet styles find's highlights there, as find.js colours them;
 * its held() tells Save Page As (save-page.js), read only, what the overlay holds of the page while it shows: the
 * overlay's host, body's earlier inert and the earlier overflow of the root and the body, so the saved copy is the page.
 * The page's own scripts share this world, so they can see and undo all of it, and what they send Jasper is untrusted.
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
    const DROPPED = 'script, iframe, frame, frameset, object, embed, form, style, link, meta, base, animate, set';
    const DIRECTIONS = ['ltr', 'rtl', 'auto'];
    const OVERFLOW = ['overflow-x', 'overflow-y'];
    // The bridge's answer to a page with too many requests waiting, and how often and how long after a report is sent
    // again.
    const BUSY = 5, RETRIES = 20, RETRY_WAIT = 100;
    // What stops at the overlay's shadow root: the page's bubbling listeners never hear the reader's pointer, mouse,
    // click and touch events, its clipboard's, drags' and selections' starts and its focus moves; capture listeners on
    // the window, the document or the root element hear them first. A wheel is not among them.
    const INPUT = ['pointerdown', 'pointerup', 'pointermove', 'pointerover', 'pointerout', 'pointercancel', 'mousedown', 'mouseup',
      'mousemove', 'mouseover', 'mouseout', 'click', 'auxclick', 'dblclick', 'contextmenu', 'touchstart', 'touchend', 'touchmove',
      'touchcancel', 'copy', 'cut', 'dragstart', 'selectstart', 'focusin', 'focusout'];
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
::highlight(jasper-find) { background-color: #FFFF00; color: #000000; }
::highlight(jasper-find-current) { background-color: #FF9632; color: #000000; }
`;

    const apply = Reflect.apply, describe = Object.getOwnPropertyDescriptor, freeze = Object.freeze;
    const isSafeInteger = Number.isSafeInteger, stringify = JSON.stringify, Json = JSON;
    const query = window.jasperBridgeQuery, idle = window.requestIdleCallback, later = window.setTimeout;
    // The page's interfaces, by their constructors.
    const Sheets = window.CSSStyleSheet, ShadowRoots = window.ShadowRoot, Documents = window.Document, Elements = window.Element;
    const HtmlElements = window.HTMLElement, Anchors = window.HTMLAnchorElement, Nodes = window.Node, Targets = window.EventTarget;
    const KeyEvents = window.KeyboardEvent, MouseEvents = window.MouseEvent, Styles = window.CSSStyleDeclaration, Events = window.Event;
    const constructors = [Sheets, ShadowRoots, Documents, Elements, HtmlElements, Anchors, Nodes, Targets, KeyEvents, MouseEvents, Styles,
      Events];
    if (typeof query !== 'function') return null;
    for (let at = 0; at < constructors.length; at++) if (typeof constructors[at] !== 'function') return null;
    // A getter of {@code type}'s prototype, as it is now; null for none.
    const getter = (type, name) => {
      const property = describe(type.prototype, name);
      return property && typeof property.get === 'function' ? property.get : null;
    };
    const replaceSync = Sheets.prototype.replaceSync, adopted = describe(ShadowRoots.prototype, 'adoptedStyleSheets');
    const getElementsByTagName = Documents.prototype.getElementsByTagName, createElement = Documents.prototype.createElement;
    const rootOf = getter(Documents, 'documentElement'), bodyOf = getter(Documents, 'body'),
      activeOf = getter(Documents, 'activeElement'), urlOf = getter(Documents, 'URL');
    const attachShadow = Elements.prototype.attachShadow, querySelectorAll = Elements.prototype.querySelectorAll,
      getAttributeNames = Elements.prototype.getAttributeNames, getAttribute = Elements.prototype.getAttribute,
      setAttribute = Elements.prototype.setAttribute, removeAttribute = Elements.prototype.removeAttribute,
      remove = Elements.prototype.remove, replaceWith = Elements.prototype.replaceWith, closest = Elements.prototype.closest,
      boundsOf = Elements.prototype.getBoundingClientRect, localNameOf = getter(Elements, 'localName');
    const scrollTop = describe(Elements.prototype, 'scrollTop'), inertOf = describe(HtmlElements.prototype, 'inert');
    const styleOf = getter(HtmlElements, 'style'), focus = HtmlElements.prototype.focus, hrefOf = getter(Anchors, 'href');
    const cloneNode = Nodes.prototype.cloneNode, appendChild = Nodes.prototype.appendChild,
      textOf = describe(Nodes.prototype, 'textContent'), childrenOf = getter(Nodes, 'childNodes'),
      connectedOf = getter(Nodes, 'isConnected');
    const listen = Targets.prototype.addEventListener, unlisten = Targets.prototype.removeEventListener;
    const keyOf = getter(KeyEvents, 'key'), buttonOf = getter(MouseEvents, 'button');
    const modifiers = [getter(MouseEvents, 'ctrlKey'), getter(MouseEvents, 'metaKey'), getter(MouseEvents, 'shiftKey'),
      getter(MouseEvents, 'altKey')];
    const stop = Events.prototype.stopImmediatePropagation, stopHere = Events.prototype.stopPropagation,
      prevent = Events.prototype.preventDefault, targetOf = getter(Events, 'target'), typeOf = getter(Events, 'type');
    const setProperty = Styles.prototype.setProperty, removeProperty = Styles.prototype.removeProperty,
      getPropertyValue = Styles.prototype.getPropertyValue, getPropertyPriority = Styles.prototype.getPropertyPriority;
    const replace = String.prototype.replace, trim = String.prototype.trim, slice = String.prototype.slice,
      indexOf = String.prototype.indexOf, toLowerCase = String.prototype.toLowerCase, test = RegExp.prototype.test,
      includes = Array.prototype.includes, decode = decodeURIComponent;
    const page = document;
    const needed = [replaceSync, rootOf, bodyOf, activeOf, urlOf, attachShadow, replaceWith, closest, boundsOf, localNameOf, styleOf,
      hrefOf, childrenOf, connectedOf, keyOf, buttonOf, targetOf, typeOf, stopHere, modifiers[0], modifiers[1], modifiers[2],
      modifiers[3]];
    for (let at = 0; at < needed.length; at++) if (typeof needed[at] !== 'function') return null;
    if (!adopted || typeof adopted.set !== 'function' || !textOf || typeof textOf.set !== 'function' || !scrollTop
        || typeof scrollTop.get !== 'function' || typeof scrollTop.set !== 'function' || !inertOf || typeof inertOf.get !== 'function'
        || typeof inertOf.set !== 'function') return null;

    // The overlay while it shows: its element, its shadow root and the root's scroller, the token its Escape reports,
    // and what it changed in the page, each with what undoes it.
    let shown = null;
    // An overlay Escape closed, whose listeners wait for Escape's keyup.
    let closing = null;

    /**
     * Tells Jasper {@code fields}, through the opener bridge; a busy bridge is asked again a little later, a few times,
     * and any other failure tells nothing, and the page goes on.
     */
    function report(fields) {
      try {
        send(apply(stringify, Json, [fields]), 0);
      } catch (unsent) {
        // Jasper hears nothing.
      }
    }

    /** Sends {@code request}, the {@code retries}th time again; a busy bridge's refusal sends it again later. */
    function send(request, retries) {
      try {
        apply(query, window, [{request, persistent: false, onSuccess() {},
          onFailure(code) {
            if (code !== BUSY || retries >= RETRIES || typeof later !== 'function') return;
            try {
              apply(later, window, [() => send(request, retries + 1), RETRY_WAIT]);
            } catch (unscheduled) {
              // Jasper hears nothing.
            }
          }}]);
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
      if (closing !== null) {
        end(closing);
        closing = null;
      }
      const now = {host: null, root: null, scroller: null, token, held: [], kept: {inert: null, overflow: []}};
      try {
        const host = apply(createElement, page, ['div']);
        now.host = host;
        const root = apply(attachShadow, host, [{mode: 'open'}]);
        now.root = root;
        for (let at = 0; at < INPUT.length; at++) apply(listen, root, [INPUT[at], onInput, {passive: true}]);
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
        apply(listen, scroller, ['click', onClick, false]);
        now.scroller = scroller;
        hold(now.held, now.kept);
        apply(appendChild, apply(rootOf, page, []), [host]);
        shown = now;
        apply(listen, window, ['keydown', onKeyDown, true]);
        apply(listen, window, ['keypress', onKey, true]);
        apply(listen, window, ['keyup', onKey, true]);
        apply(listen, window, ['pagehide', hide, true]);
        apply(focus, scroller, [{preventScroll: true}]);
      } catch (failed) {
        shown = null;
        close(now);
        end(now);
        report({op: 'reader', check: token, state: 'off'});
      }
    }

    /**
     * The overlay closes, and the page has its scrolling, keys, clicks and earlier focus again; for an overlay Escape
     * closed already, as the page goes, its listeners end.
     */
    function hide() {
      const now = shown;
      if (now !== null) {
        shown = null;
        close(now);
        end(now);
      } else if (closing !== null) {
        end(closing);
        closing = null;
      }
    }

    /** {@code now}'s overlay leaves the page, and what it changed there is undone. */
    function close(now) {
      try {
        if (now.host !== null) apply(remove, now.host, []);
      } catch (kept) {
        // An overlay the page took away is gone already.
      }
      restore(now.held);
    }

    /** {@code now}'s listeners end: the window's, and its shadow root's. */
    function end(now) {
      try {
        apply(unlisten, window, ['keydown', onKeyDown, true]);
        apply(unlisten, window, ['keypress', onKey, true]);
        apply(unlisten, window, ['keyup', onKey, true]);
        apply(unlisten, window, ['pagehide', hide, true]);
        if (now.root !== null) for (let at = 0; at < INPUT.length; at++) apply(unlisten, now.root, [INPUT[at], onInput, false]);
      } catch (kept) {
        // A listener the page took away is gone already.
      }
    }

    /**
     * A key the user pressed while the overlay shows stops here, its default action kept, so the page's own handlers
     * never hear it; Escape is prevented too, closes the overlay, and Jasper is told. After Escape closed it, until
     * Escape's keyup, each Escape keydown the user makes stops here prevented, its repeat never read, as JBR's keys may
     * not set it (ruling D47); other keys are the page's.
     */
    function onKeyDown(event) {
      if (event.isTrusted !== true) return;
      if (shown === null) {
        if (closing !== null && apply(keyOf, event, []) === 'Escape') {
          apply(stop, event, []);
          apply(prevent, event, []);
        }
        return;
      }
      apply(stop, event, []);
      if (apply(keyOf, event, []) !== 'Escape') return;
      apply(prevent, event, []);
      const now = shown;
      shown = null;
      close(now);
      closing = now;
      report({op: 'reader', check: now.token, state: 'off'});
    }

    /**
     * A keypress or keyup the user made while the overlay shows stops here, its default action kept. After Escape
     * closed the overlay, its keyup stops here too, and the overlay's listeners end with it.
     */
    function onKey(event) {
      if (event.isTrusted !== true) return;
      if (shown !== null) {
        apply(stop, event, []);
      } else if (closing !== null && apply(typeOf, event, []) === 'keyup' && apply(keyOf, event, []) === 'Escape') {
        apply(stop, event, []);
        end(closing);
        closing = null;
      }
    }

    /** A pointer, mouse, click or touch event in the overlay goes no further than its shadow root, its default kept. */
    function onInput(event) {
      apply(stopHere, event, []);
    }

    /** The overlay's shadow root while it shows, where find searches; null otherwise. */
    function root() {
      return shown === null ? null : shown.root;
    }

    /**
     * A plain click on a link to a fragment of this document, in the overlay, scrolls the overlay to the fragment's
     * target there, as the page behind would scroll to its own; any other click is the browser's.
     */
    function onClick(event) {
      const now = shown;
      if (now === null || event.isTrusted !== true || apply(buttonOf, event, []) !== 0) return;
      for (let at = 0; at < modifiers.length; at++) if (apply(modifiers[at], event, []) === true) return;
      const target = apply(targetOf, event, []);
      if (!(target instanceof Elements)) return;
      const link = apply(closest, target, ['a[href]']);
      if (!(link instanceof Anchors)) return;
      const fragment = fragmentOf(link);
      if (fragment === null) return;
      apply(prevent, event, []);
      scrollTo(now.scroller, fragment);
    }

    /** The fragment {@code link} goes to in this document, decoded; null for a link to another document. */
    function fragmentOf(link) {
      const address = apply(hrefOf, link, []), here = apply(urlOf, page, []);
      const hash = apply(indexOf, address, ['#']);
      if (hash < 0 || apply(slice, address, [0, hash]) !== withoutFragment(here)) return null;
      const fragment = apply(slice, address, [hash + 1]);
      try {
        return decode(fragment);
      } catch (undecodable) {
        return fragment;
      }
    }

    function withoutFragment(address) {
      const hash = apply(indexOf, address, ['#']);
      return hash < 0 ? address : apply(slice, address, [0, hash]);
    }

    /**
     * Scrolls the overlay to the element of the article whose id is {@code fragment}, else to the first link it names;
     * to the top for an empty fragment and for "top" with no such element; and not at all for a fragment it lacks.
     */
    function scrollTo(scroller, fragment) {
      const candidates = apply(querySelectorAll, scroller, ['[id], [name]']);
      let target = null;
      for (let at = 0; target === null && at < candidates.length; at++)
        if (apply(getAttribute, candidates[at], ['id']) === fragment) target = candidates[at];
      for (let at = 0; target === null && fragment !== '' && at < candidates.length; at++)
        if (candidates[at] instanceof Anchors && apply(getAttribute, candidates[at], ['name']) === fragment) target = candidates[at];
      if (target === null) {
        if (fragment === '' || apply(toLowerCase, fragment, []) === 'top') apply(scrollTop.set, scroller, [0]);
        return;
      }
      const offset = apply(boundsOf, target, []).top - apply(boundsOf, scroller, []).top;
      apply(scrollTop.set, scroller, [apply(scrollTop.get, scroller, []) + offset]);
    }

    /** {@code value} trimmed, or "Article" for none. */
    function title(value) {
      const text = typeof value === 'string' ? apply(trim, value, []) : '';
      return text === '' ? 'Article' : text;
    }

    /**
     * {@code content} without what would run or restyle anything: the dropped elements, custom elements, which give way
     * to their children, on* attributes and javascript: addresses.
     */
    function cleaned(content) {
      const dropped = apply(querySelectorAll, content, [DROPPED]);
      for (let at = 0; at < dropped.length; at++) apply(remove, dropped[at], []);
      const all = apply(querySelectorAll, content, ['*']);
      for (let at = 0; at < all.length; at++) if (custom(all[at])) unwrap(all[at]);
      attributes(content);
      const kept = apply(querySelectorAll, content, ['*']);
      for (let at = 0; at < kept.length; at++) attributes(kept[at]);
      return content;
    }

    /** Whether {@code element} is one a page's element definition could take: a name with a hyphen, or an is attribute. */
    function custom(element) {
      return apply(indexOf, apply(localNameOf, element, []), ['-']) >= 0 || apply(getAttribute, element, ['is']) !== null;
    }

    /** {@code element} gives way to its children, in its place. */
    function unwrap(element) {
      const children = apply(childrenOf, element, []), moved = [];
      for (let at = 0; at < children.length; at++) moved[moved.length] = children[at];
      apply(replaceWith, element, moved);
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
     * Holds the page while the overlay shows: its earlier focus kept, its own scrolling held on its root and body
     * elements, and its body inert. Each change goes into {@code held} with what undoes it before it is made, so a
     * failure part way undoes what was done, and what it changed goes into {@code kept}, frozen, for {@code held()}.
     */
    function hold(held, kept) {
      const earlier = apply(activeOf, page, []);
      held[held.length] = () => {
        if (earlier instanceof HtmlElements && apply(connectedOf, earlier, []) === true) apply(focus, earlier, [{preventScroll: true}]);
      };
      const elements = [apply(rootOf, page, []), apply(bodyOf, page, [])];
      for (let at = 0; at < elements.length; at++) {
        if (!(elements[at] instanceof HtmlElements)) continue;
        const style = apply(styleOf, elements[at], []);
        for (let each = 0; each < OVERFLOW.length; each++) {
          const name = OVERFLOW[each], value = apply(getPropertyValue, style, [name]), priority = apply(getPropertyPriority, style, [name]);
          held[held.length] = () => {
            if (value === '') apply(removeProperty, style, [name]);
            else apply(setProperty, style, [name, value, priority]);
          };
          kept.overflow[kept.overflow.length] = freeze({element: elements[at], name, value, priority});
          apply(setProperty, style, [name, 'hidden', 'important']);
        }
      }
      const body = elements[1];
      if (body instanceof HtmlElements) {
        const was = apply(inertOf.get, body, []);
        held[held.length] = () => apply(inertOf.set, body, [was]);
        kept.inert = freeze({element: body, value: was});
        apply(inertOf.set, body, [true]);
      }
    }

    /** Undoes what {@code hold} did, the last change first. */
    function restore(held) {
      for (let at = held.length - 1; at >= 0; at--) {
        try {
          held[at]();
        } catch (changed) {
          // What the page changed since keeps the page's.
        }
      }
    }

    /**
     * What the overlay holds of the page while it shows, read only, for Save Page As, which gives the page's copy its own
     * values back (ruling D42): the overlay's host, body's earlier inert and the root's and body's earlier overflow
     * longhands with their priorities; null while it does not show. A fresh frozen copy each time.
     */
    function held() {
      const now = shown;
      if (now === null) return null;
      const overflow = [];
      for (let at = 0; at < now.kept.overflow.length; at++) overflow[at] = now.kept.overflow[at];
      return freeze({host: now.host, inert: now.kept.inert, overflow: freeze(overflow)});
    }

    return freeze({check, show, hide, root, held});
  }
})
