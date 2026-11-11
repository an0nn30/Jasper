/*
 * The Browser's find in page. Chromium's own find takes no regular expressions, so Jasper runs this in each frame of a
 * page as its find bar asks: this function, called with one command, and, for a Markdown preview, the name of the
 * preview's message router. A command's search is the id Jasper gave it, and each answer goes to Jasper through that
 * router, else jasperBridgeQuery, op "find".
 *
 * - {action: "search", search, query, matchCase, wholeWord, regex} finds the query, at most 1,000 characters, in the
 *   frame's visible text: as plain text, whose runs of white space match any, or as a JavaScript regular expression
 *   (RegExp, "i" added when case does not matter). With wholeWord, plain text matches only where no letter or digit
 *   comes right before or after it, and a regular expression is put in a group between \b and \b, as the Editor's Basic
 *   preview does.
 *   The text is read a block at a time: what one block-level element holds, up
 *   to a <br> or another block-level element, each run of white space (space, tab, line feed, form feed, carriage
 *   return and no-break space) as one space, so a match may span inline elements and the source's line breaks.
 *   It highlights every match in #FFFF00 with the CSS Custom Highlight API, which leaves the page's nodes and scripts
 *   alone, and answers {search, count, invalid}. A search keeps at most 10,000 matches, or those it found in the text
 *   it collected in 250 ms.
 *   Until the next search or clear, a change to the document's nodes, text or hidden, open, class or style attributes
 *   runs the search again, after 300 ms or ten times as long as the search before took, whichever is longer, and no
 *   more once a search ran out of time; it answers again when the count changes, and the current match stays current,
 *   or the last one does when there are fewer.
 * - {action: "select", search, index} makes match index the current one, in #FF9632: it opens the closed details,
 *   hidden="until-found" elements and regions of collapsed aria-expanded controls that hide it, and scrolls it into
 *   view. -1 makes none current. The search does not see its own changes to the page.
 * - {action: "step", search, query, matchCase, wholeWord, regex, forward}, the Markdown preview's Next and Previous,
 *   searches as "search" does unless the search under way already is this one, then selects the first match after the
 *   frame's selection, or the last before it, wrapping round, makes it the current one, scrolls it into view as little
 *   as it can, and answers {search, count, invalid} for the step.
 * - {action: "selection", search} answers {search, selection}: the frame's selected text when it is one line of at
 *   most 1,000 characters, else "".
 * - {action: "clear"} removes every highlight and the style this script added, and ends the search. A document that is
 *   hidden, as when it goes into the back-forward cache, does the same.
 *
 * Text counts as visible when it shows, or when only those three hide it. Text in scripts, styles, templates, form
 * fields and shadow roots is not searched, but for one: while the Browser's reader mode shows its article over the page,
 * which reader.js hands find through window[Symbol.for('dev.jasper.browser.reader')].root(), a search reads that
 * shadow root's text, and nothing behind it, highlights its matches there, which the overlay's own sheet styles, and
 * watches the article for changes. Jasper ends the search as reader mode opens and closes, so the next search reaches
 * what then shows.
 */
