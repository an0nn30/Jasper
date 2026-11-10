/*
 * The Browser's distraction removal and Remove Element (spec section 10). Jasper runs this in a page's top frame, in
 * the page's own world, never in a sub-frame, a challenge frame or one of Jasper's own pages. It is called with a
 * command and what the command takes:
 *
 * - "start", with the token Jasper's report carries back: the pointer is a crosshair over the whole page, through a
 *   sheet the script makes, which the document adopts after its own (document.adoptedStyleSheets) and which no content
 *   security policy governs; the same sheet keeps the pointer out of the page's frames, so over a frame it is over the
 *   element that holds the frame, whose overlay a click hides, and the frame's own document hears none of it. The
 *   overlay under the pointer is outlined in red, through its own inline outline, set with the important priority,
 *   whose earlier values come back as the pointer moves on. A press, release or click of the pointer stops at the
 *   window's capture listeners and is prevented, so neither the page's own handlers nor a link hears it, except the
 *   secondary button's, whose right-click menu goes on; a click of the primary button hides the overlay under it and
 *   its backdrops. Escape, pressed by the user, is stopped and prevented, ends it, and Jasper is told, through
 *   jasperBridgeQuery (op "distractions"), {check: token, state: "off"}, as for a start that fails. A second start
 *   while it runs only takes the new token. As the page goes, it ends untold. A capture listener the page added to the
 *   window earlier still hears the pointer and the keys first.
 * - "stop": the crosshair, the outline and the listeners go, untold. What was hidden stays hidden.
 * - "removeAt", with a point in CSS pixels from the viewport's top left: the overlay of the element there
 *   (document.elementFromPoint) is hidden, or nothing.
 *
 * An element's overlay is its outermost ancestor-or-self below the body whose computed position is fixed or sticky,
 * else the element itself, unless it is the root or the body element, which have none; the reader's overlay
 * (reader.js), while it shows, is never one. An overlay's backdrops are the other fixed elements of the body, of its
 * first 50,000 elements, that cover at least 90% of the viewport, neither inside the overlay nor around it. Hiding is
 * an inline display: none with the important priority, which stays.
 *
 * It is made once per document, kept under window[Symbol.for('dev.jasper.browser.distractions')], and calls the
 * built-ins as they were when it was made. The page's own scripts share this world, so they can see and undo all of
 * it, and what they send Jasper is untrusted.
 */
