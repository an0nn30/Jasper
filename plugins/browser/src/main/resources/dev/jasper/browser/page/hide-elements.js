/*
 * The Browser's element hiding, ad blocking's part in a page. Jasper runs this in each frame of a page but challenge
 * frames and Jasper's own pages, as each document starts loading, in the page's own world, called with the lines the
 * filter list hides in the document: one a line, each a selector and the one declaration {display:none!important}.
 *
 * - Each line is added through CSSStyleSheet.insertRule to a sheet of its own the script makes, which the document then
 *   adopts (document.adoptedStyleSheets), after the sheets the page adopted itself.
 * - The declaration is fixed: a line that ends in another, or whose selector holds a brace, is dropped unread, and so
 *   is one the browser cannot parse; the others are kept. A list can therefore hide elements and nothing else, and no
 *   rule of it loads anything, however hostile its selectors are.
 * - A content security policy governs style elements, not a sheet the script makes, so it hides on every page.
 *
 * The hiding is cooperative: the page's own scripts can take the sheet away, and the elements of a shadow root are not
 * the document's. The built-ins this calls are the ones found when it runs. Running it again in the same document does
 * nothing.
 */
(function (sheet) {
  'use strict';
  const KEY = Symbol.for('dev.jasper.browser.hidden');
  const DECLARATION = '{display:none!important}';
  // The built-ins this calls, as they are now.
  const apply = Reflect.apply, define = Object.defineProperty, describe = Object.getOwnPropertyDescriptor;
  const owns = Object.prototype.hasOwnProperty;
  const split = String.prototype.split, endsWith = String.prototype.endsWith, slice = String.prototype.slice,
    indexOf = String.prototype.indexOf;
  const Sheet = window.CSSStyleSheet;
  const Page = window.Document;
  if (typeof sheet !== 'string' || typeof Sheet !== 'function' || typeof Page !== 'function') return;
  const insertRule = Sheet.prototype.insertRule;
  const adopted = describe(Page.prototype, 'adoptedStyleSheets');
  if (typeof insertRule !== 'function' || !adopted || typeof adopted.get !== 'function' || typeof adopted.set !== 'function') return;
  const page = document;
  if (apply(owns, page, [KEY])) return;
  try {
    define(page, KEY, {value: true, writable: false, enumerable: false, configurable: false});
  } catch (taken) {
    return;
  }

  const hidden = new Sheet();
  const lines = apply(split, sheet, ['\n']);
  let count = 0;
  for (let at = 0; at < lines.length; at++) {
    const line = lines[at];
    if (!apply(endsWith, line, [DECLARATION])) continue;
    const selector = apply(slice, line, [0, line.length - DECLARATION.length]);
    if (selector === '' || apply(indexOf, selector, ['{']) >= 0 || apply(indexOf, selector, ['}']) >= 0) continue;
    try {
      apply(insertRule, hidden, [selector + DECLARATION, count]);
      count++;
    } catch (unparsed) {
      // A selector the browser cannot parse hides nothing; the other lines go on.
    }
  }
  if (count === 0) return;

  // The page's own sheets, then this one, copied as own properties: no setter the page put on arrays sees them.
  const current = apply(adopted.get, page, []);
  const next = [];
  let size = 0;
  for (; size < current.length; size++) define(next, size, {value: current[size], writable: true, enumerable: true, configurable: true});
  define(next, size, {value: hidden, writable: true, enumerable: true, configurable: true});
  apply(adopted.set, page, [next]);
})
