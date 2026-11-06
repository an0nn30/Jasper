/*
 * jasper-about:welcome: Top Sites, the sites visited most, asked for when the page loads and again by Refresh. Each is a
 * link tile of its title, or its host when it has none, over its host. A title or an address only ever goes in as text,
 * through jasper.el.
 */
(function () {
  'use strict';
  const sites = document.getElementById('sites');
  const none = document.getElementById('no-sites');
  const failure = document.getElementById('failure');
  const refresh = document.getElementById('refresh');
  // Each request counts up: only the latest one's answer shows.
  let asked = 0;

  function tile(site) {
    return jasper.el('li', {}, jasper.el('a', {class: 'tile', href: site.url},
      jasper.el('div', {class: 'title'}, site.title || site.host),
      jasper.el('div', {class: 'address'}, site.host)));
  }

  function show() {
    const ticket = ++asked;
    jasper.ask({op: 'top-sites'}).then(function (answer) {
      if (ticket !== asked) return;
      failure.hidden = true;
      sites.replaceChildren(...answer.sites.map(tile));
      none.hidden = answer.sites.length > 0;
    }, function (error) {
      if (ticket !== asked) return;
      failure.textContent = error.message;
      failure.hidden = false;
    });
  }

  refresh.addEventListener('click', show);
  show();
})();
