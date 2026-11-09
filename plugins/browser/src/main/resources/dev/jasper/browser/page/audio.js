/*
 * The Browser's audio muting. Chromium has no mute call Jasper can reach, so Jasper runs this in each frame of a page
 * but challenge frames, as each document starts loading, in the page's own world. This function is called with the
 * document's audio mode, which the page's top-level site's stored choice gives: "allow", "block" or "ask"; anything
 * else is "block".
 *
 * - It watches the document's sound: it wraps HTMLMediaElement.prototype.play and its muted property, listens for play,
 *   playing and volumechange in the capture phase, on the window and on each shadow root the page attaches from now on,
 *   reads the media elements already in the document, and tracks every AudioContext the page makes from now on, through
 *   webkitAudioContext too, whether it is AudioContext itself or a constructor of its own.
 * - Muted, every media element is muted, while its muted property reads and keeps what the page set, which unmuting
 *   puts back; one the page unmutes another way is muted again at its next volumechange. Every running audio context is
 *   suspended, and its resume() waits until unmuted; unmuting resumes those the page wants running.
 * - The document's first audible playback, an element the page did not mute with a volume above 0, or a context
 *   running, is reported to Jasper once, through jasperPrivacyQuery (op "audio.playing"): in "ask" the frame mutes
 *   itself first, and in "block" it is muted from the start.
 * - window[Symbol.for('dev.jasper.browser.audio')].setMuted(muted), which Jasper runs in each frame, mutes the frame or
 *   plays its sound from then on. That entry can be neither replaced nor removed.
 *
 * The muting is cooperative: the page's own scripts can call setMuted too, and an AudioContext made before this ran is
 * not tracked, nor is an element's autoplay in a shadow root made before this ran, or one the parser made from a
 * declarative shadow root. The page function is the one found when this runs, and so are the built-ins it calls, so a
 * page that changes them later can break at most its own muting. Running it again in the same document does nothing.
 */
