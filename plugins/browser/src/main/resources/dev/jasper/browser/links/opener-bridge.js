/*
 * The Browser's opener bridge. Chromium cannot host a popup together with its opener in Jasper, so Jasper opens a
 * page's window.open as an item of its own, and this script stands in for the window each page would hold of the other.
 * Jasper runs it in a page's top frame, never in a sub-frame, as each document starts loading: this function, called
 * with {} in every page, and with {opener: "<link id>", closed: <boolean>} in a popup Jasper opened for a window.open.
 *
 * - In every page, window.open asks Jasper, through jasperBridgeQuery, to open the address in a new tab next to this
 *   page's, and returns a stand-in for the popup with closed, close(), focus() and postMessage(). Without the user's
 *   activation it returns null, as a blocked popup does; Jasper keeps its own record of the activation and decides.
 *   With noopener or noreferrer it opens the tab without a link and returns null. _self, _parent and _top keep the
 *   page's own window.open.
 * - In a popup, window.opener is a stand-in for the opener with closed, focus() and postMessage(), and window.close()
 *   asks Jasper to close the popup's item.
 * - A message must be JSON, at most 1 MiB of it. Jasper checks its target origin against the receiving document's
 *   origin, and the receiver gets a message event from the sender's origin whose source is its stand-in for the sender.
 *   Origins are the documents' own (window.origin), which a sandbox can make opaque ("null").
 *
 * Running it again in the same document only applies the configuration again. A popup's location and document cannot
 * be read through its stand-in.
 */
