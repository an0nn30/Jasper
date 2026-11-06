/*
 * The Browser's page notifications. Chromium asks no one in Jasper whether a page may show notifications, so Jasper
 * runs this in a page's top frame, never in a sub-frame or a challenge frame, as each document starts loading, in the
 * page's own world: it replaces window.Notification with a stand-in whose notifications are Jasper's. This function is
 * called with the document's permission as Jasper's store holds it for the document's site: "granted", "denied" or
 * "default".
 *
 * - Notification.permission is that permission. Notification.requestPermission(callback) answers it at once unless it
 *   is "default", and otherwise asks Jasper, through jasperPrivacyQuery (op "notification.request"), which asks the
 *   user; its promise resolves, and the callback is called, with the answer. Calls made while Jasper asks share its
 *   answer.
 * - new Notification(title, {body}) asks Jasper to show a notification (op "notification.show") when the permission is
 *   "granted", and fires show once Jasper has; otherwise, or when Jasper does not show it, it fires error. Icons and
 *   the other options are ignored, and click never fires, as Jasper shows the page's tab instead.
 * - close() asks Jasper to take the notification away (op "notification.close") and fires close.
 *
 * The page function is the one found when this script runs, and Jasper checks every request itself: a notification
 * shows only when the user allowed the site. Running it again in the same document does nothing.
 */
(function (permission) {
  'use strict';
  const KEY = Symbol.for('dev.jasper.browser.notifications');
  if (window[KEY]) return;
  try {
    Object.defineProperty(window, KEY, {value: true});
  } catch (taken) {
    return;
  }
  const query = window.jasperPrivacyQuery;
  const ANSWERS = ['granted', 'denied', 'default'];
  // The answer of the permission request Jasper is asked now, which every call meanwhile shares.
  let asking = null;
  let made = 0;

  function ask(request) {
    return new Promise((resolve, reject) => {
      if (typeof query !== 'function') {
        reject(new Error('Jasper cannot reach this page.'));
        return;
      }
      query({
        request: JSON.stringify(request),
        persistent: false,
        onSuccess: resolve,
        onFailure(code, reason) {
          reject(new Error(reason));
        }
      });
    });
  }

  function later(task) {
    setTimeout(task, 0);
  }

  class Notification extends EventTarget {
    #id;
    #title;
    #body;
    // 'asked' until Jasper shows it, then 'shown'; 'refused' or 'closed' after.
    #state = 'asked';
    #handlers = {show: null, close: null, error: null, click: null};

    constructor(title, options) {
      super();
      if (arguments.length === 0) {
        throw new TypeError("Failed to construct 'Notification': 1 argument required, but only 0 present.");
      }
      this.#title = String(title);
      this.#body = options === undefined || options === null || options.body === undefined ? '' : String(options.body);
      this.#id = String(++made);
      for (const type of Object.keys(this.#handlers)) {
        this.addEventListener(type, event => {
          const handler = this.#handlers[type];
          if (handler) handler.call(this, event);
        });
      }
      if (permission !== 'granted') {
        this.#refuse();
        return;
      }
      ask({op: 'notification.show', id: this.#id, title: this.#title, body: this.#body}).then(() => {
        if (this.#state !== 'asked') return;
        this.#state = 'shown';
        this.dispatchEvent(new Event('show'));
      }, () => {
        if (this.#state === 'asked') this.#refuse();
      });
    }

    static get permission() {
      return permission;
    }

    static requestPermission(callback) {
      let answer;
      if (permission !== 'default') {
        answer = Promise.resolve(permission);
      } else {
        if (asking === null) {
          asking = ask({op: 'notification.request'}).then(given => {
            if (ANSWERS.includes(given)) permission = given;
            return permission;
          }, () => permission).then(given => {
            asking = null;
            return given;
          });
        }
        answer = asking;
      }
      if (typeof callback === 'function') answer.then(given => callback(given));
      return answer;
    }

    get title() {
      return this.#title;
    }

    get body() {
      return this.#body;
    }

    get onshow() {
      return this.#handlers.show;
    }

    set onshow(handler) {
      this.#handlers.show = typeof handler === 'function' ? handler : null;
    }

    get onclose() {
      return this.#handlers.close;
    }

    set onclose(handler) {
      this.#handlers.close = typeof handler === 'function' ? handler : null;
    }

    get onerror() {
      return this.#handlers.error;
    }

    set onerror(handler) {
      this.#handlers.error = typeof handler === 'function' ? handler : null;
    }

    get onclick() {
      return this.#handlers.click;
    }

    set onclick(handler) {
      this.#handlers.click = typeof handler === 'function' ? handler : null;
    }

    close() {
      if (this.#state !== 'asked' && this.#state !== 'shown') return;
      this.#state = 'closed';
      ask({op: 'notification.close', id: this.#id}).catch(() => {});
      later(() => this.dispatchEvent(new Event('close')));
    }

    #refuse() {
      this.#state = 'refused';
      later(() => this.dispatchEvent(new Event('error')));
    }
  }

  try {
    Object.defineProperty(window, 'Notification', {value: Notification, writable: true, configurable: true, enumerable: false});
  } catch (kept) {
    // The page made Chromium's own permanent first, which CEF denies.
  }
})
