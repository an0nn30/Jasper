/*
 * The Browser's text insertion, behind Snippets in page fields: text another plugin inserts into a browser item goes
 * where the user is typing in its page. Jasper runs this function, called with the text, in an isolated world of the
 * page's main frame through DevTools (Runtime.evaluate), and reads back which of three things it returns (H38):
 * - 'inserted': the focused field took the text;
 * - 'unreachable': the focus is in a frame of another origin or a closed shadow root, which this world cannot enter, and
 *   Jasper types the text there with DevTools' Input.insertText, never the clipboard;
 * - 'not-editable': the focus takes no text, and Jasper does nothing more, Return included, as Return would press the
 *   page's control: nothing is focused, or the document's body is; a button, a link, a checkbox or any other element
 *   that takes no text; a field that is disabled or read-only; or a field whose document refused the text.
 * The isolated world shares the page's nodes but none of its scripts' globals, so no page script can replace
 * execCommand, a value setter or activeElement to steer the text or read it. The page still sees the input events the
 * insertion fires, as it sees typing.
 *
 * The focus is followed from the main frame's document into same-origin frames (any element with a document of its own:
 * an iframe, a frame or an object) and open shadow roots. An element whose document this frame cannot reach is a frame
 * of another origin. An element a shadow root can be attached to, a custom element or a div, a span, a section and the
 * like, that has no open shadow root and cannot take the focus itself, as it has no tabindex, is not editable and does
 * not scroll, is focused because the focus is inside its closed shadow root.
 *
 * The field is an <input> of a type that takes text (text, search, url, tel, email, number, or password, as a snippet
 * may be a user name or a code), a <textarea>, or an editable element (contenteditable, or a document in design mode),
 * and is neither disabled nor read-only. The text goes in as typing would: document.execCommand('insertText') replaces
 * the selection, fires beforeinput and input, and keeps the field's undo. Where the document refuses that command, an
 * <input> or <textarea> takes the text in its value, replacing its selection, or at its end when its type has no
 * selection, and an input event says so; but a number field, whose value text that is no number would empty, and an
 * editable element take nothing then.
 */
(function (text) {
  'use strict';
  const INSERTED = 'inserted', UNREACHABLE = 'unreachable', NOT_EDITABLE = 'not-editable';
  const TEXT_TYPES = new Set(['text', 'search', 'url', 'tel', 'email', 'number', 'password']);
  // The elements besides custom ones that a shadow root can be attached to; the body is left out, as a focus there is
  // taken for no focus at all.
  const HOSTS = new Set(['article', 'aside', 'blockquote', 'div', 'footer', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'header', 'main',
    'nav', 'p', 'section', 'span']);
  // Far more frames and shadow roots than a page nests; the walk ends there whatever a page does.
  const MAX_STEPS = 100;

  // The focused element, followed into same-origin frames and open shadow roots: null for none, or past the step bound;
  // UNREACHABLE for a frame of another origin.
  function focused() {
    let element = document.activeElement;
    for (let step = 0; element; step++) {
      if (step >= MAX_STEPS) return null;
      if (element.shadowRoot && element.shadowRoot.activeElement) {
        element = element.shadowRoot.activeElement;
      } else if ('contentDocument' in element) {
        const inner = element.contentDocument; // null for a frame of another origin
        if (!inner) return UNREACHABLE;
        element = inner.activeElement;
      } else {
        return element;
      }
    }
    return null;
  }

  function control(element) {
    return element.localName === 'textarea' || element.localName === 'input' && TEXT_TYPES.has(element.type);
  }

  // Whether the focus is inside element's closed shadow root: element could host one, has no open one, and cannot take
  // the focus itself. A scroll container can, so a scrolling element counts as focused itself.
  function closedHost(element) {
    const name = element.localName;
    if (!(name.includes('-') || HOSTS.has(name)) || element.shadowRoot || element.hasAttribute('tabindex')) return false;
    const view = element.ownerDocument.defaultView;
    if (!view) return false;
    const style = view.getComputedStyle(element);
    const scrolls = value => value === 'auto' || value === 'scroll' || value === 'overlay';
    return !scrolls(style.overflowX) && !scrolls(style.overflowY);
  }

  function typed(element) {
    try {
      return element.ownerDocument.execCommand('insertText', false, text) === true;
    } catch (refused) {
      return false;
    }
  }

  function intoValue(element) {
    const start = element.selectionStart, end = element.selectionEnd;
    if (typeof start === 'number' && typeof end === 'number') element.setRangeText(text, start, end, 'end');
    else element.value += text;
    element.dispatchEvent(new InputEvent('input', {bubbles: true, inputType: 'insertText', data: text}));
  }

  try {
    const element = focused();
    if (element === UNREACHABLE) return UNREACHABLE;
    if (!element) return NOT_EDITABLE;
    if (control(element)) {
      if (element.disabled || element.readOnly) return NOT_EDITABLE;
      if (typed(element)) return INSERTED;
      if (element.type === 'number') return NOT_EDITABLE;
      intoValue(element);
      return INSERTED;
    }
    if (element.isContentEditable === true) return typed(element) ? INSERTED : NOT_EDITABLE;
    return closedHost(element) ? UNREACHABLE : NOT_EDITABLE;
  } catch (failed) {
    return NOT_EDITABLE;
  }
})