(function (configuration) {
  'use strict';
  const KEY = Symbol.for('dev.jasper.browser.opener-bridge');
  const existing = window[KEY];
  if (existing) {
    existing.configure(configuration);
    return;
  }
  const bridge = make();
  try {
    Object.defineProperty(window, KEY, {value: bridge});
  } catch (taken) {
    return;
  }
  bridge.configure(configuration);

  function make() {
    const LIMIT = 1048576;
    const query = window.jasperBridgeQuery;
    const nativeOpen = window.open;
    const nativeClose = window.close;
    // This document's stand-ins for its popups, by link id, and in a popup the link to its opener.
    const popups = new Map();
    let opener = null;

    function ask(request, answered, refused) {
      if (typeof query !== 'function') {
        if (refused) refused('Jasper cannot reach this page.');
        return;
      }
      query({
        request: JSON.stringify(request),
        persistent: false,
        onSuccess(answer) {
          if (answered) answered(answer);
        },
        onFailure(code, reason) {
          if (refused) refused(reason);
        }
      });
    }

    function warn(reason) {
      console.warn('Jasper did not deliver the message: ' + reason);
    }

    function link(id, closed) {
      return {id, closed, closeAsked: false, window: null};
    }

    // A stand-in for the other page's window; a popup's can be closed.
    function standIn(entry, popup) {
      const other = {
        get closed() {
          return entry.closed;
        },
        focus() {
          if (entry.id !== null && !entry.closed) ask({op: 'focus', id: entry.id});
        },
        postMessage(message, options) {
          post(entry, message, options);
        }
      };
      if (popup) {
        other.close = function close() {
          if (entry.closed) return;
          if (entry.id === null) entry.closeAsked = true;
          else ask({op: 'close', id: entry.id});
        };
      }
      return Object.freeze(other);
    }

    function post(entry, message, options) {
      let target = options !== null && typeof options === 'object' ? options.targetOrigin : options;
      target = target === undefined ? '/' : String(target);
      if (target === '/') {
        target = window.origin;
      } else if (target !== '*') {
        try {
          target = new URL(target).origin;
        } catch (invalid) {
          throw new DOMException("Failed to execute 'postMessage': Invalid target origin '" + target + "'.", 'SyntaxError');
        }
      }
      if (entry.closed || entry.id === null) return;
      let data;
      try {
        data = JSON.stringify(message);
      } catch (unserialisable) {
        data = undefined;
      }
      if (typeof data !== 'string') {
        warn('Jasper delivers only messages that JSON can hold.');
        return;
      }
      if (data.length > LIMIT) {
        warn('Jasper delivers messages of up to 1 MiB of JSON.');
        return;
      }
      ask({op: 'post', id: entry.id, targetOrigin: target, data}, null, warn);
    }

    // Whether window.open's features ask for noopener or noreferrer, read as the HTML standard reads them.
    function detached(features) {
      const text = features === undefined ? '' : String(features).toLowerCase();
      const separator = character => '=, \t\n\f\r'.includes(character);
      const found = new Map();
      let at = 0;
      while (at < text.length) {
        while (at < text.length && separator(text[at])) at++;
        let name = '';
        while (at < text.length && !separator(text[at])) name += text[at++];
        while (at < text.length && text[at] !== '=' && text[at] !== ',' && separator(text[at])) at++;
        let value = '';
        if (at < text.length && separator(text[at])) {
          while (at < text.length && separator(text[at]) && text[at] !== ',') at++;
          while (at < text.length && !separator(text[at])) value += text[at++];
        }
        if (name) found.set(name, value);
      }
      const on = value => value === '' || value === 'yes' || value === 'true' || (parseInt(value, 10) || 0) !== 0;
      return (found.has('noopener') && on(found.get('noopener'))) || (found.has('noreferrer') && on(found.get('noreferrer')));
    }

    function open(...args) {
      const [url, target, features] = args;
      const name = target === undefined ? '' : String(target).toLowerCase();
      if (name === '_self' || name === '_parent' || name === '_top') return nativeOpen.apply(window, args);
      let address = 'about:blank';
      if (url !== undefined && url !== '') {
        try {
          address = new URL(String(url), document.baseURI).href;
        } catch (invalid) {
          throw new DOMException("Failed to execute 'open': Unable to open a window with invalid URL '" + url + "'.", 'SyntaxError');
        }
      }
      if (!(navigator.userActivation && navigator.userActivation.isActive)) return null;
      if (detached(features)) {
        ask({op: 'open', url: address, noopener: true});
        return null;
      }
      const entry = link(null, false);
      entry.window = standIn(entry, true);
      ask({op: 'open', url: address}, id => {
        entry.id = id;
        popups.set(id, entry);
        if (entry.closeAsked) ask({op: 'close', id});
      }, () => {
        entry.closed = true;
      });
      return entry.window;
    }

    try {
      window.open = open;
    } catch (fixed) {
      // The page made window.open its own first.
    }

    function configure(given) {
      if (!given || typeof given.opener !== 'string') return;
      if (opener !== null && opener.id === given.opener) {
        if (given.closed === true) opener.closed = true;
        return;
      }
      const entry = link(given.opener, given.closed === true);
      entry.window = standIn(entry, false);
      opener = entry;
      try {
        Object.defineProperty(window, 'opener', {value: entry.window, writable: true, configurable: true, enumerable: true});
        window.close = function close() {
          ask({op: 'close', id: entry.id}, null, () => nativeClose.call(window));
        };
      } catch (fixed) {
        // The page made window.opener or window.close its own first.
      }
    }

    // Jasper delivers a message from the page at the other end of link `id`: its opener, or one of its popups.
    function deliver(id, from, data, origin, target) {
      if (target !== '*' && target !== window.origin) return;
      let source;
      if (from === 'opener') {
        if (opener === null || opener.id !== id) return;
        source = opener.window;
      } else {
        let entry = popups.get(id);
        if (!entry) {
          entry = link(id, false);
          entry.window = standIn(entry, true);
          popups.set(id, entry);
        }
        source = entry.window;
      }
      let value;
      try {
        value = JSON.parse(data);
      } catch (malformed) {
        return;
      }
      const event = new MessageEvent('message', {data: value, origin});
      Object.defineProperty(event, 'source', {value: source});
      window.dispatchEvent(event);
    }

    // Jasper tells that the page at the other end of link `id` closed.
    function closed(id) {
      if (opener !== null && opener.id === id) opener.closed = true;
      const entry = popups.get(id);
      if (entry) entry.closed = true;
    }

    return Object.freeze({configure, deliver, closed});
  }
})
