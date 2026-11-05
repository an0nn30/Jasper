/*
 * The Markdown preview's script, which jasper-preview: serves at /preview.js and the shell loads in its head. Jasper
 * calls it as jasperPreview.<function>(...), each argument a JSON string literal or a boolean:
 *
 * - theme(variables, mermaid, dark): sets each CSS custom property of the JSON object variables on the document's
 *   root, and the root's colour scheme; when Mermaid's theme variables, the JSON object mermaid, changed, the diagrams
 *   are drawn again in them.
 * - show(revision): fetches /document/<revision>, aborting the fetch before, and puts it in place of the document shown
 *   through a <template>, keeping the scroll position, and sets the known images again. Then it tells Jasper shown, then
 *   where the blocks are, and draws the document's diagrams.
 * - image(id, url) shows the image at url in each image element whose data-image is id; imageReason(id, text) shows,
 *   in its place, a box with its alternative text, or "Image", then text; clearImages() forgets them all. Each holds for
 *   every document after, until clearImages.
 * - scrollTo(y) scrolls the page; before the first document is in the page, as when Jasper replays the last scroll on
 *   ready, that document goes there once installed. reveal(id) scrolls the document's element with that id to the top
 *   and puts the caret at its start, so Tab moves to the next link after it.
 *
 * It tells Jasper through the preview client's message router, jasperPreviewQuery, one flat JSON object at a time, every
 * number written as a string:
 *
 * - {op: "ready"}, once it has loaded;
 * - {op: "shown", rev}, once a document is in the page;
 * - {op: "geometry", rev, tops, height, viewport}: each data-b block's top, comma-separated in the order of the blocks'
 *   numbers, NaN for a block with no box; after each shown, and whenever the layout changes (a resize, an
 *   image or a font loading, a diagram drawn), at most once an animation frame and, after the first, every 100 ms;
 * - {op: "scrolled", y, user}, at most once an animation frame: user is false from a scrollTo until the first scroll
 *   more than 1 px from where it went, and true otherwise;
 * - {op: "link", id}, for a click or a middle click on a [data-link] element, or Enter on a focused one, whose default it
 *   prevents.
 *
 * Mermaid: the first document with a diagram (a pre.mermaid) loads /mermaid.min.js, which the Browser serves once it
 * bundles Mermaid. Until then, and if it does not load, a diagram shows as its source, as code. Each diagram's SVG is
 * kept by its source and theme. A diagram Mermaid cannot draw shows "Mermaid could not draw this diagram: <message>"
 * above its source. Mermaid runs strictly: no script or click of a diagram's runs, and a diagram's own directives cannot
 * change that, as Mermaid's default list of what they may not set stays.
 *
 * A heading's or a footnote's id is also a property of window, so this script reads no global it did not define, and
 * its own ids hold an upper-case letter, which no id of the Editor's holds. It never builds markup from text but the
 * document, through its template, and Mermaid's SVG.
 */
