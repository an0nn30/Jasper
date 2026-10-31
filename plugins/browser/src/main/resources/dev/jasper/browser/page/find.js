/*
 * The Browser's find in page. Chromium's own find takes no regular expressions, so Jasper runs this in each frame of a
 * page as its find bar asks: this function, called with one command. A command's search is the id Jasper gave it, and
 * each answer goes to Jasper through jasperBridgeQuery, op "find".
 *
 * - {action: "search", search, query, matchCase, regex} finds the query, at most 1,000 characters, in the frame's
 *   visible text, one text node at a time: as plain text, or as a JavaScript regular expression (RegExp, "i" added
 *   when case does not matter). It highlights every match, at most 10,000, in #FFFF00 with the CSS Custom Highlight
 *   API, which leaves the page's nodes and scripts alone, and answers {search, count, invalid}. Until the next search
 *   or clear, a change to the document's nodes or text runs the search again, at most every 300 ms, and answers again
 *   when the count changes; the current match stays current, or the last one does when there are fewer.
 * - {action: "select", search, index} makes match index the current one, in #FF9632: it opens the closed details,
 *   hidden="until-found" elements and regions of collapsed aria-expanded controls that hide it, and scrolls it into
 *   view. -1 makes none current.
 * - {action: "selection", search} answers {search, selection}: the frame's selected text when it is one line of at
 *   most 1,000 characters, else "".
 * - {action: "clear"} removes every highlight and the style this script added, and ends the search. A document that is
 *   hidden, as when it goes into the back-forward cache, does the same.
 *
 * Text counts as visible when it shows, or when only those three hide it. Text in scripts, styles, form fields and
 * shadow roots is not searched.
 */
