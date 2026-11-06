/*
 * jasper-about:history (spec section 10): Jasper's browsing history, newest first, grouped under Today, Yesterday or
 * the day, 50 entries at a time. The search asks again 300 ms after the typing stops, as iTerm2's page does; Load More
 * continues the search shown; each entry's Delete, and Clear All History, ask first. A title or an address only ever
 * goes in as text, through jasper.el.
 */
(function () {
  'use strict';
  const DAY = {weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'};
  const TIME = {hour: '2-digit', minute: '2-digit'};
  const search = document.getElementById('search');
  const clear = document.getElementById('clear');
  const failure = document.getElementById('failure');
  const entries = document.getElementById('entries');
  const empty = document.getElementById('empty');
  const more = document.getElementById('more');
  // The search whose entries show, and where Load More continues it: null after its last page.
  let shown = '';
  let next = null;
  // Each request for a page of entries counts up: only the latest one's answer shows.
  let asked = 0;
  // The day the last entry shown is on, and the list of that day's group.
  let lastDay = '';
  let lastList = null;

  /** Asks for a page of entries: the first of the search in the field, or, when continued, the next of the one shown. */
  function ask(continued) {
    const ticket = ++asked;
    const words = continued ? shown : search.value;
    const request = {op: 'list', query: words};
    if (continued) request.before = next;
    more.disabled = true;
    jasper.ask(request).then(function (answer) {
      if (ticket !== asked) return;
      if (!continued) {
        entries.replaceChildren();
        lastDay = '';
        lastList = null;
        shown = words;
      }
      failed('');
      for (const entry of answer.entries) add(entry);
      next = typeof answer.next === 'string' ? answer.next : null;
      more.hidden = next === null;
      more.disabled = false;
      showEmpty();
    }, function (error) {
      if (ticket !== asked) return;
      more.disabled = false;
      failed(error.message);
    });
  }

  /** The entry's row, under its day's heading, which is made with the day's first row. */
  function add(entry) {
    const when = new Date(entry.time);
    const day = when.toDateString();
    if (day !== lastDay || lastList === null) {
      lastDay = day;
      lastList = jasper.el('ul', {class: 'list'});
      entries.append(jasper.el('section', {}, jasper.el('h2', {}, heading(when)), lastList));
    }
    const remove = jasper.el('button', {type: 'button', class: 'icon', 'aria-label': 'Delete', title: 'Delete'}, '×');
    const row = jasper.el('li', {class: 'row'},
      jasper.el('span', {class: 'time'}, when.toLocaleTimeString([], TIME)),
      jasper.el('div', {class: 'grow'},
        jasper.el('div', {class: 'title'}, entry.title || 'Untitled'),
        jasper.el('a', {class: 'address', href: entry.url}, entry.url)),
      remove);
    remove.addEventListener('click', function () { forget(entry.id, row); });
    lastList.append(row);
  }

  /** Today, Yesterday, or the day written out, as the user's locale writes it. */
  function heading(when) {
    const today = new Date();
    const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
    if (when.toDateString() === today.toDateString()) return 'Today';
    if (when.toDateString() === yesterday.toDateString()) return 'Yesterday';
    return when.toLocaleDateString(undefined, DAY);
  }

  /** Deletes one entry once the user agrees, and its row, and its day's heading with its last row. */
  function forget(id, row) {
    if (!window.confirm('Delete this history entry?')) return;
    jasper.ask({op: 'delete', id: id}).then(function () {
      const list = row.parentElement;
      row.remove();
      if (list !== null && list.childElementCount === 0) {
        list.parentElement.remove();
        if (list === lastList) {
          lastDay = '';
          lastList = null;
        }
      }
      failed('');
      showEmpty();
    }, function (error) {
      failed(error.message);
    });
  }

  /** "No browsing history" while no entry shows and none is left to load. */
  function showEmpty() {
    empty.hidden = entries.childElementCount > 0 || next !== null;
  }

  /** Says why the last request failed; nothing for none. */
  function failed(reason) {
    failure.textContent = reason;
    failure.hidden = reason === '';
  }

  clear.addEventListener('click', function () {
    if (!window.confirm('This will delete all browsing history. This action cannot be undone. Continue?')) return;
    jasper.ask({op: 'clear'}).then(function () { ask(false); }, function (error) { failed(error.message); });
  });
  search.addEventListener('input', jasper.debounce(function () { ask(false); }, 300));
  more.addEventListener('click', function () { ask(true); });
  ask(false);
})();