(function () {
  'use strict';
  const GEOMETRY_INTERVAL = 100;
  const MERMAID_FAILED = 'Mermaid could not draw this diagram: ';
  const router = window.jasperPreviewQuery;
  const root = document.documentElement;
  let content = null;
  // The document fetch under way, and the revision shown.
  let fetching = null, shownRevision = null;
  // The images by data-image id: {url} or {reason}; and each image element's reason box.
  const images = new Map(), boxes = new WeakMap();
  // Where the last scrollTo went, until the user scrolls elsewhere; and one asked for before the first document, which
  // that document takes.
  let target = null, scrollWaiting = false, pending = null;
  // Whether geometry waits to be told, and when it was last.
  let geometryWaiting = false, lastGeometry = 0;
  // Mermaid: 'absent' until a document asks for it, then 'loading', 'ready' or 'missing'; its theme, as the JSON text
  // Jasper gave and as read; the drawing pass under way, by number; the drawn diagrams by theme and source.
  let mermaidState = 'absent', mermaidTheme = '{}', mermaidVariables = {}, drawing = 0;
  const drawn = new Map();

  function post(message) {
    if (typeof router !== 'function') return;
    router({request: JSON.stringify(message), persistent: false, onSuccess() {}, onFailure() {}});
  }

  function setTheme(variables, mermaid, dark) {
    const values = JSON.parse(String(variables));
    for (const name of Object.keys(values)) if (name.startsWith('--')) root.style.setProperty(name, String(values[name]));
    root.style.colorScheme = dark === true ? 'dark' : 'light';
    const next = String(mermaid);
    if (next === mermaidTheme) return;
    mermaidTheme = next;
    mermaidVariables = JSON.parse(next);
    if (mermaidState === 'ready') {
      configureMermaid();
      drawDiagrams();
    }
  }

  function show(revision) {
    if (content === null) return;
    const rev = String(revision);
    if (fetching !== null) fetching.abort();
    const controller = new AbortController();
    fetching = controller;
    fetch('/document/' + rev, {signal: controller.signal, cache: 'no-store', credentials: 'omit'})
      .then(response => response.ok ? response.text() : null)
      .then(html => {
        if (fetching !== controller) return;
        fetching = null;
        if (html !== null) install(rev, html);
      })
      .catch(() => {
        if (fetching === controller) fetching = null;
      });
  }

  function install(rev, html) {
    const template = document.createElement('template');
    template.innerHTML = html;
    const asked = pending, kept = window.scrollY;
    pending = null;
    content.replaceChildren(template.content);
    window.scrollTo({top: asked === null ? kept : asked, behavior: 'instant'});
    // Jasper's scroll, or a shorter document's move: neither is the user's.
    if (asked !== null || window.scrollY !== kept) target = window.scrollY;
    shownRevision = rev;
    for (const element of content.querySelectorAll('img[data-image]')) applyImage(element);
    post({op: 'shown', rev});
    tellGeometry();
    if (content.querySelector('pre.mermaid') !== null) {
      if (mermaidState === 'absent') loadMermaid();
      else if (mermaidState === 'ready') drawDiagrams();
    }
  }

  function image(id, url) {
    images.set(String(id), {url: String(url)});
    applyImages(String(id));
  }

  function imageReason(id, text) {
    images.set(String(id), {reason: String(text)});
    applyImages(String(id));
  }

  function clearImages() {
    images.clear();
    applyImages(null);
  }

  // Applies the images to the elements of one data-image id, or of every id for null.
  function applyImages(id) {
    if (content === null) return;
    for (const element of content.querySelectorAll('img[data-image]'))
      if (id === null || element.getAttribute('data-image') === id) applyImage(element);
  }

  function applyImage(element) {
    const state = images.get(element.getAttribute('data-image'));
    let box = boxes.get(element);
    if (state !== undefined && state.reason !== undefined) {
      element.removeAttribute('src');
      element.hidden = true;
      if (box === undefined) {
        box = document.createElement('span');
        box.className = 'image-reason';
        const alt = document.createElement('span'), why = document.createElement('span');
        alt.className = 'image-reason-alt';
        why.className = 'image-reason-text';
        box.append(alt, why);
        element.before(box);
        boxes.set(element, box);
      }
      box.firstChild.textContent = element.alt.trim() === '' ? 'Image' : element.alt;
      box.lastChild.textContent = state.reason;
      return;
    }
    if (box !== undefined) {
      box.remove();
      boxes.delete(element);
    }
    element.hidden = false;
    if (state === undefined) element.removeAttribute('src');
    else if (element.getAttribute('src') !== state.url) element.setAttribute('src', state.url);
  }

  function scrollPage(y) {
    const top = Number(y);
    if (!Number.isFinite(top)) return;
    // The shell alone cannot scroll: the first document goes there.
    if (shownRevision === null) {
      pending = top;
      return;
    }
    window.scrollTo({top, behavior: 'instant'});
    target = window.scrollY;
  }

  function reveal(id) {
    const element = document.getElementById(String(id));
    if (element === null || content === null || !content.contains(element)) return;
    element.scrollIntoView({block: 'start', behavior: 'instant'});
    const selection = window.getSelection();
    if (selection !== null) selection.collapse(element, 0);
    // Focusing the element starts the next Tab after it; one that takes no focus takes it this once.
    if (element.tabIndex < 0 && !element.hasAttribute('tabindex')) {
      element.setAttribute('tabindex', '-1');
      element.addEventListener('blur', () => element.removeAttribute('tabindex'), {once: true});
    }
    element.focus({preventScroll: true});
  }

  function geometry() {
    const tops = [], scrolled = window.scrollY;
    for (const block of content.querySelectorAll('[data-b]')) {
      const written = block.getAttribute('data-b');
      if (!/^(0|[1-9][0-9]{0,5})$/.test(written)) continue;
      const number = Number(written);
      while (tops.length <= number) tops.push('NaN');
      if (block.getClientRects().length > 0) tops[number] = String(Math.round((block.getBoundingClientRect().top + scrolled) * 100) / 100);
    }
    return {op: 'geometry', rev: shownRevision, tops: tops.join(','), height: String(root.scrollHeight),
      viewport: String(window.innerHeight)};
  }

  function tellGeometry() {
    lastGeometry = performance.now();
    post(geometry());
  }

  // Tells where the blocks are once the layout settles: at most once an animation frame, and every 100 ms.
  function geometryChanged() {
    if (geometryWaiting || shownRevision === null || content === null) return;
    geometryWaiting = true;
    const wait = Math.max(0, lastGeometry + GEOMETRY_INTERVAL - performance.now());
    setTimeout(() => requestAnimationFrame(() => {
      geometryWaiting = false;
      tellGeometry();
    }), wait);
  }

  function scrolled() {
    if (scrollWaiting) return;
    scrollWaiting = true;
    requestAnimationFrame(() => {
      scrollWaiting = false;
      const y = window.scrollY;
      let user = true;
      if (target !== null) {
        if (Math.abs(y - target) <= 1) user = false;
        else target = null;
      }
      post({op: 'scrolled', y: String(y), user});
    });
  }

  function activate(event) {
    const element = event.target instanceof Element ? event.target.closest('[data-link]') : null;
    if (element === null || !content.contains(element)) return;
    event.preventDefault();
    const id = element.getAttribute('data-link');
    if (/^(0|[1-9][0-9]*)$/.test(id)) post({op: 'link', id});
  }

  function loadMermaid() {
    mermaidState = 'loading';
    const script = document.createElement('script');
    script.src = '/mermaid.min.js';
    script.addEventListener('load', () => {
      const api = window.mermaid;
      if (api === null || typeof api !== 'object' || typeof api.render !== 'function') {
        mermaidState = 'missing';
        return;
      }
      mermaidState = 'ready';
      configureMermaid();
      drawDiagrams();
    }, {once: true});
    script.addEventListener('error', () => {
      mermaidState = 'missing';
      script.remove();
    }, {once: true});
    document.head.append(script);
  }

  function configureMermaid() {
    window.mermaid.initialize({startOnLoad: false, securityLevel: "strict", theme: "base", themeVariables: mermaidVariables,
      deterministicIds: true, fontFamily: mermaidVariables.fontFamily, suppressErrorRendering: true});
  }

  // Draws the document's diagrams, one after another, in the theme now; a later pass ends this one.
  async function drawDiagrams() {
    const pass = ++drawing, theme = mermaidTheme, used = new Set();
    let index = 0;
    for (const source of Array.from(content.querySelectorAll('pre.mermaid'))) {
      const text = source.textContent, key = theme + '\n' + text;
      used.add(key);
      let result = drawn.get(key);
      if (result === undefined) {
        try {
          const rendered = await window.mermaid.render('Jasper-diagram-' + pass + '-' + index, text);
          result = {svg: rendered.svg};
        } catch (failure) {
          result = {error: failure !== null && typeof failure === 'object' && typeof failure.message === 'string' ? failure.message : String(failure)};
        }
        if (pass !== drawing) return;
        drawn.set(key, result);
      }
      index++;
      if (source.isConnected) place(source, result);
    }
    for (const key of Array.from(drawn.keys())) if (!used.has(key)) drawn.delete(key);
    geometryChanged();
  }

  // Puts a diagram's drawing, or why it could not be drawn, above its source, in a box that takes the source's block.
  function place(source, result) {
    let box = source.parentElement;
    if (box === null || !box.classList.contains('mermaid-diagram')) {
      box = document.createElement('div');
      box.className = 'mermaid-diagram';
      const anchor = source.getAttribute('data-b');
      if (anchor !== null) {
        box.setAttribute('data-b', anchor);
        source.removeAttribute('data-b');
      }
      source.replaceWith(box);
      box.append(source);
    }
    for (const child of Array.from(box.children)) if (child !== source) child.remove();
    if (result.svg !== undefined) {
      const picture = document.createElement('div');
      picture.className = 'mermaid-svg';
      picture.innerHTML = result.svg;
      box.prepend(picture);
      source.hidden = true;
    } else {
      const error = document.createElement('p');
      error.className = 'mermaid-error';
      error.textContent = MERMAID_FAILED + result.error;
      box.prepend(error);
      source.hidden = false;
    }
  }

  function start() {
    content = document.getElementById('Jasper-content');
    if (content === null) return;
    content.addEventListener('click', event => {
      if (event.button === 0) activate(event);
    });
    content.addEventListener('auxclick', event => {
      if (event.button === 1) activate(event);
    });
    content.addEventListener('load', geometryChanged, true);
    new ResizeObserver(geometryChanged).observe(content);
    window.addEventListener('resize', geometryChanged);
    window.addEventListener('scroll', scrolled, {passive: true});
    document.fonts.addEventListener('loadingdone', geometryChanged);
    post({op: 'ready'});
  }

  Object.defineProperty(window, 'jasperPreview', {value: Object.freeze({
    theme: setTheme,
    show: show,
    image: image,
    imageReason: imageReason,
    clearImages: clearImages,
    scrollTo: scrollPage,
    reveal: reveal
  })});
  document.addEventListener('DOMContentLoaded', start, {once: true});
})();
