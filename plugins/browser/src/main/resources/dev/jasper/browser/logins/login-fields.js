/*
 * The Credential Vault's buttons on a page's login fields (plan 8). Jasper runs this in a page's top frame, in the
 * page's own world, as each http or https document starts loading, never in a sub-frame, a challenge frame or one of
 * Jasper's own pages, and only while the Credential Vault is present. In a document whose origin is opaque ("null"),
 * as a sandbox makes one, it does nothing, as Jasper would refuse its picks.
 *
 * - As the user focuses an enabled, editable, visible input (a trusted focusin), a button shows inside the field's
 *   right edge: a key (U+1F511) in a password field, and a ticket (U+1F3AB) in an input whose autocomplete tokens,
 *   compared ignoring case, include "username", iTerm2's rule. Visible means it has client rectangles, a rectangle of
 *   some size and a computed visibility other than hidden. The button, titled and labelled "Open Password Manager", is
 *   the one element of a closed shadow root on an element of the document's root element, outside its body, fixed and
 *   topmost, styled by a sheet the shadow root adopts, which no content security policy governs. It is the field's
 *   height less 4 px, at most 24 px, centred vertically, and it follows the field as the page scrolls or is resized,
 *   and again in the next animation frame. It goes as the focus moves to anything but the field or the button. A
 *   press, a release or a click on it stops at its shadow root, and its press is prevented, so the field keeps the
 *   focus and the page's bubbling listeners hear none of them.
 * - A trusted click on it asks Jasper, through jasperBridgeQuery, {op: "login.pick", field: "password" | "username",
 *   x, y, width, height}: the field's rectangle mapped through the visual viewport, as a pinch zoom moves it, in whole
 *   CSS pixels rounded outwards. Jasper opens the Credential Vault's picker under the field and answers what the user
 *   chose: {password} from a password field, {username, password} from a username field, each without an empty
 *   member, or {} for nothing. One pick waits at a time, and a reply fills only if its pick is still the one waiting;
 *   a failure, a busy page's included, is never asked again, as each pick needs its own click.
 * - A reply is a plain object whose only members are a string username and a string password, else it fills nothing.
 *   It fills only the field it was asked from, while that field is connected, still editable and, a password field,
 *   still one, and while the document's origin is the one it was asked from. Each value goes through the input's own
 *   value setter, then bubbling input and change events. From a username field, the username fills it, then the first
 *   enabled, editable password input after it in the document, in its form when it has one, takes the focus and the
 *   password. An empty value fills nothing.
 * - As the page scrolls the field, or the visual viewport scrolls or is resized, while a pick waits, the pick is
 *   cancelled (jasperBridgeCancel), which closes Jasper's picker, as the field it opened under has moved.
 *
 * It is made once per document, kept under window[Symbol.for('dev.jasper.browser.logins')], and calls the built-ins and
 * the bridge's functions as they were when it ran. It logs nothing and puts no value in an attribute. The page's own
 * scripts share this world, so they can see and undo all of it, and can call the bridge themselves: Jasper's Java side
 * decides what any of them gets.
 */
