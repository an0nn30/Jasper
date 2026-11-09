/*
 * jasper-about:permissions (spec section 12): the site permissions of this tab's store, grouped by website. The search
 * matches a website or a permission's name, ignoring case, and the type and status filters narrow what shows. Each
 * website's Clear forgets its permissions, each permission's × revokes it once the user agrees, and Clear All
 * Permissions forgets every one; Jasper then reloads the tabs that show those websites, and the list is asked for again.
 * A website or a permission only ever goes in as text, through jasper.el.
 */
(function () {
  'use strict';
  const STATUS = new Map([['allowed', 'Allowed'], ['blocked', 'Blocked']]);
  const search = document.getElementById('search');
  const type = document.getElementById('type');
  const status = document.getElementById('status');
  const clear = document.getElementById('clear');
  const failure = document.getElementById('failure');
  const sites = document.getElementById('sites');
  const empty = document.getElementById('empty');
  // Every permission stored, as the last list gave them, by website.
  let grants = [];
  // Each list asked for counts up: only the latest one's answer shows.
  let asked = 0;

  /** Asks for every permission stored, and shows those the search and filters keep. */
  function refresh() {
    const ticket = ++asked;
    jasper.ask({op: 'list'}).then(function (answer) {
      if (ticket !== asked) return;
      grants = answer.grants;
      failed('');
      show();
    }, function (error) {
      if (ticket !== asked) return;
      failed(error.message);
    });
  }

  /** Whether the search and the filters keep the permission. */
  function kept(grant) {
    if (type.value !== '' && grant.type !== type.value) return false;
    if (status.value !== '' && grant.decision !== status.value) return false;
    const words = search.value.trim().toLowerCase();
    return words === '' || grant.origin.toLowerCase().includes(words) || grant.label.toLowerCase().includes(words);
  }

  /** The permissions kept, each website's under its heading, or "No permissions found". */
  function show() {
    sites.replaceChildren();
    let shownOrigin = null;
    let list = null;
    for (const grant of grants) {
      if (!kept(grant)) continue;
      if (grant.origin !== shownOrigin) {
        shownOrigin = grant.origin;
        list = site(grant.origin);
      }
      list.append(row(grant));
    }
    empty.hidden = sites.childElementCount > 0;
  }

  /** A website's heading, with its Clear, and the list its permissions go into. */
  function site(origin) {
    const forget = jasper.el('button', {type: 'button'}, 'Clear');
    forget.addEventListener('click', function () { change({op: 'clear-site', origin: origin}); });
    const list = jasper.el('ul', {class: 'list'});
    sites.append(jasper.el('section', {}, jasper.el('div', {class: 'toolbar'}, jasper.el('h2', {class: 'grow title'}, origin), forget), list));
    return list;
  }

  /** A permission's row: its name, its status and its ×. */
  function row(grant) {
    const revoke = jasper.el('button', {type: 'button', class: 'icon', 'aria-label': 'Revoke', title: 'Revoke'}, '×');
    revoke.addEventListener('click', function () {
      if (!window.confirm('Revoke ' + grant.label + ' permission for ' + grant.origin + '?')) return;
      change({op: 'revoke', origin: grant.origin, type: grant.type});
    });
    return jasper.el('li', {class: 'row'},
      jasper.el('span', {class: 'grow title'}, grant.label),
      jasper.el('span', {class: 'muted'}, STATUS.get(grant.decision) || ''),
      revoke);
  }

  /** Sends a change, then asks for the list again. */
  function change(request) {
    jasper.ask(request).then(refresh, function (error) { failed(error.message); });
  }

  /** Says why the last request failed; nothing for none. */
  function failed(reason) {
    failure.textContent = reason;
    failure.hidden = reason === '';
  }

  clear.addEventListener('click', function () { change({op: 'clear-all'}); });
  search.addEventListener('input', show);
  type.addEventListener('change', show);
  status.addEventListener('change', show);
  refresh();
})();
