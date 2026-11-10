/*
 * The Browser's Save Page As (spec section 10, plan 7's Task 7). Jasper runs this in a page's top frame, in the page's
 * own world, never in a sub-frame, a challenge frame or one of Jasper's own pages. It is called with the save's prefix,
 * "jasper-save-", 16 hex digits and "-", and takes a snapshot of the document for Jasper, which fetches the resources
 * again itself and writes the folder; the page's live document is never changed.
 *
 * The snapshot is a copy of the document (cloneNode on the document, so the copy has no browsing context: it loads
 * nothing and runs nothing) in which:
 * - each http or https reference Jasper saves becomes a token, the prefix, "r", the resource's number and "-", so "r1"
 *   never starts "r10": img's src and each srcset candidate, source's src and srcset, video's src and poster, audio's
 *   and track's src, an image input's src, script's src (kind "script") and the href of a link whose rel names a
 *   stylesheet (kind "style") or an icon. An element with a token loses its integrity and crossorigin attributes;
 * - each style element's text becomes the prefix, "s", its number and "-", and each style attribute's value the prefix,
 *   "a", its number and "-", Jasper rewriting the references in them;
 * - every other href, src, action and formaction becomes absolute, so links and frames stay remote; data: addresses and
 *   a fragment alone stay as written;
 * - base elements, charset declarations and Content-Security-Policy meta elements go, as a policy would block the
 *   saved files, and a meta charset="utf-8" comes first in head; the doctype is the document's.
 * No address keeps a user name or password. The answer, {prefix, html, resources: [{url, kind}], styles, attributes,
 * base}, goes to Jasper as JSON in parts, through jasperBridgeQuery (op "savePage"), as {save: prefix, part, parts, text},
 * one at a time, each after the last was answered, each part's text at most 1 MiB once JSON writes it, the bridge's
 * limit; a page of more than 64 parts sends only the count, which Jasper refuses, and a snapshot that fails sends an
 * empty part, which Jasper cannot read. A part the bridge refuses because too many of the page's requests wait is sent
 * again a little later, a few times.
 *
 * The saver is made once per document, kept under window[Symbol.for('dev.jasper.browser.save-page')], and calls the
 * built-ins as they were when it was made. The page's own scripts share this world, so they can see and change all of
 * it, and what they send Jasper is untrusted. Scripts are kept; shadow DOM content is not saved.
 */
