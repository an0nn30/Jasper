/*
 * What every one of Jasper's own pages (jasper-about:) runs first: this function, called with the page's secret, gives
 * the page's script `jasper`, with three helpers.
 *
 * - jasper.ask(request) sends one request, an object, to Jasper as JSON through jasperAboutQuery, the secret before
 *   it, and returns a Promise of Jasper's answer, parsed. It is rejected with an Error whose message is Jasper's reason
 *   and whose code is Jasper's: 1 when the answer failed, 2 when Jasper refused.
 * - jasper.el(tag, attributes, ...children) makes an element. Text, a page's title or address among it, only ever goes
 *   in as text; an href is kept only for an absolute http:, https:, file: or jasper-about: address, and left off
 *   otherwise; event handler attributes, src, style and the form targets are refused, as are elements that run, frame
 *   or load anything.
 * - jasper.debounce(fn, ms) calls fn once the calls stop for ms milliseconds, with the last call's arguments.
 */
(function (secret) {
  'use strict';
  const query = window.jasperAboutQuery;
  const LINKED = new Set(['http:', 'https:', 'file:', 'jasper-about:']);
  const REFUSED_TAGS = new Set(['script', 'style', 'link', 'meta', 'base', 'iframe', 'frame', 'frameset', 'object', 'embed',
    'template', 'form', 'svg', 'math']);
  const REFUSED_ATTRIBUTES = new Set(['src', 'style', 'action', 'formaction', 'ping']);
  const NAME = /^[a-z][a-z0-9-]*$/;

  function ask(request) {
    return new Promise(function (resolve, reject) {
      if (typeof query !== 'function') {
        reject(failure(2, 'Jasper does not answer this page.'));
        return;
      }
      query({
        request: secret + JSON.stringify(request),
        persistent: false,
        onSuccess(answer) {
          try {
            resolve(JSON.parse(answer));
          } catch (unreadable) {
            reject(failure(1, 'Jasper gave an answer this page cannot read.'));
          }
        },
        onFailure(code, reason) {
          reject(failure(code, reason));
        }
      });
    });
  }

  function failure(code, reason) {
    const error = new Error(String(reason));
    error.code = code;
    return error;
  }

  function linked(address) {
    try {
      return LINKED.has(new URL(String(address)).protocol);
    } catch (notAnAddress) {
      return false;
    }
  }

  function el(tag, attributes, ...children) {
    const name = String(tag).toLowerCase();
    if (!NAME.test(name) || REFUSED_TAGS.has(name)) throw new Error('Jasper pages make no ' + tag + ' element');
    const element = document.createElement(name);
    for (const [key, value] of Object.entries(attributes || {})) {
      const attribute = key.toLowerCase();
      if (!NAME.test(attribute) || attribute.startsWith('on') || REFUSED_ATTRIBUTES.has(attribute))
        throw new Error('Jasper pages set no ' + key + ' attribute');
      if (value === null || value === undefined || value === false) continue;
      if (attribute === 'href') {
        if (linked(value)) element.setAttribute('href', String(value));
        continue;
      }
      element.setAttribute(attribute, value === true ? '' : String(value));
    }
    const kept = children.flat(Infinity).filter(function (child) {
      return child !== null && child !== undefined && child !== false;
    });
    if (kept.every(function (child) { return !(child instanceof Node); })) {
      element.textContent = kept.map(String).join('');
    } else {
      for (const child of kept) element.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return element;
  }

  function debounce(fn, ms) {
    let timer = null;
    return function (...args) {
      if (timer !== null) clearTimeout(timer);
      timer = setTimeout(function () {
        timer = null;
        fn(...args);
      }, ms);
    };
  }

  return Object.freeze({ask, el, debounce});
})
