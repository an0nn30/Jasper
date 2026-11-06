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
 * The page function is the one found when this script runs, and so are the built-ins the stand-in calls, so a page
 * that changes them later breaks nothing of it; Jasper checks every request itself: a notification shows only when the
 * user allowed the site. Running it again in the same document does nothing.
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
  // The built-ins the stand-in calls, as they are now.
  const apply = Reflect.apply;
  const PromiseOf = Promise, then = Promise.prototype.then, stringify = JSON.stringify, schedule = setTimeout;
  const EventOf = Event, text = String, Failure = Error, Misuse = TypeError;
  const listen = EventTarget.prototype.addEventListener, dispatch = EventTarget.prototype.dispatchEvent;
  const TYPES = ['show', 'close', 'error', 'click'];
  // The answer of the permission request Jasper is asked now, which every call meanwhile shares.
  let asking = null;
  let made = 0;

  function ask(request) {
    return new PromiseOf((resolve, reject) => {
      if (typeof query !== 'function') {
        reject(new Failure('Jasper cannot reach this page.'));
        return;
      }
      query({
        request: stringify(request),
        persistent: false,
        onSuccess: resolve,
        onFailure(code, reason) {
          reject(new Failure(reason));
        }
      });
    });
  }

  function after(promise, answered, failed) {
    return apply(then, promise, [answered, failed]);
  }

  function fire(target, type) {
    schedule(() => apply(dispatch, target, [new EventOf(type)]), 0);
  }

  function known(answer) {
    return answer === 'granted' || answer === 'denied' || answer === 'default';
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
        throw new Misuse("Failed to construct 'Notification': 1 argument required, but only 0 present.");
      }
      this.#title = text(title);
      this.#body = options === undefined || options === null || options.body === undefined ? '' : text(options.body);
      this.#id = text(++made);
      for (let at = 0; at < TYPES.length; at++) {
        const type = TYPES[at];
        apply(listen, this, [type, event => {
          const handler = this.#handlers[type];
          if (handler) apply(handler, this, [event]);
        }]);
      }
      if (permission !== 'granted') {
        this.#refuse();
        return;
      }
      after(ask({op: 'notification.show', id: this.#id, title: this.#title, body: this.#body}), () => {
        if (this.#state !== 'asked') return;
        this.#state = 'shown';
        apply(dispatch, this, [new EventOf('show')]);
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
        const now = permission;
        answer = new PromiseOf(resolve => resolve(now));
      } else {
        if (asking === null) {
          asking = after(after(ask({op: 'notification.request'}), given => {
            if (known(given)) permission = given;
            return permission;
          }, () => permission), given => {
            asking = null;
            return given;
          });
        }
        answer = asking;
      }
      if (typeof callback === 'function') after(answer, given => callback(given));
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
      after(ask({op: 'notification.close', id: this.#id}), undefined, () => {});
      fire(this, 'close');
    }

    #refuse() {
      this.#state = 'refused';
      fire(this, 'error');
    }
  }

  try {
    Object.defineProperty(window, 'Notification', {value: Notification, writable: true, configurable: true, enumerable: false});
  } catch (kept) {
    // The page made Chromium's own permanent first, which CEF denies.
  }
})