(function (mode) {
  'use strict';
  const KEY = Symbol.for('dev.jasper.browser.audio');
  // The built-ins this calls, as they are now.
  const apply = Reflect.apply, construct = Reflect.construct;
  const define = Object.defineProperty, describe = Object.getOwnPropertyDescriptor, freeze = Object.freeze;
  const prototypeOf = Object.getPrototypeOf, setPrototypeOf = Object.setPrototypeOf, owns = Object.prototype.hasOwnProperty;
  const WeakMapOf = WeakMap, mapGet = WeakMap.prototype.get, mapSet = WeakMap.prototype.set, mapHas = WeakMap.prototype.has,
    mapDelete = WeakMap.prototype.delete;
  const WeakSetOf = WeakSet, weakAdd = WeakSet.prototype.add, weakHas = WeakSet.prototype.has;
  const SetOf = Set, setAdd = Set.prototype.add, setDelete = Set.prototype.delete, setEach = Set.prototype.forEach,
    setSize = describe(Set.prototype, 'size').get;
  const WeakRefOf = WeakRef, deref = WeakRef.prototype.deref;
  const PromiseOf = Promise, then = Promise.prototype.then;
  const listen = EventTarget.prototype.addEventListener, targetOf = describe(Event.prototype, 'target').get;
  const attachShadow = typeof Element === 'function' ? Element.prototype.attachShadow : undefined;
  const Media = window.HTMLMediaElement;
  if (typeof Media !== 'function' || apply(owns, window, [KEY])) return;
  const media = Media.prototype, play = media.play, mutedOf = describe(media, 'muted');
  const mutedGet = mutedOf.get, mutedSet = mutedOf.set;
  const volumeOf = describe(media, 'volume').get, pausedOf = describe(media, 'paused').get;
  const select = Document.prototype.querySelectorAll, page = document;
  const countOf = describe(NodeList.prototype, 'length').get, itemOf = NodeList.prototype.item;
  const query = window.jasperPrivacyQuery;
  const REPORT = '{"op":"audio.playing"}';
  const nothing = function () { };

  // Whether Jasper mutes the frame; whether its first sound mutes it ("ask"); and whether that sound was reported.
  let silenced = mode !== 'allow' && mode !== 'ask';
  let asking = mode === 'ask';
  let reported = false;
  // Each media element Jasper mutes, and the muted value the page set for it.
  const own = new WeakMapOf();
  // Each element and context seen, and each tracked context's own built-ins; whether a context is to run once unmuted.
  const seen = new WeakSetOf(), natives = new WeakMapOf(), wanted = new WeakMapOf();
  // The constructor that stands in for each wrapped context prototype, which every name of it is given.
  const wrappers = new WeakMapOf();
  const elements = {refs: new SetOf(), limit: 64}, contexts = {refs: new SetOf(), limit: 64};
  // The page's resume() calls that wait until unmuted.
  let waiting = new SetOf();

  try {
    define(window, KEY, {value: freeze({setMuted}), writable: false, enumerable: false, configurable: false});
  } catch (taken) {
    return;
  }

  /** Jasper mutes the frame, or plays its sound, from now on. */
  function setMuted(muted) {
    asking = false;
    mute(muted === true);
  }

  function mute(muted) {
    silenced = muted;
    if (muted) {
      const found = apply(select, page, ['audio, video']);
      const count = apply(countOf, found, []);
      for (let at = 0; at < count; at++) track(apply(itemOf, found, [at]));
      each(elements, force);
      each(contexts, hold);
      return;
    }
    each(elements, release);
    const resumes = waiting;
    waiting = new SetOf();
    apply(setEach, resumes, [function (resume) { resume(); }]);
    each(contexts, resumeWanted);
  }

  /** The document's first audible playback: reported once, an "ask" frame muted first. */
  function heard() {
    if (reported) return;
    reported = true;
    if (asking && !silenced) mute(true);
    asking = false;
    if (typeof query !== 'function') return;
    try {
      apply(query, window, [{request: REPORT, persistent: false, onSuccess: nothing, onFailure: nothing}]);
    } catch (unreachable) {
      // Jasper cannot hear this frame: its sound stays as it is.
    }
  }

  // A list of what this has seen, held weakly, from which the gone are dropped as it is walked or grows.
  function keep(list, target) {
    apply(setAdd, list.refs, [new WeakRefOf(target)]);
    if (apply(setSize, list.refs, []) <= list.limit) return;
    each(list, nothing);
    list.limit = 2 * apply(setSize, list.refs, []) + 64;
  }

  function each(list, visit) {
    apply(setEach, list.refs, [function (ref) {
      const target = apply(deref, ref, []);
      if (target === undefined) apply(setDelete, list.refs, [ref]);
      else visit(target);
    }]);
  }

  function isMedia(target) {
    try {
      apply(pausedOf, target, []);
      return true;
    } catch (notMedia) {
      return false;
    }
  }

  function nativeMuted(element) {
    return apply(mutedGet, element, []);
  }

  /** Whether {@code element} plays sound the page did not mute, at a volume above 0. */
  function audible(element) {
    const pageMuted = apply(mapHas, own, [element]) ? apply(mapGet, own, [element]) : nativeMuted(element);
    return !apply(pausedOf, element, []) && !pageMuted && apply(volumeOf, element, []) > 0;
  }

  function track(element) {
    if (apply(weakHas, seen, [element])) return;
    apply(weakAdd, seen, [element]);
    keep(elements, element);
    // Heard even when the element is in no document, where window's capture listeners hear nothing of it.
    apply(listen, element, ['play', changed]);
    apply(listen, element, ['playing', changed]);
    apply(listen, element, ['volumechange', changed]);
  }

  /** The page uses {@code element}: it is tracked, muted while the frame is, and its sound, if any, heard. */
  function element(target) {
    track(target);
    if (silenced) force(target);
    if (audible(target)) heard();
  }

  function changed(event) {
    const target = apply(targetOf, event, []);
    if (target !== null && isMedia(target)) element(target);
  }

  /** Mutes {@code element}, keeping the muted value the page set; one the page unmuted since keeps false. */
  function force(element) {
    if (!apply(mapHas, own, [element])) apply(mapSet, own, [element, nativeMuted(element)]);
    else if (!nativeMuted(element)) apply(mapSet, own, [element, false]);
    if (!nativeMuted(element)) apply(mutedSet, element, [true]);
  }

  /** Puts back the muted value the page set for {@code element}. */
  function release(element) {
    if (!apply(mapHas, own, [element])) return;
    const value = apply(mapGet, own, [element]);
    apply(mapDelete, own, [element]);
    if (nativeMuted(element) !== value) apply(mutedSet, element, [value]);
  }

  function running(context) {
    return apply(apply(mapGet, natives, [context]).state, context, []) === 'running';
  }

  /** A running context is suspended until unmuted, and then resumed. */
  function hold(context) {
    if (!running(context)) return;
    apply(mapSet, wanted, [context, true]);
    settle(apply(mapGet, natives, [context]).suspend, context);
  }

  function resumeWanted(context) {
    const resume = apply(mapGet, wanted, [context]) === true;
    apply(mapDelete, wanted, [context]);
    if (resume) settle(apply(mapGet, natives, [context]).resume, context);
  }

  /** Runs a context's suspend or resume, a closed context's failure ignored. */
  function settle(change, context) {
    try {
      apply(then, apply(change, context, []), [undefined, nothing]);
    } catch (closed) {
      // A closed context changes no more.
    }
  }

  function contextChanged(context) {
    if (!running(context)) return;
    if (silenced) hold(context);
    heard();
  }

  function getter(prototype, name) {
    for (let at = prototype; at !== null; at = prototypeOf(at)) {
      const found = describe(at, name);
      if (found) return found.get;
    }
    return undefined;
  }

  /** Has the page's {@code name} contexts tracked from now on, through a constructor of the same name in its place. */
  function wrap(name) {
    const Native = window[name];
    if (typeof Native !== 'function' || typeof Native.prototype !== 'object' || Native.prototype === null) return;
    const prototype = Native.prototype;
    // Another name of a constructor wrapped already, as webkitAudioContext very likely is of AudioContext, gets the
    // same stand-in.
    if (apply(mapHas, wrappers, [prototype])) {
      const Context = apply(mapGet, wrappers, [prototype]);
      define(window, name, {value: Context, writable: true, enumerable: false, configurable: true});
      return;
    }
    const resume = prototype.resume, suspend = prototype.suspend, state = getter(prototype, 'state');
    if (typeof resume !== 'function' || typeof suspend !== 'function' || typeof state !== 'function') return;
    const kind = freeze({resume, suspend, state});
    const Context = function () {
      if (new.target === undefined) return apply(Native, this, arguments);
      const context = construct(Native, arguments, new.target);
      apply(mapSet, natives, [context, kind]);
      keep(contexts, context);
      apply(listen, context, ['statechange', function () { contextChanged(context); }]);
      contextChanged(context);
      return context;
    };
    define(Context, 'prototype', {value: prototype, writable: false});
    define(Context, 'name', {value: name, configurable: true});
    define(Context, 'length', {value: Native.length, configurable: true});
    setPrototypeOf(Context, prototypeOf(Native));
    define(prototype, 'constructor', {value: Context, writable: true, enumerable: false, configurable: true});
    define(prototype, 'resume', {value: {resume() {
      if (!silenced || !apply(mapHas, natives, [this])) return apply(resume, this, arguments);
      const context = this;
      apply(mapSet, wanted, [context, true]);
      heard();
      return new PromiseOf(function (resolved, failed) {
        apply(setAdd, waiting, [function () {
          if (apply(mapGet, wanted, [context]) === false) resolved(undefined);
          else apply(then, apply(resume, context, []), [resolved, failed]);
        }]);
      });
    }}.resume, writable: true, enumerable: describe(prototype, 'resume').enumerable, configurable: true});
    define(prototype, 'suspend', {value: {suspend() {
      if (silenced && apply(mapHas, natives, [this])) apply(mapSet, wanted, [this, false]);
      return apply(suspend, this, arguments);
    }}.suspend, writable: true, enumerable: describe(prototype, 'suspend').enumerable, configurable: true});
    apply(mapSet, wrappers, [prototype, Context]);
    define(window, name, {value: Context, writable: true, enumerable: false, configurable: true});
  }

  /** A shadow root the page attaches: what plays in it is heard, as its media events stop at the root. */
  function hearIn(root) {
    apply(listen, root, ['play', changed, true]);
    apply(listen, root, ['playing', changed, true]);
    apply(listen, root, ['volumechange', changed, true]);
  }

  // An element Jasper mutes reads, and keeps, the muted value the page sets; any other is the browser's own.
  const muting = describe({
    get muted() {
      return apply(mapHas, own, [this]) ? apply(mapGet, own, [this]) : apply(mutedGet, this, []);
    },
    set muted(value) {
      if (!silenced || !isMedia(this)) {
        apply(mutedSet, this, [value]);
        return;
      }
      track(this);
      force(this);
      apply(mapSet, own, [this, !!value]);
      if (audible(this)) heard();
    }
  }, 'muted');
  define(media, 'muted', {get: muting.get, set: muting.set, enumerable: mutedOf.enumerable, configurable: true});
  define(media, 'play', {value: {play() {
    const target = isMedia(this) ? this : null;
    if (target !== null) {
      track(target);
      if (silenced) force(target);
    }
    const started = apply(play, this, arguments);
    if (target !== null) element(target);
    return started;
  }}.play, writable: true, enumerable: describe(media, 'play').enumerable, configurable: true});
  if (typeof attachShadow === 'function') {
    const shown = describe(Element.prototype, 'attachShadow').enumerable;
    define(Element.prototype, 'attachShadow', {value: {attachShadow() {
      const root = apply(attachShadow, this, arguments);
      hearIn(root);
      return root;
    }}.attachShadow, writable: true, enumerable: shown, configurable: true});
  }
  wrap('AudioContext');
  wrap('webkitAudioContext');
  apply(listen, window, ['play', changed, true]);
  apply(listen, window, ['playing', changed, true]);
  apply(listen, window, ['volumechange', changed, true]);
  const found = apply(select, page, ['audio, video']);
  const count = apply(countOf, found, []);
  for (let at = 0; at < count; at++) element(apply(itemOf, found, [at]));
})