(function (command) {
  'use strict';
  const KEY = Symbol.for('dev.jasper.browser.find');
  let find = window[KEY];
  if (!find) {
    if (command.action === 'select' || command.action === 'clear') return;
    find = make();
    try {
      Object.defineProperty(window, KEY, {value: find});
    } catch (taken) {
      return;
    }
  }
  find.run(command);

  function make() {
    const LIMIT = 1000, MATCHES = 10000, QUIET = 300;
    // Jasper takes only a few of a page's requests at a time, and refuses the rest as busy: an answer then waits its turn.
    const BUSY = 5, TRIES = 6;
    const ALL = 'jasper-find', CURRENT = 'jasper-find-current';
    const UNSEARCHED = 'script, style, noscript, textarea, select';
    const query = window.jasperBridgeQuery;
    const sheet = new CSSStyleSheet();
    sheet.replaceSync('::highlight(' + ALL + ') { background-color: #FFFF00; color: #000000; }\n'
      + '::highlight(' + CURRENT + ') { background-color: #FF9632; color: #000000; }');
    const all = new Highlight(), now = new Highlight();
    now.priority = 1;
    let search = 0, pattern = null, ranges = [], current = -1, observer = null, timer = 0;
    window.addEventListener('pagehide', end);

    function tell(answer, tries) {
      if (typeof query !== 'function') return;
      query({
        request: JSON.stringify(Object.assign({op: 'find'}, answer)),
        persistent: false,
        onSuccess() {},
        onFailure(code) {
          if (code === BUSY && tries + 1 < TRIES) setTimeout(() => tell(answer, tries + 1), 50 * (tries + 1));
        }
      });
    }

    // The regions a collapsed aria-expanded control names, by id, with the control.
    function collapsed() {
      const named = new Map();
      for (const control of document.querySelectorAll('[aria-expanded="false"][aria-controls]')) {
        for (const id of control.getAttribute('aria-controls').split(/\s+/)) if (id !== '' && !named.has(id)) named.set(id, control);
      }
      return named;
    }

    // Whether find opens element: a closed details, a hidden="until-found" element, or a collapsed control's region.
    function opens(element, named) {
      return element.localName === 'details' && !element.open || element.getAttribute('hidden') === 'until-found'
        || element.id !== '' && named.has(element.id);
    }

    // Whether the text of element counts: it shows, or only what find opens hides it.
    function counts(element, named) {
      if (element.closest(UNSEARCHED) !== null) return false;
      for (let at = element; at !== null;) {
        if (at.checkVisibility({visibilityProperty: true})) return true;
        let hider = at;
        while (hider !== null && !opens(hider, named)) {
          if (getComputedStyle(hider).display === 'none') return false;
          hider = hider.parentElement;
        }
        if (hider === null) return false;
        at = hider.parentElement;
      }
      return false;
    }

    function matches() {
      const found = [], named = collapsed();
      const walker = document.createTreeWalker(document, NodeFilter.SHOW_TEXT);
      for (let node = walker.nextNode(); node !== null && found.length < MATCHES; node = walker.nextNode()) {
        const text = node.nodeValue, parent = node.parentElement;
        if (!text || parent === null) continue;
        pattern.lastIndex = 0;
        let hit, checked = false;
        while (found.length < MATCHES && (hit = pattern.exec(text)) !== null) {
          if (hit[0] === '') {
            pattern.lastIndex++;
            continue;
          }
          if (!checked) {
            if (!counts(parent, named)) break;
            checked = true;
          }
          const range = document.createRange();
          range.setStart(node, hit.index);
          range.setEnd(node, hit.index + hit[0].length);
          found.push(range);
        }
      }
      return found;
    }

    function paint() {
      all.clear();
      for (const range of ranges) all.add(range);
      now.clear();
      if (current >= 0) now.add(ranges[current]);
      if (CSS.highlights.get(ALL) !== all) CSS.highlights.set(ALL, all);
      if (CSS.highlights.get(CURRENT) !== now) CSS.highlights.set(CURRENT, now);
      if (!document.adoptedStyleSheets.includes(sheet)) document.adoptedStyleSheets = [...document.adoptedStyleSheets, sheet];
    }

    function end() {
      if (observer !== null) observer.disconnect();
      clearTimeout(timer);
      observer = null;
      timer = 0;
      search = 0;
      pattern = null;
      ranges = [];
      current = -1;
      all.clear();
      now.clear();
      if (CSS.highlights.get(ALL) === all) CSS.highlights.delete(ALL);
      if (CSS.highlights.get(CURRENT) === now) CSS.highlights.delete(CURRENT);
      if (document.adoptedStyleSheets.includes(sheet)) document.adoptedStyleSheets = document.adoptedStyleSheets.filter(each => each !== sheet);
    }

    function changed() {
      if (timer === 0) timer = setTimeout(again, QUIET);
    }

    function again() {
      timer = 0;
      if (pattern === null) return;
      const before = ranges.length;
      ranges = matches();
      if (current >= ranges.length) current = ranges.length - 1;
      paint();
      if (ranges.length !== before) tell({search, count: ranges.length, invalid: false}, 0);
    }

    // Opens what hides the range, outermost first: text right inside a closed details is hidden though the details shows.
    // A collapsed control is clicked only while its region is hidden, and only when that cannot follow a link or send a
    // form.
    function reveal(range) {
      const element = range.startContainer.parentElement;
      if (element === null) return;
      const loose = element.localName === 'details' && !element.open;
      if (!loose && element.checkVisibility({visibilityProperty: true})) return;
      const named = collapsed(), hiders = [];
      for (let at = element; at !== null; at = at.parentElement) if (opens(at, named)) hiders.unshift(at);
      for (const hider of hiders) {
        if (hider.localName === 'details' && !hider.open) {
          hider.open = true;
        } else if (hider.getAttribute('hidden') === 'until-found') {
          hider.dispatchEvent(new Event('beforematch', {bubbles: true}));
          hider.removeAttribute('hidden');
        } else if (!hider.checkVisibility({visibilityProperty: true})) {
          const control = named.get(hider.id);
          const sends = control.closest('a[href]') !== null || control.form && (control.type === 'submit' || control.type === 'image');
          if (control.getAttribute('aria-expanded') === 'false' && !sends) control.click();
        }
      }
    }

    function show(range) {
      const element = range.startContainer.parentElement;
      if (element === null) return;
      element.scrollIntoView({behavior: 'instant', block: 'center', inline: 'nearest'});
      const box = range.getBoundingClientRect();
      if (box.top < 0 || box.bottom > window.innerHeight) window.scrollBy({top: box.top - window.innerHeight / 2, behavior: 'instant'});
    }

    return {
      run(command) {
        switch (command.action) {
          case 'search': {
            end();
            const text = String(command.query).slice(0, LIMIT);
            let compiled;
            try {
              compiled = new RegExp(command.regex ? text : text.replace(/[\\^$.*+?()[\]{}|]/g, '\\$&'), command.matchCase ? 'g' : 'gi');
            } catch (invalid) {
              tell({search: command.search, count: 0, invalid: true}, 0);
              return;
            }
            search = command.search;
            pattern = compiled;
            ranges = text === '' ? [] : matches();
            paint();
            observer = new MutationObserver(changed);
            observer.observe(document, {childList: true, subtree: true, characterData: true});
            tell({search, count: ranges.length, invalid: false}, 0);
            return;
          }
          case 'select':
            if (command.search !== search || pattern === null) return;
            current = Number.isInteger(command.index) && command.index >= 0 && command.index < ranges.length ? command.index : -1;
            paint();
            if (current >= 0) {
              reveal(ranges[current]);
              show(ranges[current]);
            }
            return;
          case 'selection': {
            const selected = String(window.getSelection() || '');
            const line = selected.length <= LIMIT && !/[\n\r\u2028\u2029]/.test(selected);
            tell({search: command.search, selection: line ? selected : ''}, 0);
            return;
          }
          case 'clear':
            end();
            return;
        }
      }
    };
  }
})