(function (command, first, second) {
  'use strict';
  const KEY = Symbol.for('dev.jasper.browser.distractions');
  let tools = null;
  if (Reflect.apply(Object.prototype.hasOwnProperty, window, [KEY])) {
    tools = window[KEY];
  } else {
    tools = make();
    if (tools === null) return;
    try {
      Object.defineProperty(window, KEY, {value: tools, writable: false, enumerable: false, configurable: false});
    } catch (taken) {
      return;
    }
  }
  if (tools === null || typeof tools !== 'object') return;
  if (command === 'start') tools.start(first);
  else if (command === 'stop') tools.stop();
  else if (command === 'removeAt') tools.removeAt(first, second);

  /** Distraction removal in this document, or null where the built-ins it needs are missing. */
  function make() {
    const READER = Symbol.for('dev.jasper.browser.reader');
    // The pointer over everything while removal runs, on the sheet the document adopts, and never inside a frame.
    const CROSSHAIR = '*{cursor:crosshair!important}';
    const FRAMES = 'iframe,frame,object,embed{pointer-events:none!important}';
    const OUTLINE = '2px solid #FF0000';
    // The outline's longhands, whose inline values and priorities the red one replaces and gives back.
    const OUTLINES = ['outline-color', 'outline-style', 'outline-width'];
    // How much of the viewport a fixed element covers to be a backdrop.
    const COVERING = 0.9;
    // The most of the body's elements read for backdrops, as reader mode's limit.
    const LIMIT = 50000;
    // The pointer's primary button, and its secondary one, whose press and click open the right-click menu.
    const PRIMARY = 0, SECONDARY = 2;

    const apply = Reflect.apply, describe = Object.getOwnPropertyDescriptor, prototypeOf = Object.getPrototypeOf,
      define = Object.defineProperty, freeze = Object.freeze, max = Math.max, min = Math.min;
    const isSafeInteger = Number.isSafeInteger, isFinite = Number.isFinite, stringify = JSON.stringify, Json = JSON;
    const query = window.jasperBridgeQuery, computed = window.getComputedStyle;
    // The page's interfaces, by their constructors; SVG's is optional.
    const Sheets = window.CSSStyleSheet, Documents = window.Document, Elements = window.Element, HtmlElements = window.HTMLElement,
      SvgElements = window.SVGElement, Nodes = window.Node, ShadowRoots = window.ShadowRoot, Targets = window.EventTarget,
      Events = window.Event, KeyEvents = window.KeyboardEvent, MouseEvents = window.MouseEvent, Styles = window.CSSStyleDeclaration;
    const constructors = [Sheets, Documents, Elements, HtmlElements, Nodes, ShadowRoots, Targets, Events, KeyEvents, MouseEvents, Styles];
    if (typeof query !== 'function' || typeof computed !== 'function') return null;
    for (let at = 0; at < constructors.length; at++) if (typeof constructors[at] !== 'function') return null;
    // A getter of {@code type}'s prototype, as it is now; null for none.
    const getter = (type, name) => {
      const property = describe(type.prototype, name);
      return property && typeof property.get === 'function' ? property.get : null;
    };
    // The getter of {@code object}'s own or inherited property {@code name}, as it is now; null for none.
    const accessor = (object, name) => {
      for (let at = object; at !== null; at = prototypeOf(at)) {
        const property = describe(at, name);
        if (property) return typeof property.get === 'function' ? property.get : null;
      }
      return null;
    };
    const replaceSync = Sheets.prototype.replaceSync, adopted = describe(Documents.prototype, 'adoptedStyleSheets');
    const elementFromPoint = Documents.prototype.elementFromPoint, rootOf = getter(Documents, 'documentElement'),
      bodyOf = getter(Documents, 'body');
    const querySelectorAll = Elements.prototype.querySelectorAll, boundsOf = Elements.prototype.getBoundingClientRect;
    const parentOf = getter(Nodes, 'parentElement'), contains = Nodes.prototype.contains, hostOf = getter(ShadowRoots, 'host');
    const styleOf = getter(HtmlElements, 'style'), svgStyleOf = typeof SvgElements === 'function' ? getter(SvgElements, 'style') : null;
    // The viewport's size: the window's own getters, or, where the page replaced them, what it put there.
    const widthOf = accessor(window, 'innerWidth'), heightOf = accessor(window, 'innerHeight');
    const listen = Targets.prototype.addEventListener, unlisten = Targets.prototype.removeEventListener;
    const halt = Events.prototype.stopImmediatePropagation, prevent = Events.prototype.preventDefault,
      targetOf = getter(Events, 'target');
    const keyOf = getter(KeyEvents, 'key'), buttonOf = getter(MouseEvents, 'button');
    const setProperty = Styles.prototype.setProperty, removeProperty = Styles.prototype.removeProperty,
      getPropertyValue = Styles.prototype.getPropertyValue, getPropertyPriority = Styles.prototype.getPropertyPriority;
    const page = document;
    const needed = [replaceSync, elementFromPoint, rootOf, bodyOf, querySelectorAll, boundsOf, parentOf, contains, hostOf, styleOf,
      listen, unlisten, halt, prevent, targetOf, keyOf, buttonOf, setProperty, removeProperty, getPropertyValue, getPropertyPriority];
    for (let at = 0; at < needed.length; at++) if (typeof needed[at] !== 'function') return null;
    if (!adopted || typeof adopted.get !== 'function' || typeof adopted.set !== 'function') return null;

    // While removal runs: the token its Escape reports, the sheet the document adopted, and the outlined overlay with
    // its inline style and its outline's earlier values.
    let running = null;

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

    /** Distraction removal starts, and its Escape will report {@code token}; while it runs, it takes the new token. */
    function start(token) {
      if (!valid(token)) return;
      if (running !== null) {
        running.token = token;
        return;
      }
      const now = {token, sheet: null, outlined: null};
      try {
        const sheet = new Sheets();
        apply(replaceSync, sheet, [CROSSHAIR + '\n' + FRAMES]);
        adopt(sheet);
        now.sheet = sheet;
        running = now;
        listeners(listen);
      } catch (failed) {
        running = null;
        end(now);
        report({op: 'distractions', check: token, state: 'off'});
      }
    }

    /** Distraction removal ends, untold; what it hid stays hidden. */
    function stop() {
      const now = running;
      if (now === null) return;
      running = null;
      end(now);
    }

    /** {@code now}'s listeners, outline and sheet go. */
    function end(now) {
      try {
        listeners(unlisten);
      } catch (kept) {
        // A listener the page took away is gone already.
      }
      unoutline(now);
      if (now.sheet !== null) unadopt(now.sheet);
    }

    /** The window's capture listeners, added or removed with {@code method}. */
    function listeners(method) {
      apply(method, window, ['pointerover', onOver, true]);
      apply(method, window, ['pointerdown', onPress, true]);
      apply(method, window, ['pointerup', onPress, true]);
      apply(method, window, ['mousedown', onPress, true]);
      apply(method, window, ['mouseup', onPress, true]);
      apply(method, window, ['auxclick', onPress, true]);
      apply(method, window, ['dblclick', onPress, true]);
      apply(method, window, ['click', onClick, true]);
      apply(method, window, ['keydown', onKeyDown, true]);
      apply(method, window, ['pagehide', onPageHide, true]);
    }

    /** Hides the overlay of the element at {@code x}, {@code y} in CSS pixels of the viewport, or nothing. */
    function removeAt(x, y) {
      if (typeof x !== 'number' || typeof y !== 'number' || !isFinite(x) || !isFinite(y)) return;
      let element = null;
      try {
        element = apply(elementFromPoint, page, [x, y]);
      } catch (unread) {
        return;
      }
      const overlay = overlayOf(element);
      if (overlay !== null) hide(overlay);
    }

    /** The pointer came over an element: its overlay is outlined, and the one before has its own outline again. */
    function onOver(event) {
      if (running === null || event.isTrusted !== true) return;
      outline(overlayOf(apply(targetOf, event, [])));
    }

    /** A press, release, double click or other button's click stops here, prevented, but the secondary button's. */
    function onPress(event) {
      if (running === null || event.isTrusted !== true || apply(buttonOf, event, []) === SECONDARY) return;
      apply(halt, event, []);
      apply(prevent, event, []);
    }

    /** A click stops here, prevented, but the secondary button's; the primary one's hides the overlay and its backdrops. */
    function onClick(event) {
      if (running === null || event.isTrusted !== true) return;
      const button = apply(buttonOf, event, []);
      if (button === SECONDARY) return;
      apply(halt, event, []);
      apply(prevent, event, []);
      if (button !== PRIMARY) return;
      const overlay = overlayOf(apply(targetOf, event, []));
      if (overlay === null) return;
      const backdrops = backdropsOf(overlay);
      hide(overlay);
      for (let at = 0; at < backdrops.length; at++) hide(backdrops[at]);
    }

    /** Escape, pressed by the user, is stopped and prevented, ends removal, and Jasper is told. */
    function onKeyDown(event) {
      const now = running;
      if (now === null || event.isTrusted !== true || apply(keyOf, event, []) !== 'Escape') return;
      apply(halt, event, []);
      apply(prevent, event, []);
      stop();
      report({op: 'distractions', check: now.token, state: 'off'});
    }

    /** The page goes: removal ends, untold, as Jasper ends it with the document. */
    function onPageHide() {
      stop();
    }

    /**
     * The overlay of {@code element}: its outermost ancestor-or-self below the body whose computed position is fixed or
     * sticky, else the element itself; null for the root and the body element, for no element, and for the reader's
     * overlay.
     */
    function overlayOf(element) {
      if (!(element instanceof Elements)) return null;
      const root = apply(rootOf, page, []), body = apply(bodyOf, page, []);
      if (element === root || element === body) return null;
      let overlay = element;
      for (let node = element; node instanceof Elements && node !== body && node !== root; node = apply(parentOf, node, [])) {
        const position = positionOf(node);
        if (position === 'fixed' || position === 'sticky') overlay = node;
      }
      return overlay === readerHost() ? null : overlay;
    }

    /** The other fixed elements of the body that cover at least 90% of the viewport, neither inside {@code overlay} nor around it. */
    function backdropsOf(overlay) {
      const found = [];
      try {
        const body = apply(bodyOf, page, []);
        const width = widthOf !== null ? apply(widthOf, window, []) : window.innerWidth;
        const height = heightOf !== null ? apply(heightOf, window, []) : window.innerHeight;
        if (!(body instanceof Elements) || typeof width !== 'number' || typeof height !== 'number' || !(width > 0) || !(height > 0))
          return found;
        const all = apply(querySelectorAll, body, ['*']);
        const count = min(all.length, LIMIT);
        for (let at = 0; at < count; at++) {
          const element = all[at];
          if (positionOf(element) !== 'fixed' || apply(contains, overlay, [element]) || apply(contains, element, [overlay])) continue;
          if (covered(apply(boundsOf, element, []), width, height) >= COVERING) found[found.length] = element;
        }
      } catch (unread) {
        // The backdrops found so far.
      }
      return found;
    }

    /** How much of the viewport, {@code width} by {@code height} from its top left, {@code bounds} covers: 0 to 1. */
    function covered(bounds, width, height) {
      const left = max(bounds.left, 0), top = max(bounds.top, 0), right = min(bounds.right, width), bottom = min(bounds.bottom, height);
      return right > left && bottom > top ? ((right - left) * (bottom - top)) / (width * height) : 0;
    }

    /** {@code element}'s computed position; empty when it cannot be read. */
    function positionOf(element) {
      try {
        return apply(getPropertyValue, apply(computed, window, [element]), ['position']);
      } catch (unread) {
        return '';
      }
    }

    /** The reader's overlay element while it shows, as reader.js tells it; null otherwise. */
    function readerHost() {
      try {
        const reader = window[READER];
        const root = reader !== null && typeof reader === 'object' && typeof reader.root === 'function' ? reader.root() : null;
        return root instanceof ShadowRoots ? apply(hostOf, root, []) : null;
      } catch (unread) {
        return null;
      }
    }

    /** {@code element}'s inline style, an HTML or SVG element's; null for another. */
    function inline(element) {
      try {
        if (element instanceof HtmlElements) return apply(styleOf, element, []);
        if (svgStyleOf !== null && element instanceof SvgElements) return apply(svgStyleOf, element, []);
      } catch (unread) {
        // No style to set.
      }
      return null;
    }

    /** Hides {@code element}, inline and important, its red outline taken off first. */
    function hide(element) {
      const now = running;
      if (now !== null && now.outlined !== null && now.outlined.element === element) unoutline(now);
      const style = inline(element);
      if (style === null) return;
      try {
        apply(setProperty, style, ['display', 'none', 'important']);
      } catch (refused) {
        // An element the page will not let go of stays.
      }
    }

    /** Outlines {@code overlay} in red, after the outlined one before has its own outline again; null outlines nothing. */
    function outline(overlay) {
      const now = running;
      if (now.outlined !== null && now.outlined.element === overlay) return;
      unoutline(now);
      const style = overlay === null ? null : inline(overlay);
      if (style === null) return;
      try {
        const earlier = [];
        for (let at = 0; at < OUTLINES.length; at++)
          earlier[at] = {value: apply(getPropertyValue, style, [OUTLINES[at]]), priority: apply(getPropertyPriority, style, [OUTLINES[at]])};
        now.outlined = {element: overlay, style, earlier};
        apply(setProperty, style, ['outline', OUTLINE, 'important']);
      } catch (refused) {
        unoutline(now);
      }
    }

    /** The outlined overlay of {@code now} has its own outline again: each longhand's earlier value and priority. */
    function unoutline(now) {
      const outlined = now.outlined;
      if (outlined === null) return;
      now.outlined = null;
      for (let at = 0; at < outlined.earlier.length; at++) {
        const earlier = outlined.earlier[at];
        try {
          if (earlier.value === '') apply(removeProperty, outlined.style, [OUTLINES[at]]);
          else apply(setProperty, outlined.style, [OUTLINES[at], earlier.value, earlier.priority]);
        } catch (changed) {
          // What the page changed since keeps the page's.
        }
      }
    }

    /** The document adopts {@code sheet} after its own sheets, copied as own properties: no setter the page put on arrays sees them. */
    function adopt(sheet) {
      const current = apply(adopted.get, page, []);
      const next = [];
      let size = 0;
      for (; size < current.length; size++) define(next, size, {value: current[size], writable: true, enumerable: true, configurable: true});
      define(next, size, {value: sheet, writable: true, enumerable: true, configurable: true});
      apply(adopted.set, page, [next]);
    }

    /** The document's adopted sheets without {@code sheet}; the page's own, and any it added since, stay. */
    function unadopt(sheet) {
      try {
        const current = apply(adopted.get, page, []);
        const next = [];
        let size = 0;
        for (let at = 0; at < current.length; at++)
          if (current[at] !== sheet) define(next, size++, {value: current[at], writable: true, enumerable: true, configurable: true});
        if (size !== current.length) apply(adopted.set, page, [next]);
      } catch (kept) {
        // A sheet the page took away is gone already.
      }
    }

    return freeze({start, stop, removeAt});
  }
})