(function (command, router) {
  'use strict';
  const KEY = Symbol.for('dev.jasper.browser.find');
  let find = window[KEY];
  if (!find) {
    if (command.action === 'select' || command.action === 'clear') return;
    find = make(typeof router === 'string' ? router : 'jasperBridgeQuery');
    try {
      Object.defineProperty(window, KEY, {value: find});
    } catch (taken) {
      return;
    }
  }
  find.run(command);

  function make(routerName) {
    const LIMIT = 1000, MATCHES = 10000, QUIET = 300, BUDGET = 250;
    // Jasper takes only a few of a page's requests at a time, and refuses the rest as busy: an answer then waits its turn.
    const BUSY = 5, TRIES = 6;
    const ALL = 'jasper-find', CURRENT = 'jasper-find-current';
    // Where reader mode keeps itself, in a page's top frame; its root() is the overlay's shadow root while it shows.
    const READER = Symbol.for('dev.jasper.browser.reader');
    // Elements whose text is not the page's own, which the walk leaves out whole.
    const UNSEARCHED = new Set(['script', 'style', 'noscript', 'template', 'textarea', 'select']);
    const FILTER = {
      acceptNode: node => node.nodeType === Node.ELEMENT_NODE && UNSEARCHED.has(node.localName)
        ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT
    };
    // White space a page's text collapses, and the no-break space, which find takes as one; the query's is read alike.
    // What collapses: a run of two or more, or one that is not a space; a lone space stays as it is.
    const NBSP = String.fromCharCode(160), WHITE = '[ \\t\\n\\f\\r' + NBSP + ']';
    const COLLAPSING = new RegExp(WHITE + '{2,}|[\\t\\n\\f\\r' + NBSP + ']', 'g'), BLANKS = new RegExp(WHITE + '+', 'g');
    // What may not come right before or after a whole word of plain text: a letter or a digit, as Java's isLetterOrDigit.
    const BEFORE = '(?<![\\p{L}\\p{Nd}])', AFTER = '(?![\\p{L}\\p{Nd}])';
    const WATCHED = {childList: true, subtree: true, characterData: true, attributes: true,
      attributeFilter: ['hidden', 'open', 'class', 'style']};
    const query = window[routerName];
    const sheet = new CSSStyleSheet();
    sheet.replaceSync('::highlight(' + ALL + ') { background-color: #FFFF00; color: #000000; }\n'
      + '::highlight(' + CURRENT + ') { background-color: #FF9632; color: #000000; }');
    const all = new Highlight(), now = new Highlight();
    now.priority = 1;
    let search = 0, pattern = null, ranges = [], current = -1, observer = null, timer = 0;
    // What the search under way reads: the document, or the reader's shadow root while reader mode shows.
    let area = document;
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

    // What a search reads now: the reader's shadow root while reader mode shows its article, else the document.
    function scope() {
      let root = null;
      try {
        const reader = window[READER];
        root = reader !== null && typeof reader === 'object' && typeof reader.root === 'function' ? reader.root() : null;
      } catch (unreadable) {
        root = null;
      }
      return typeof ShadowRoot === 'function' && root instanceof ShadowRoot ? root : document;
    }

    // The regions a collapsed aria-expanded control names, by id, with the control.
    function controlled() {
      const named = new Map();
      for (const control of area.querySelectorAll('[aria-expanded="false"][aria-controls]')) {
        for (const id of control.getAttribute('aria-controls').split(/\s+/)) if (id !== '' && !named.has(id)) named.set(id, control);
      }
      return named;
    }

    // Whether element is hidden="until-found", its value read ASCII case-insensitively, as HTML reads it.
    function untilFound(element) {
      const value = element.getAttribute('hidden');
      return value !== null && value.replace(/[A-Z]/g, letter => letter.toLowerCase()) === 'until-found';
    }

    // Whether find opens element: a closed details, a hidden="until-found" element, or a collapsed control's region.
    function opens(element, named) {
      return element.localName === 'details' && !element.open || untilFound(element) || element.id !== '' && named.has(element.id);
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

    // The element's computed display, once per element in a pass.
    function displayOf(element, pass) {
      let display = pass.displays.get(element);
      if (display === undefined) {
        display = getComputedStyle(element).display;
        pass.displays.set(element, display);
      }
      return display;
    }

    // Whether a display lays an element out as a block: no inline kind (inline, inline-block, inline-flex and so on),
    // not contents and not none.
    function block(display) {
      return !display.startsWith('inline') && display !== 'contents' && display !== 'none';
    }

    // The block a text's element lies in: the nearest element, itself included, whose display is no inline kind and
    // not contents.
    function blockOf(element, pass) {
      const passed = [];
      let found = null;
      for (let at = element; at !== null; at = at.parentElement) {
        const known = pass.blocks.get(at);
        if (known !== undefined) {
          found = known;
          break;
        }
        passed.push(at);
        const display = displayOf(at, pass);
        if (!display.startsWith('inline') && display !== 'contents') {
          found = at;
          break;
        }
      }
      for (const each of passed) pass.blocks.set(each, found);
      return found;
    }

    // text with each run of white space as one space: the text, and after each run of two or more, the offset its next
    // character takes and how many characters were dropped up to there. One native replace, which leaves lone spaces,
    // the most of a page's white space, alone: no loop over characters.
    function collapse(text) {
      const points = [], dropped = [];
      let gone = 0;
      const collapsed = text.replace(COLLAPSING, (spaces, at) => {
        if (spaces.length > 1) {
          gone += spaces.length - 1;
          points.push(at - gone + spaces.length);
          dropped.push(gone);
        }
        return ' ';
      });
      return {collapsed, points, dropped};
    }

    // The index of the last of the ascending values that is at most target; -1 for none.
    function lastAtMost(values, target) {
      let low = 0, high = values.length - 1, found = -1;
      while (low <= high) {
        const middle = (low + high) >> 1;
        if (values[middle] <= target) {
          found = middle;
          low = middle + 1;
        } else {
          high = middle - 1;
        }
      }
      return found;
    }

    // Adds node's text to the run, collapsed; a space that would follow the run's own is dropped.
    function append(run, node) {
      let {collapsed, points, dropped} = collapse(node.nodeValue), lead = 0;
      if (run.space && collapsed.startsWith(' ')) {
        collapsed = collapsed.slice(1);
        lead = 1;
      }
      if (collapsed === '') return;
      run.starts.push(run.length);
      run.segments.push({node, lead, points, dropped});
      run.parts.push(collapsed);
      run.length += collapsed.length;
      run.space = collapsed.endsWith(' ');
    }

    // Where character index of the run's text came from: its node, and its offset in that node's text.
    function locate(run, index) {
      const at = lastAtMost(run.starts, index), segment = run.segments[at];
      const inner = index - run.starts[at] + segment.lead, point = lastAtMost(segment.points, inner);
      return {node: segment.node, offset: inner + (point < 0 ? 0 : segment.dropped[point])};
    }

    // Whether the pass is over: it holds every match it may keep, or its time is up.
    function over(pass) {
      if (!pass.late && performance.now() > pass.deadline) pass.late = true;
      return pass.late || pass.found.length >= MATCHES;
    }

    // Finds the pattern in the run's text and keeps a range for each match, across its nodes, until the pass holds every
    // match it may keep. A run the pass collected is searched whole, whatever the time, so it counts the text it reached;
    // only a pattern's empty matches, which find nothing and step one character at a time, stop at the deadline. Answers
    // null: what follows starts a run of its own.
    function seek(run, pass) {
      if (run === null) return null;
      const text = run.parts.join('');
      pattern.lastIndex = 0;
      let hit;
      while (pass.found.length < MATCHES && (hit = pattern.exec(text)) !== null) {
        if (hit[0] === '') {
          if (over(pass)) break;
          pattern.lastIndex++;
          continue;
        }
        const start = locate(run, hit.index), end = locate(run, hit.index + hit[0].length - 1);
        const range = document.createRange();
        range.setStart(start.node, start.offset);
        range.setEnd(end.node, end.offset + 1);
        pass.found.push(range);
      }
      return null;
    }

    // Every match in the document's visible text, in document order, read a block at a time. A block-level element,
    // with text or without, and a <br> end the text before them. Once its time is up, a pass still searches the text it
    // collected, so a page counts what the pass reached.
    function matches() {
      const started = performance.now();
      const pass = {deadline: started + BUDGET, late: false, found: [], named: controlled(), displays: new Map(), blocks: new Map(),
        counted: new Map()};
      const walker = document.createTreeWalker(area, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, FILTER);
      let run = null;
      for (let node = walker.nextNode(); node !== null && !over(pass); node = walker.nextNode()) {
        if (node.nodeType === Node.ELEMENT_NODE) {
          if (node.localName === 'br' || block(displayOf(node, pass))) run = seek(run, pass);
          continue;
        }
        const parent = node.parentElement;
        if (!node.nodeValue || parent === null || !counted(parent, pass)) continue;
        const within = blockOf(parent, pass);
        if (run !== null && run.block !== within) run = seek(run, pass);
        if (run === null) run = {block: within, parts: [], starts: [], segments: [], length: 0, space: false};
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
      area = document;
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
      const loose = element.localName === 'details' && !element.open || untilFound(element);
      const box = boxed(element);
      if (!loose && box !== null && box.checkVisibility({visibilityProperty: true})) return;
      const named = controlled(), hiders = [];
      for (let at = element; at !== null; at = at.parentElement) if (opens(at, named)) hiders.unshift(at);
      for (const hider of hiders) {
        if (hider.localName === 'details' && !hider.open) {
          hider.open = true;
        } else if (untilFound(hider)) {
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
      // In the reader, the overlay scrolled to it; the page behind stays where it is.
      if (area !== document) return;
      const box = range.getBoundingClientRect();
      if (box.top < 0 || box.bottom > window.innerHeight) window.scrollBy({top: box.top - window.innerHeight / 2, behavior: 'instant'});
    }

    // Scrolls the range into view as little as it can: the nearest edge of its element, then the range itself.
    function near(range) {
      const element = boxed(range.startContainer.parentElement);
      if (element === null) return;
      element.scrollIntoView({behavior: 'instant', block: 'nearest', inline: 'nearest'});
      if (area !== document) return;
      const box = range.getBoundingClientRect();
      if (box.top < 0) window.scrollBy({top: box.top, behavior: 'instant'});
      else if (box.bottom > window.innerHeight) window.scrollBy({top: box.bottom - window.innerHeight, behavior: 'instant'});
    }

    // The command's query as a pattern, or null when it is no regular expression.
    function compile(command) {
      const text = String(command.query).slice(0, LIMIT);
      let source, flags = command.matchCase ? 'g' : 'gi';
      if (command.regex) {
        source = text;
      } else {
        source = text.replace(BLANKS, ' ').replace(/[\\^$.*+?()[\]{}|]/g, '\\$&');
        if (command.wholeWord === true) {
          source = BEFORE + source + AFTER;
          flags += 'u';
        }
      }
      try {
        const pattern = new RegExp(source, flags);
        // Whole Word puts the whole expression between the boundaries, so a|b is a whole a or a whole b; compiled
        // alone first, an expression such as a)(b stays refused.
        return command.regex && command.wholeWord === true ? new RegExp('\\b(?:' + source + ')\\b', flags) : pattern;
      } catch (invalid) {
        return null;
      }
    }

    // Starts a search for the pattern, with the command's id: its matches, highlighted and watched.
    function begin(command, compiled) {
      end();
      search = command.search;
      pattern = compiled;
      area = scope();
      ranges = String(command.query) === '' ? [] : matches();
      paint();
      if (!exhausted) {
        observer = new MutationObserver(changed);
        observer.observe(area, WATCHED);
      }
    }

    // The selection in where, the search's area unless given: a shadow root's own, where it has one, else the window's.
    function selectionIn(where) {
      const within = where === undefined ? area : where;
      return within !== document && typeof within.getSelection === 'function' ? within.getSelection() : window.getSelection();
    }

    // The match a step goes to: the first that starts at or after the selection's end, or the last that ends at or
    // before its start, round from the end to the start; without a selection, the first or the last.
    function following(forward) {
      const selection = selectionIn();
      if (selection === null || selection.rangeCount === 0) return forward ? 0 : ranges.length - 1;
      const selected = selection.getRangeAt(0), point = document.createRange();
      if (forward) {
        point.setStart(selected.endContainer, selected.endOffset);
        for (let at = 0; at < ranges.length; at++) if (ranges[at].compareBoundaryPoints(Range.START_TO_START, point) >= 0) return at;
        return 0;
      }
      point.setStart(selected.startContainer, selected.startOffset);
      for (let at = ranges.length - 1; at >= 0; at--) if (ranges[at].compareBoundaryPoints(Range.END_TO_END, point) <= 0) return at;
      return ranges.length - 1;
    }

    return {
      run(command) {
        switch (command.action) {
          case 'search': {
            end();
            const compiled = compile(command);
            if (compiled === null) {
              tell({search: command.search, count: 0, invalid: true}, 0);
              return;
            }
            begin(command, compiled);
            tell({search, count: ranges.length, invalid: false}, 0);
            return;
          }
          case 'step': {
            const compiled = compile(command);
            if (compiled === null) {
              end();
              tell({search: command.search, count: 0, invalid: true}, 0);
              return;
            }
            const same = pattern !== null && pattern.source === compiled.source && pattern.flags === compiled.flags
              && ranges.every(range => range.startContainer.isConnected && range.endContainer.isConnected);
            if (same) search = command.search;
            else begin(command, compiled);
            if (ranges.length > 0) {
              current = following(command.forward === true);
              paint();
              reveal(ranges[current]);
              if (observer !== null) observer.takeRecords();
              const selection = selectionIn();
              if (selection !== null) {
                selection.removeAllRanges();
                selection.addRange(ranges[current].cloneRange());
              }
              near(ranges[current]);
            }
            tell({search: command.search, count: ranges.length, invalid: false}, 0);
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
            const text = String(selectionIn(scope()) || '');
            const line = text.length <= LIMIT && !/[\n\r\u2028\u2029]/.test(text);
            tell({search: command.search, selection: line ? text : ''}, 0);
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