(function (prefix) {
  'use strict';
  const KEY = Symbol.for('dev.jasper.browser.save-page');
  let saver = null;
  if (Reflect.apply(Object.prototype.hasOwnProperty, window, [KEY])) {
    saver = window[KEY];
  } else {
    saver = make();
    if (saver === null) return;
    try {
      Object.defineProperty(window, KEY, {value: saver, writable: false, enumerable: false, configurable: false});
    } catch (taken) {
      return;
    }
  }
  if (saver === null || typeof saver !== 'object') return;
  saver.save(prefix);

  /** The saver of this document, or null where the built-ins it needs are missing. */
  function make() {
    // The most characters of JSON one part may take: the opener bridge's message limit, 1 MiB.
    const LIMIT = 1048576;
    // The most parts Jasper takes.
    const MOST_PARTS = 64;
    // The bridge's answer to a page with too many requests waiting, and how often and how long after a part is sent again.
    const BUSY = 5, RETRIES = 20, RETRY_WAIT = 100;
    const XHTML = 'http://www.w3.org/1999/xhtml';
    const PREFIX = /^jasper-save-[0-9a-f]{16}-$/;
    // What a token takes: the elements whose references Jasper saves, then those made absolute.
    const SAVED = 'img, source, video, audio, track, input, script[src], link[href]';
    const ABSOLUTE = ['href', 'src', 'action', 'formaction'];
    const ICONS = ['icon', 'apple-touch-icon', 'apple-touch-icon-precomposed', 'mask-icon'];

    const apply = Reflect.apply, describe = Object.getOwnPropertyDescriptor, freeze = Object.freeze, dictionary = Object.create;
    const stringify = JSON.stringify, Json = JSON, floor = Math.floor, min = Math.min, max = Math.max;
    const query = window.jasperBridgeQuery, later = window.setTimeout, Urls = window.URL;
    const Documents = window.Document, Nodes = window.Node, Elements = window.Element, Types = window.DocumentType,
      Lists = window.NodeList;
    const constructors = [Documents, Nodes, Elements, Types, Lists, Urls];
    if (typeof query !== 'function' || typeof later !== 'function') return null;
    for (let at = 0; at < constructors.length; at++) if (typeof constructors[at] !== 'function') return null;
    const getter = (type, name) => {
      const property = describe(type.prototype, name);
      return property && typeof property.get === 'function' ? property.get : null;
    };
    const setter = (type, name) => {
      const property = describe(type.prototype, name);
      return property && typeof property.set === 'function' ? property.set : null;
    };
    const rootOf = getter(Documents, 'documentElement'), doctypeOf = getter(Documents, 'doctype'), headOf = getter(Documents, 'head'),
      createElement = Documents.prototype.createElement;
    const cloneNode = Nodes.prototype.cloneNode, insertBefore = Nodes.prototype.insertBefore, firstChildOf = getter(Nodes, 'firstChild'),
      baseOf = getter(Nodes, 'baseURI'), textOf = getter(Nodes, 'textContent'), setText = setter(Nodes, 'textContent');
    const querySelectorAll = Elements.prototype.querySelectorAll, getAttribute = Elements.prototype.getAttribute,
      setAttribute = Elements.prototype.setAttribute, removeAttribute = Elements.prototype.removeAttribute,
      hasAttribute = Elements.prototype.hasAttribute, remove = Elements.prototype.remove, localNameOf = getter(Elements, 'localName'),
      namespaceOf = getter(Elements, 'namespaceURI'), outerOf = getter(Elements, 'outerHTML');
    const nameOf = getter(Types, 'name'), publicOf = getter(Types, 'publicId'), systemOf = getter(Types, 'systemId');
    const lengthOf = getter(Lists, 'length');
    const hrefOf = getter(Urls, 'href'), protocolOf = getter(Urls, 'protocol'), setUser = setter(Urls, 'username'),
      setPassword = setter(Urls, 'password');
    const slice = String.prototype.slice, charCodeAt = String.prototype.charCodeAt, trim = String.prototype.trim,
      toLowerCase = String.prototype.toLowerCase, splitText = String.prototype.split, test = RegExp.prototype.test,
      includes = Array.prototype.includes;
    const page = document;
    const needed = [rootOf, doctypeOf, headOf, createElement, cloneNode, insertBefore, firstChildOf, baseOf, textOf, setText,
      querySelectorAll, getAttribute, setAttribute, removeAttribute, hasAttribute, remove, localNameOf, namespaceOf, outerOf, nameOf,
      publicOf, systemOf, lengthOf, hrefOf, protocolOf, setUser, setPassword];
    for (let at = 0; at < needed.length; at++) if (typeof needed[at] !== 'function') return null;

    /** Takes the snapshot for the save of {@code prefix} and sends it to Jasper; a prefix of no save does nothing. */
    function save(prefix) {
      if (typeof prefix !== 'string' || !apply(test, PREFIX, [prefix])) return;
      let whole = null;
      try {
        whole = apply(stringify, Json, [snapshot(prefix)]);
      } catch (failed) {
        whole = null;
      }
      if (typeof whole !== 'string') {
        send(prefix, [''], 1, 0, 0);
        return;
      }
      const parts = chunks(whole);
      if (parts.length > MOST_PARTS) send(prefix, [''], parts.length, 0, 0);
      else send(prefix, parts, parts.length, 0, 0);
    }

    /** Sends part {@code at} of {@code count}, then, once Jasper answered, the next; a busy bridge is asked again. */
    function send(prefix, parts, count, at, retries) {
      if (at >= parts.length) return;
      const request = apply(stringify, Json, [{op: 'savePage', save: prefix, part: at, parts: count, text: parts[at]}]);
      try {
        apply(query, window, [{request: request, persistent: false,
          onSuccess() {
            send(prefix, parts, count, at + 1, 0);
          },
          onFailure(code) {
            if (code === BUSY && retries < RETRIES) apply(later, window, [() => send(prefix, parts, count, at, retries + 1), RETRY_WAIT]);
          }}]);
      } catch (unsent) {
        // Jasper hears no more; its save ends unanswered.
      }
    }

    /** {@code whole} in parts, each at most LIMIT characters once JSON writes it, no surrogate pair split. */
    function chunks(whole) {
      const parts = [];
      let start = 0;
      while (start < whole.length) {
        let end = min(whole.length, start + LIMIT - 2);
        for (;;) {
          if (end < whole.length && end - start > 1 && high(apply(charCodeAt, whole, [end - 1]))) end--;
          const size = apply(stringify, Json, [apply(slice, whole, [start, end])]).length;
          if (size <= LIMIT) break;
          end = start + max(1, floor((end - start) * (LIMIT - 2) / (size - 2)));
        }
        parts[parts.length] = apply(slice, whole, [start, end]);
        start = end;
      }
      return parts;
    }

    function high(code) {
      return code >= 0xD800 && code <= 0xDBFF;
    }

    /** The snapshot of the document for the save of {@code prefix}. */
    function snapshot(prefix) {
      const base = apply(baseOf, page, []);
      const copy = apply(cloneNode, page, [true]);
      const root = apply(rootOf, copy, []);
      const resources = [], styles = [], attributes = [];
      const numbers = dictionary(null);
      const answer = {prefix: prefix, html: '', resources: resources, styles: styles, attributes: attributes, base: absolute(base, base) || ''};
      if (root === null) return answer;

      /** The token of the resource at {@code value}, of {@code kind}; the absolute address of one Jasper does not fetch. */
      function token(value, kind) {
        if (dataAddress(value)) return value;
        const url = absolute(value, base);
        if (url === null) return value;
        if (!web(url)) return url;
        const key = kind + ' ' + url;
        let number = numbers[key];
        if (number === undefined) {
          number = resources.length;
          resources[number] = {url: url, kind: kind};
          numbers[key] = number;
        }
        return prefix + 'r' + number + '-';
      }

      /** Sets {@code name} of {@code element} to its token; whether there was one to set. */
      function tokenize(element, name, kind) {
        if (!apply(hasAttribute, element, [name])) return false;
        apply(setAttribute, element, [name, token(apply(getAttribute, element, [name]), kind)]);
        return true;
      }

      function tokenizeCandidates(element) {
        if (!apply(hasAttribute, element, ['srcset'])) return false;
        apply(setAttribute, element, ['srcset', candidates(apply(getAttribute, element, ['srcset']), token)]);
        return true;
      }

      const dropped = list(root, 'base, meta[charset], meta[http-equiv]');
      for (let at = 0; at < dropped.length; at++) {
        const element = dropped[at];
        if (apply(localNameOf, element, []) === 'meta' && !apply(hasAttribute, element, ['charset'])) {
          const equiv = lower(apply(trim, apply(getAttribute, element, ['http-equiv']) || '', []));
          if (equiv !== 'content-type' && equiv !== 'content-security-policy') continue;
        }
        apply(remove, element, []);
      }

      const saved = list(root, SAVED);
      for (let at = 0; at < saved.length; at++) {
        const element = saved[at];
        if (apply(namespaceOf, element, []) !== XHTML) continue;
        const name = apply(localNameOf, element, []);
        let changed = false;
        if (name === 'img' || name === 'source') {
          changed = tokenize(element, 'src', 'other');
          changed = tokenizeCandidates(element) || changed;
        } else if (name === 'video') {
          changed = tokenize(element, 'src', 'other');
          changed = tokenize(element, 'poster', 'other') || changed;
        } else if (name === 'audio' || name === 'track') {
          changed = tokenize(element, 'src', 'other');
        } else if (name === 'input') {
          if (lower(apply(trim, apply(getAttribute, element, ['type']) || '', [])) === 'image') changed = tokenize(element, 'src', 'other');
        } else if (name === 'script') {
          changed = tokenize(element, 'src', 'script');
        } else if (name === 'link') {
          const rel = words(apply(getAttribute, element, ['rel']) || '');
          if (apply(includes, rel, ['stylesheet'])) changed = tokenize(element, 'href', 'style');
          else if (icon(rel)) changed = tokenize(element, 'href', 'other');
        }
        if (changed) {
          apply(removeAttribute, element, ['integrity']);
          apply(removeAttribute, element, ['crossorigin']);
        }
      }

      const linked = list(root, '[href], [src], [action], [formaction]');
      for (let at = 0; at < linked.length; at++) {
        const element = linked[at];
        for (let name = 0; name < ABSOLUTE.length; name++) {
          if (!apply(hasAttribute, element, [ABSOLUTE[name]])) continue;
          const value = apply(getAttribute, element, [ABSOLUTE[name]]);
          if (apply(slice, value, [0, prefix.length]) === prefix || dataAddress(value) || apply(slice, apply(trim, value, []), [0, 1]) === '#')
            continue;
          const url = absolute(value, base);
          if (url !== null) apply(setAttribute, element, [ABSOLUTE[name], url]);
        }
      }

      const sheets = list(root, 'style');
      for (let at = 0; at < sheets.length; at++) {
        const element = sheets[at];
        if (apply(namespaceOf, element, []) !== XHTML) continue;
        styles[styles.length] = apply(textOf, element, []);
        apply(setText, element, [prefix + 's' + (styles.length - 1) + '-']);
      }
      const styled = list(root, '[style]');
      for (let at = 0; at < styled.length; at++) {
        attributes[attributes.length] = apply(getAttribute, styled[at], ['style']);
        apply(setAttribute, styled[at], ['style', prefix + 'a' + (attributes.length - 1) + '-']);
      }

      let head = apply(headOf, copy, []);
      if (head === null) {
        head = apply(createElement, copy, ['head']);
        apply(insertBefore, root, [head, apply(firstChildOf, root, [])]);
      }
      const charset = apply(createElement, copy, ['meta']);
      apply(setAttribute, charset, ['charset', 'utf-8']);
      apply(insertBefore, head, [charset, apply(firstChildOf, head, [])]);

      answer.html = doctype(apply(doctypeOf, page, [])) + apply(outerOf, root, []);
      return answer;
    }

    /** The elements under {@code root} that {@code selector} matches, as an array. */
    function list(root, selector) {
      const found = apply(querySelectorAll, root, [selector]);
      const count = apply(lengthOf, found, []);
      const elements = [];
      for (let at = 0; at < count; at++) elements[at] = found[at];
      return elements;
    }

    /**
     * {@code srcset} with each candidate's address replaced by what {@code replace} gives it, its descriptors kept, as
     * HTML's srcset parsing reads candidates: white space and commas before each, its address up to white space, trailing
     * commas no part of it, and its descriptors up to a comma outside parentheses.
     */
    function candidates(srcset, replace) {
      const written = [];
      let at = 0;
      const n = srcset.length;
      while (at < n) {
        while (at < n && (space(srcset, at) || apply(charCodeAt, srcset, [at]) === 44)) at++;
        if (at >= n) break;
        let end = at;
        while (end < n && !space(srcset, end)) end++;
        let url = apply(slice, srcset, [at, end]);
        let descriptors = '';
        if (apply(charCodeAt, url, [url.length - 1]) === 44) {
          let last = url.length;
          while (last > 0 && apply(charCodeAt, url, [last - 1]) === 44) last--;
          url = apply(slice, url, [0, last]);
          at = end;
        } else {
          let close = end, depth = 0;
          while (close < n) {
            const code = apply(charCodeAt, srcset, [close]);
            if (code === 40) depth++;
            else if (code === 41 && depth > 0) depth--;
            else if (code === 44 && depth === 0) break;
            close++;
          }
          descriptors = apply(trim, apply(slice, srcset, [end, close]), []);
          at = close;
        }
        if (url.length > 0) written[written.length] = replace(url, 'other') + (descriptors.length > 0 ? ' ' + descriptors : '');
      }
      let text = '';
      for (let each = 0; each < written.length; each++) text += (each > 0 ? ', ' : '') + written[each];
      return text;
    }

    function space(text, at) {
      const code = apply(charCodeAt, text, [at]);
      return code === 32 || code === 9 || code === 10 || code === 12 || code === 13;
    }

    /** {@code value} resolved against {@code base}, without user name or password; null for none. */
    function absolute(value, base) {
      if (typeof value !== 'string') return null;
      try {
        const url = new Urls(apply(trim, value, []), base);
        apply(setUser, url, ['']);
        apply(setPassword, url, ['']);
        return apply(hrefOf, url, []);
      } catch (unreadable) {
        return null;
      }
    }

    function web(url) {
      try {
        const protocol = apply(protocolOf, new Urls(url), []);
        return protocol === 'http:' || protocol === 'https:';
      } catch (unreadable) {
        return false;
      }
    }

    function dataAddress(value) {
      return lower(apply(slice, apply(trim, value, []), [0, 5])) === 'data:';
    }

    function lower(text) {
      return apply(toLowerCase, text, []);
    }

    /** {@code text}'s words, in lower case, as rel lists them. */
    function words(text) {
      const found = [];
      const all = apply(splitText, lower(text), [/[ \t\n\f\r]+/]);
      for (let at = 0; at < all.length; at++) if (all[at].length > 0) found[found.length] = all[at];
      return found;
    }

    function icon(rel) {
      for (let at = 0; at < ICONS.length; at++) if (apply(includes, rel, [ICONS[at]])) return true;
      return false;
    }

    /** The doctype {@code type} as HTML writes it, then a line break; nothing for none. */
    function doctype(type) {
      if (type === null || type === undefined) return '';
      const publicId = apply(publicOf, type, []), systemId = apply(systemOf, type, []);
      let text = '<!DOCTYPE ' + apply(nameOf, type, []);
      if (publicId) text += ' PUBLIC "' + publicId + '"';
      if (systemId) text += (publicId ? '' : ' SYSTEM') + ' "' + systemId + '"';
      return text + '>\n';
    }

    return freeze({save: save});
  }
})
