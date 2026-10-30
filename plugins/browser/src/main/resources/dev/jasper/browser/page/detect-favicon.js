/*
 * The Browser's favicon detector. Chromium tells Jasper nothing of a page's favicon, so Jasper runs this in a page's top
 * frame, never in a sub-frame, as each document starts loading. Once the document is parsed it tells Jasper, through
 * jasperBridgeQuery (op "favicon"), the absolute address of the icon the document names: its first
 * <link rel~="icon"> or <link rel="mask-icon"> with an address, else /favicon.ico at the document's origin. Jasper
 * fetches only the http and https icons of http and https documents, so it sends nothing else, and such a page shows the
 * globe.
 */
(function () {
  'use strict';
  const query = window.jasperBridgeQuery;
  if (typeof query !== 'function') return;

  function web(address) {
    return address.startsWith('http:') || address.startsWith('https:');
  }

  function icon() {
    for (const link of document.querySelectorAll('link[rel~="icon" i], link[rel="mask-icon" i]')) {
      if ((link.getAttribute('href') || '').trim() !== '') return link.href;
    }
    return location.origin + '/favicon.ico';
  }

  function report() {
    if (!web(location.href)) return;
    let address = '';
    try {
      address = icon();
    } catch (unreadable) {
      return;
    }
    if (!web(address)) return;
    query({
      request: JSON.stringify({op: 'favicon', url: address}),
      persistent: false,
      onSuccess() {},
      onFailure() {}
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', report, {once: true});
  else report();
})();
