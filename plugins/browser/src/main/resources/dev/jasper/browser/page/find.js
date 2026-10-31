/*
 * The Browser's find in page. Chromium's own find takes no regular expressions, so Jasper runs this in each frame of a
 * page as its find bar asks: this function, called with one command. A command's search is the id Jasper gave it, and
 * each answer goes to Jasper through jasperBridgeQuery, op "find".
 *
 * - {action: "search", search, query, matchCase, regex} finds the query, at most 1,000 characters, in the frame's
 *   visible text: as plain text, whose runs of white space match any, or as a JavaScript regular expression (RegExp,
 *   "i" added when case does not matter). The text is read a block at a time: what one block-level element holds, up
 *   to a <br>, each run of white space as one space, so a match may span inline elements and the source's line breaks.
 *   It highlights every match in #FFFF00 with the CSS Custom Highlight API, which leaves the page's nodes and scripts
 *   alone, and answers {search, count, invalid}. A search keeps at most 10,000 matches, or those it found in 250 ms.
 *   Until the next search or clear, a change to the document's nodes, text or hidden, open, class or style attributes
 *   runs the search again, after 300 ms or ten times as long as the search before took, whichever is longer, and no
 *   more once a search ran out of time; it answers again when the count changes, and the current match stays current,
 *   or the last one does when there are fewer.
 * - {action: "select", search, index} makes match index the current one, in #FF9632: it opens the closed details,
 *   hidden="until-found" elements and regions of collapsed aria-expanded controls that hide it, and scrolls it into
 *   view. -1 makes none current. The search does not see its own changes to the page.
 * - {action: "selection", search} answers {search, selection}: the frame's selected text when it is one line of at
 *   most 1,000 characters, else "".
 * - {action: "clear"} removes every highlight and the style this script added, and ends the search. A document that is
 *   hidden, as when it goes into the back-forward cache, does the same.
 *
 * Text counts as visible when it shows, or when only those three hide it. Text in scripts, styles, templates, form
 * fields and shadow roots is not searched.
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
    const LIMIT = 1000, MATCHES = 10000, QUIET = 300, BUDGET = 250;
    // Jasper takes only a few of a page's requests at a time, and refuses the rest as busy: an answer then waits its turn.
    const BUSY = 5, TRIES = 6;
    const ALL = 'jasper-find', CURRENT = 'jasper-find-current';
    // Elements whose text is not the page's own, which the walk leaves out whole.
    const UNSEARCHED = new Set(['script', 'style', 'noscript', 'template', 'textarea', 'select']);
    const FILTER = {
      acceptNode: node => node.nodeType === Node.ELEMENT_NODE && UNSEARCHED.has(node.localName)
        ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT
    };
    const WATCHED = {childList: true, subtree: true, characterData: true, attributes: true,
      attributeFilter: ['hidden', 'open', 'class', 'style']};
    const query = window.jasperBridgeQuery;
    const sheet = new CSSStyleSheet();
    sheet.replaceSync('::highlight(' + ALL + ') { background-color: #FFFF00; color: #000000; }\n'
      + '::highlight(' + CURRENT + ') { background-color: #FF9632; color: #000000; }');
    const all = new Highlight(), now = new Highlight();
    now.priority = 1;
    let search = 0, pattern = null, ranges = [], current = -1, observer = null, timer = 0;
    // How long the last search took, in milliseconds, and whether it ran out of time.
    let cost = 0, exhausted = false;
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

    // The element, or its nearest ancestor with a box of its own: display: contents makes none.
    function boxed(element) {
      let at = element;
      while (at !== null && getComputedStyle(at).display === 'contents') at = at.parentElement;
      return at;
    }

    // Whether the text of element counts: it shows, or only what find opens hides it.
    function countable(element, named) {
      for (let at = boxed(element); at !== null;) {
        if (at.checkVisibility({visibilityProperty: true})) return true;
        let hider = at;
        while (hider !== null && !opens(hider, named)) {
          if (getComputedStyle(hider).display === 'none') return false;
          hider = hider.parentElement;
        }
        if (hider === null) return false;
        at = boxed(hider.parentElement);
      }
      return false;
    }

    // As countable, once per element in a pass.
    function counted(element, pass) {
      let counts = pass.counted.get(element);
      if (counts === undefined) {
        counts = countable(element, pass.named);
        pass.counted.set(element, counts);
      }
      return counts;
    }

    // The block a text's element lies in: the nearest element, itself included, whose display is no inline kind
    // (inline, inline-block, inline-flex and so on) and not contents.
    function blockOf(element, blocks) {
      const passed = [];
      let block = null;
      for (let at = element; at !== null; at = at.parentElement) {
        const known = blocks.get(at);
        if (known !== undefined) {
          block = known;
          break;
        }
        passed.push(at);
        const display = getComputedStyle(at).display;
        if (!display.startsWith('inline') && display !== 'contents') {
          block = at;
          break;
        }
      }
      for (const each of passed) blocks.set(each, block);
      return block;
    }

    // Whether a character is white space a page's text collapses, or a no-break space, which find takes as one.
    function blank(code) {
      return code === 32 || code === 9 || code === 10 || code === 12 || code === 13 || code === 160;
    }

    // Adds node's text to the run, each run of white space as one space, noting where each character came from.
    function append(run, node) {
      const text = node.nodeValue, index = run.nodes.push(node) - 1;
      let added = '';
      for (let at = 0; at < text.length; at++) {
        const space = blank(text.charCodeAt(at));
        if (space && run.space) continue;
        added += space ? ' ' : text[at];
        run.at.push(at);
        run.which.push(index);
        run.space = space;
      }
      run.text += added;
    }

    // Whether the pass is over: it holds every match it may keep, or its time is up.
    function over(pass) {
      if (!pass.late && performance.now() > pass.deadline) pass.late = true;
      return pass.late || pass.found.length >= MATCHES;
    }

    // Finds the pattern in the run's text and keeps a range for each match, across its nodes. Answers null: what follows
    // starts a run of its own.
    function seek(run, pass) {
      if (run === null) return null;
      pattern.lastIndex = 0;
      let hit;
      while (!over(pass) && (hit = pattern.exec(run.text)) !== null) {
        if (hit[0] === '') {
          pattern.lastIndex++;
          continue;
        }
        const first = hit.index, last = hit.index + hit[0].length - 1;
        const range = document.createRange();
        range.setStart(run.nodes[run.which[first]], run.at[first]);
        range.setEnd(run.nodes[run.which[last]], run.at[last] + 1);
        pass.found.push(range);
      }
      return null;
    }

    // Every match in the document's visible text, in document order, read a block at a time.
    function matches() {
      const started = performance.now();
      const pass = {deadline: started + BUDGET, late: false, found: [], named: collapsed(), blocks: new Map(), counted: new Map()};
      const walker = document.createTreeWalker(document, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, FILTER);
      let run = null;
      for (let node = walker.nextNode(); node !== null && !over(pass); node = walker.nextNode()) {
        if (node.nodeType === Node.ELEMENT_NODE) {
          if (node.localName === 'br') run = seek(run, pass);
          continue;
        }
        const parent = node.parentElement;
        if (!node.nodeValue || parent === null || !counted(parent, pass)) continue;
        const block = blockOf(parent, pass.blocks);
        if (run !== null && run.block !== block) run = seek(run, pass);
        if (run === null) run = {block, text: '', nodes: [], at: [], which: [], space: false};
        append(run, node);
      }
      seek(run, pass);
      cost = performance.now() - started;
      exhausted = pass.late;
      return pass.found;
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

    function unwatch() {
      if (observer !== null) observer.disconnect();
      clearTimeout(timer);
      observer = null;
      timer = 0;
    }

    function end() {
      unwatch();
      search = 0;
      pattern = null;
      ranges = [];
      current = -1;
      cost = 0;
      exhausted = false;
      all.clear();
      now.clear();
      if (CSS.highlights.get(ALL) === all) CSS.highlights.delete(ALL);
      if (CSS.highlights.get(CURRENT) === now) CSS.highlights.delete(CURRENT);
      if (document.adoptedStyleSheets.includes(sheet)) document.adoptedStyleSheets = document.adoptedStyleSheets.filter(each => each !== sheet);
    }

    function changed() {
      if (timer === 0 && !exhausted) timer = setTimeout(again, Math.max(QUIET, 10 * cost));
    }

    function again() {
      timer = 0;
      if (pattern === null) return;
      const before = ranges.length;
      ranges = matches();
      if (current >= ranges.length) current = ranges.length - 1;
      paint();
      if (exhausted) unwatch();
      if (ranges.length !== before) tell({search, count: ranges.length, invalid: false}, 0);
    }

    // Opens what hides the range, outermost first. Text right inside a closed details or a hidden="until-found" element
    // is hidden though that element shows. A collapsed control is clicked only while its region is hidden, and only when
    // that cannot follow a link or send a form.
    function reveal(range) {
      const element = range.startContainer.parentElement;
      if (element === null) return;
      const loose = element.localName === 'details' && !element.open || element.getAttribute('hidden') === 'until-found';
      const box = boxed(element);
      if (!loose && box !== null && box.checkVisibility({visibilityProperty: true})) return;
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
      const element = boxed(range.startContainer.parentElement);
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
            const source = command.regex ? text : text.replace(/\s+/g, ' ').replace(/[\\^$.*+?()[\]{}|]/g, '\\$&');
            let compiled;
            try {
              compiled = new RegExp(source, command.matchCase ? 'g' : 'gi');
            } catch (invalid) {
              tell({search: command.search, count: 0, invalid: true}, 0);
              return;
            }
            search = command.search;
            pattern = compiled;
            ranges = text === '' ? [] : matches();
            paint();
            if (!exhausted) {
              observer = new MutationObserver(changed);
              observer.observe(document, WATCHED);
            }
            tell({search, count: ranges.length, invalid: false}, 0);
            return;
          }
          case 'select':
            if (command.search !== search || pattern === null) return;
            current = Number.isInteger(command.index) && command.index >= 0 && command.index < ranges.length ? command.index : -1;
            paint();
            if (current >= 0) {
              reveal(ranges[current]);
              // What reveal changed is find's own doing, not the page's.
              if (observer !== null) observer.takeRecords();
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