(function () {
  'use strict';
  const KEY = Symbol.for('dev.jasper.browser.logins');
  if (Reflect.apply(Object.prototype.hasOwnProperty, window, [KEY])) return;
  if (self.origin === 'null') return;
  const buttons = make();
  if (buttons === null) return;
  try {
    Object.defineProperty(window, KEY, {value: buttons.handle, writable: false, enumerable: false, configurable: false});
  } catch (taken) {
    return;
  }
  buttons.start();

  /** The buttons of this document, or null where the built-ins they need are missing. */
  function make() {
    const KEY_BUTTON = '\u{1F511}', TICKET_BUTTON = '\u{1F3AB}';
    const LABEL = 'Open Password Manager';
    // The largest a button is, and how far it sits from the field's right edge, in CSS pixels.
    const LARGEST = 24, INSET = 2;
    // The largest a request's coordinate or size may be, either way, as Jasper reads one.
    const LIMIT = 1000000;
    // compareDocumentPosition's bit for a node that follows.
    const FOLLOWING = 4;
    const SHEET = `
:host {
  all: initial !important; position: fixed !important; z-index: 2147483647 !important; left: 0 !important;
  top: 0 !important; width: 0 !important; height: 0 !important; overflow: visible !important;
}
button {
  all: initial; position: fixed; box-sizing: border-box; display: flex; align-items: center; justify-content: center;
  margin: 0; padding: 0; border: 0; border-radius: 4px; background: transparent; cursor: pointer; line-height: 1;
  user-select: none; -webkit-user-select: none;
  font-family: "Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", sans-serif;
}
button:hover { background: rgba(127, 127, 127, 0.25); }
`;

    const apply = Reflect.apply, describe = Object.getOwnPropertyDescriptor, freeze = Object.freeze, keysOf = Object.keys;
    const isArray = Array.isArray, isFinite = Number.isFinite, floor = Math.floor, ceil = Math.ceil, min = Math.min;
    const stringify = JSON.stringify, parse = JSON.parse, Json = JSON;
    const query = window.jasperBridgeQuery, cancel = window.jasperBridgeCancel, viewport = window.visualViewport;
    const frame = window.requestAnimationFrame, computed = window.getComputedStyle;
    // The page's interfaces, by their constructors; the visual viewport's is optional.
    const Sheets = window.CSSStyleSheet, ShadowRoots = window.ShadowRoot, Documents = window.Document, Elements = window.Element,
      HtmlElements = window.HTMLElement, Inputs = window.HTMLInputElement, Forms = window.HTMLFormElement, Nodes = window.Node,
      Targets = window.EventTarget, Events = window.Event, FocusEvents = window.FocusEvent, Styles = window.CSSStyleDeclaration,
      Viewports = window.VisualViewport;
    const constructors = [Sheets, ShadowRoots, Documents, Elements, HtmlElements, Inputs, Forms, Nodes, Targets, Events, FocusEvents,
      Styles];
    if (typeof query !== 'function' || typeof cancel !== 'function' || typeof frame !== 'function' || typeof computed !== 'function')
      return null;
    for (let at = 0; at < constructors.length; at++) if (typeof constructors[at] !== 'function') return null;
    // A getter, or a setter, of {@code type}'s prototype, as it is now; null for none.
    const getter = (type, name) => {
      const property = describe(type.prototype, name);
      return property && typeof property.get === 'function' ? property.get : null;
    };
    const setter = (type, name) => {
      const property = describe(type.prototype, name);
      return property && typeof property.set === 'function' ? property.set : null;
    };
    const replaceSync = Sheets.prototype.replaceSync, adopt = setter(ShadowRoots, 'adoptedStyleSheets');
    const createElement = Documents.prototype.createElement, querySelectorAll = Documents.prototype.querySelectorAll,
      rootOf = getter(Documents, 'documentElement');
    const attachShadow = Elements.prototype.attachShadow, boundsOf = Elements.prototype.getBoundingClientRect,
      rectsOf = Elements.prototype.getClientRects, setAttribute = Elements.prototype.setAttribute, remove = Elements.prototype.remove;
    const focus = HtmlElements.prototype.focus, styleOf = getter(HtmlElements, 'style');
    const valueTo = setter(Inputs, 'value'), typeOf = getter(Inputs, 'type'), disabledOf = getter(Inputs, 'disabled'),
      readOnlyOf = getter(Inputs, 'readOnly'), formOf = getter(Inputs, 'form'), autocompleteOf = getter(Inputs, 'autocomplete');
    const elementsOf = getter(Forms, 'elements');
    const appendChild = Nodes.prototype.appendChild, contains = Nodes.prototype.contains,
      compare = Nodes.prototype.compareDocumentPosition, connectedOf = getter(Nodes, 'isConnected'),
      textTo = setter(Nodes, 'textContent');
    const listen = Targets.prototype.addEventListener, dispatch = Targets.prototype.dispatchEvent;
    const stop = Events.prototype.stopPropagation, prevent = Events.prototype.preventDefault, targetOf = getter(Events, 'target'),
      relatedOf = getter(FocusEvents, 'relatedTarget');
    const setProperty = Styles.prototype.setProperty, getPropertyValue = Styles.prototype.getPropertyValue;
    const toLowerCase = String.prototype.toLowerCase;
    const place = location, originOf = describe(place, 'origin');
    const offsetLeftOf = typeof Viewports === 'function' ? getter(Viewports, 'offsetLeft') : null,
      offsetTopOf = typeof Viewports === 'function' ? getter(Viewports, 'offsetTop') : null,
      scaleOf = typeof Viewports === 'function' ? getter(Viewports, 'scale') : null;
    const page = document;
    const needed = [replaceSync, adopt, createElement, querySelectorAll, rootOf, attachShadow, boundsOf, rectsOf, setAttribute, remove,
      focus, styleOf, valueTo, typeOf, disabledOf, readOnlyOf, formOf, autocompleteOf, elementsOf, appendChild, contains, compare,
      connectedOf, textTo, listen, dispatch, stop, prevent, targetOf, relatedOf, setProperty, getPropertyValue];
    for (let at = 0; at < needed.length; at++) if (typeof needed[at] !== 'function') return null;
    if (!originOf || typeof originOf.get !== 'function') return null;
    // The visual viewport maps a field only with all three of its getters.
    const mapped = viewport !== null && typeof viewport === 'object' && typeof offsetLeftOf === 'function'
      && typeof offsetTopOf === 'function' && typeof scaleOf === 'function';
    // The white space autocomplete's tokens are split on.
    const WHITE = ' \t\n\f\r';

    // The button's element, its shadow root's one button and the sheet it adopts, made the first time one shows.
    let host = null, button = null;
    // The field the button shows in, and its kind, while it shows.
    let shown = null;
    // The pick waiting for Jasper's reply: its token, the field and its kind, the origin it was asked from, and the
    // bridge's id for its query, which a scroll cancels.
    let waiting = null;
    let picks = 0;
    // Whether a placement waits for the next animation frame.
    let framed = false;

    function start() {
      apply(listen, window, ['focusin', onFocusIn, true]);
      apply(listen, window, ['focusout', onFocusOut, true]);
      apply(listen, window, ['scroll', onScroll, {capture: true, passive: true}]);
      apply(listen, window, ['resize', onResize, {passive: true}]);
      if (mapped) {
        apply(listen, viewport, ['scroll', onViewportMoved, {passive: true}]);
        apply(listen, viewport, ['resize', onViewportMoved, {passive: true}]);
      }
    }

    /** The kind of button {@code field} shows: "password", "username", or null for none. */
    function kindOf(field) {
      if (!(field instanceof Inputs) || !editable(field) || !visible(field)) return null;
      if (apply(typeOf, field, []) === 'password') return 'password';
      return hasToken(apply(toLowerCase, apply(autocompleteOf, field, []), []), 'username') ? 'username' : null;
    }

    /** Whether {@code text}, split on white space, holds {@code wanted}. */
    function hasToken(text, wanted) {
      let token = '';
      for (let at = 0; at <= text.length; at++) {
        const c = at < text.length ? text[at] : ' ';
        if (WHITE.indexOf(c) < 0) {
          token += c;
          continue;
        }
        if (token === wanted) return true;
        token = '';
      }
      return false;
    }

    function editable(field) {
      return !apply(disabledOf, field, []) && !apply(readOnlyOf, field, []);
    }

    function visible(field) {
      if (apply(rectsOf, field, []).length === 0) return false;
      const bounds = apply(boundsOf, field, []);
      if (!(bounds.width > 0) || !(bounds.height > 0)) return false;
      return apply(getPropertyValue, apply(computed, window, [field]), ['visibility']) !== 'hidden';
    }

    function connected(node) {
      return apply(connectedOf, node, []) === true;
    }

    function origin() {
      return apply(originOf.get, place, []);
    }

    function onFocusIn(event) {
      try {
        if (!event.isTrusted) return;
        const target = apply(targetOf, event, []);
        if (host !== null && target === host) return;
        const kind = kindOf(target);
        if (kind === null) hide();
        else show(target, kind);
      } catch (failed) {
        hide();
      }
    }

    function onFocusOut(event) {
      try {
        if (!event.isTrusted || shown === null || apply(targetOf, event, []) !== shown.field) return;
        const next = apply(relatedOf, event, []);
        if (next !== shown.field && (host === null || next !== host)) hide();
      } catch (failed) {
        hide();
      }
    }

    /** The page or one of its elements scrolled: a pick waiting for a field it moved is cancelled; the button follows. */
    function onScroll(event) {
      try {
        if (waiting !== null) {
          const target = apply(targetOf, event, []);
          if (target === page || (target instanceof Nodes && apply(contains, target, [waiting.field]))) withdraw();
        }
      } catch (failed) {
        withdraw();
      }
      follow();
    }

    function onResize() {
      follow();
    }

    /** The visual viewport moved or was zoomed: the field moved on the screen, so a pick waiting for it is cancelled. */
    function onViewportMoved() {
      if (waiting !== null) withdraw();
    }

    /** The button follows its field now and in the next animation frame. */
    function follow() {
      if (shown === null) return;
      position();
      if (framed) return;
      framed = true;
      try {
        apply(frame, window, [() => {
          framed = false;
          position();
        }]);
      } catch (unscheduled) {
        framed = false;
      }
    }

    /** Shows the button for {@code field}, a field of {@code kind}, inside its right edge. */
    function show(field, kind) {
      if (!ready()) return;
      shown = {field, kind};
      apply(textTo, button, [kind === 'password' ? KEY_BUTTON : TICKET_BUTTON]);
      const root = apply(rootOf, page, []);
      if (root === null) {
        shown = null;
        return;
      }
      if (!connected(host)) apply(appendChild, root, [host]);
      position();
    }

    /** The button's host, shadow root, sheet and button, made once; false where they could not be. */
    function ready() {
      if (host !== null) return true;
      try {
        const made = apply(createElement, page, ['div']);
        const root = apply(attachShadow, made, [{mode: 'closed'}]);
        const sheet = new Sheets();
        apply(replaceSync, sheet, [SHEET]);
        apply(adopt, root, [[sheet]]);
        const control = apply(createElement, page, ['button']);
        apply(setAttribute, control, ['type', 'button']);
        apply(setAttribute, control, ['tabindex', '-1']);
        apply(setAttribute, control, ['title', LABEL]);
        apply(setAttribute, control, ['aria-label', LABEL]);
        apply(appendChild, root, [control]);
        apply(listen, root, ['pointerdown', onPress, false]);
        apply(listen, root, ['mousedown', onMouseDown, false]);
        apply(listen, root, ['mouseup', onPress, false]);
        apply(listen, root, ['click', onClick, false]);
        host = made;
        button = control;
        return true;
      } catch (failed) {
        return false;
      }
    }

    /** Places the button inside the right edge of the field it shows in, or hides it once the field cannot show it. */
    function position() {
      const now = shown;
      if (now === null) return;
      try {
        if (!connected(now.field) || !visible(now.field)) {
          hide();
          return;
        }
        const bounds = apply(boundsOf, now.field, []);
        const size = min(LARGEST, bounds.height - 4);
        const style = apply(styleOf, button, []);
        if (!(size > 0)) {
          apply(setProperty, style, ['display', 'none']);
          return;
        }
        apply(setProperty, style, ['display', 'flex']);
        apply(setProperty, style, ['left', (bounds.right - size - INSET) + 'px']);
        apply(setProperty, style, ['top', (bounds.top + (bounds.height - size) / 2) + 'px']);
        apply(setProperty, style, ['width', size + 'px']);
        apply(setProperty, style, ['height', size + 'px']);
        apply(setProperty, style, ['font-size', floor(size * 0.75) + 'px']);
      } catch (failed) {
        hide();
      }
    }

    /** The button goes; a pick waiting goes on. */
    function hide() {
      shown = null;
      if (host === null) return;
      try {
        apply(remove, host, []);
      } catch (kept) {
        // A host the page took away is gone already.
      }
    }

    /** A press, a release or a click on the button stops at its shadow root; a press keeps the focus in the field. */
    function onPress(event) {
      apply(stop, event, []);
    }

    function onMouseDown(event) {
      apply(stop, event, []);
      apply(prevent, event, []);
    }

    function onClick(event) {
      apply(stop, event, []);
      if (event.isTrusted) pick();
    }

    /**
     * Asks Jasper for a login for the field the button shows in, unless a pick waits already; nothing for a field whose
     * rectangle Jasper would refuse.
     */
    function pick() {
      const now = shown;
      if (now === null || waiting !== null) return;
      let request = null;
      try {
        request = requestFor(now, apply(boundsOf, now.field, []));
      } catch (unmeasured) {
        return;
      }
      if (request === null) return;
      const token = ++picks;
      const asked = {token, field: now.field, kind: now.kind, origin: origin(), id: null};
      waiting = asked;
      try {
        const id = apply(query, window, [{request: apply(stringify, Json, [request]), persistent: false,
          onSuccess(answer) {
            answered(token, answer);
          },
          onFailure() {
            if (waiting !== null && waiting.token === token) waiting = null;
          }}]);
        if (waiting === asked) asked.id = id;
      } catch (unsent) {
        if (waiting === asked) waiting = null;
      }
    }

    /**
     * The request for a pick from {@code now}'s field at {@code bounds}: its rectangle mapped through the visual
     * viewport, in whole CSS pixels rounded outwards; null for one Jasper would refuse.
     */
    function requestFor(now, bounds) {
      let left = bounds.left, top = bounds.top, right = bounds.right, bottom = bounds.bottom;
      if (mapped) {
        const offsetLeft = apply(offsetLeftOf, viewport, []), offsetTop = apply(offsetTopOf, viewport, []),
          scale = apply(scaleOf, viewport, []);
        if (!isFinite(offsetLeft) || !isFinite(offsetTop) || !isFinite(scale) || !(scale > 0)) return null;
        left = (left - offsetLeft) * scale;
        right = (right - offsetLeft) * scale;
        top = (top - offsetTop) * scale;
        bottom = (bottom - offsetTop) * scale;
      }
      const x = floor(left), y = floor(top);
      const width = ceil(right) - x, height = ceil(bottom) - y;
      const values = [x, y, width, height];
      for (let at = 0; at < values.length; at++)
        if (!isFinite(values[at]) || values[at] < -LIMIT || values[at] > LIMIT) return null;
      if (width < 0 || height < 0) return null;
      return {op: 'login.pick', field: now.kind, x, y, width, height};
    }

    /** The pick waiting is cancelled in Jasper, which closes its picker; its reply, if one still comes, fills nothing. */
    function withdraw() {
      const now = waiting;
      if (now === null) return;
      waiting = null;
      if (typeof now.id !== 'number') return;
      try {
        apply(cancel, window, [now.id]);
      } catch (uncancelled) {
        // Jasper's picker stays until the user closes it.
      }
    }

    /** Jasper's reply to the pick {@code token}: it fills only while that pick is the one waiting. */
    function answered(token, answer) {
      const now = waiting;
      if (now === null || now.token !== token) return;
      waiting = null;
      try {
        const login = loginOf(answer);
        if (login !== null) fill(now, login);
      } catch (unfilled) {
        // The page goes on.
      }
    }

    /**
     * The login in {@code answer}: a plain object whose only members are a string username and a string password; null
     * for anything else.
     */
    function loginOf(answer) {
      if (typeof answer !== 'string') return null;
      const value = apply(parse, Json, [answer]);
      if (value === null || typeof value !== 'object' || isArray(value)) return null;
      const names = keysOf(value);
      const login = {username: '', password: ''};
      for (let at = 0; at < names.length; at++) {
        const name = names[at];
        if (name !== 'username' && name !== 'password') return null;
        const member = describe(value, name);
        if (!member || typeof member.value !== 'string') return null;
        login[name] = member.value;
      }
      return freeze(login);
    }

    /** Fills {@code login} into the pick's field, and from a username field, its password into the next. */
    function fill(now, login) {
      if (origin() !== now.origin) return;
      if (now.kind === 'password') {
        if (login.password !== '' && fillable(now.field) && apply(typeOf, now.field, []) === 'password') set(now.field, login.password);
        return;
      }
      if (login.username !== '' && fillable(now.field)) set(now.field, login.username);
      if (login.password === '') return;
      const next = passwordAfter(now.field);
      if (next === null) return;
      apply(focus, next, []);
      set(next, login.password);
    }

    function fillable(field) {
      return connected(field) && editable(field);
    }

    /**
     * The first enabled, editable password input after {@code field} in the document, in its form when it has one; null
     * for none.
     */
    function passwordAfter(field) {
      const form = apply(formOf, field, []);
      const candidates = form !== null ? apply(elementsOf, form, []) : apply(querySelectorAll, page, ['input']);
      for (let at = 0; at < candidates.length; at++) {
        const candidate = candidates[at];
        if (candidate === field || !(candidate instanceof Inputs)) continue;
        if ((apply(compare, field, [candidate]) & FOLLOWING) === 0) continue;
        if (apply(typeOf, candidate, []) === 'password' && fillable(candidate)) return candidate;
      }
      return null;
    }

    /** {@code value} goes into {@code field} through the input's own setter, then bubbling input and change events. */
    function set(field, value) {
      apply(valueTo, field, [value]);
      apply(dispatch, field, [new Events('input', {bubbles: true})]);
      apply(dispatch, field, [new Events('change', {bubbles: true})]);
    }

    return {handle: freeze({}), start};
  }
})();
