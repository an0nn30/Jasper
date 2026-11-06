/*
 * jasper-about:bookmarks (spec section 10): Jasper's bookmarks, 50 at a time, searched 300 ms after the typing stops,
 * sorted by date added, title or address, and filtered by the tags chosen (a bookmark must have every one). Load More
 * continues what is shown; each bookmark's delete, and Clear All, ask first. A title, address or tag only ever goes in
 * as text, through jasper.el.
 */
(function () {
  'use strict';
  const DATE = {year: 'numeric', month: 'short', day: 'numeric'};
  const search = document.getElementById('search');
  const sort = document.getElementById('sort');
  const clear = document.getElementById('clear');
  const tagbar = document.getElementById('tagbar');
  const tagList = document.getElementById('tags');
  const failure = document.getElementById('failure');
  const list = document.getElementById('bookmarks');
  const empty = document.getElementById('empty');
  const more = document.getElementById('more');
  // The tags chosen, and how many bookmarks show, so that Load More asks for the next ones of the same request.
  const chosen = new Set();
  let shownCount = 0;
  let hasMore = false;
  // Each request counts up: only the latest one's answer shows.
  let asked = 0;

  /** Asks for a page: the first of what the fields say, or, when continued, the one after those shown. */
  function ask(continued) {
    const ticket = ++asked;
    const request = {
      op: 'list', query: search.value, sort: sort.value, tags: Array.from(chosen),
      offset: String(continued ? shownCount : 0)
    };
    more.disabled = true;
    jasper.ask(request).then(function (answer) {
      if (ticket !== asked) return;
      // The answer is filtered by a tag no bookmark has any more: the list is asked for again without it.
      if (letGo(answer.tags)) {
        ask(false);
        return;
      }
      if (!continued) {
        list.replaceChildren();
        shownCount = 0;
      }
      failed('');
      for (const bookmark of answer.bookmarks) add(bookmark);
      shownCount += answer.bookmarks.length;
      hasMore = answer.more === true;
      more.hidden = !hasMore;
      more.disabled = false;
      showTags(answer.tags);
      showEmpty();
    }, function (error) {
      if (ticket !== asked) return;
      more.disabled = false;
      failed(error.message);
    });
  }

  /** The bookmark's row: its title, its address as a link, its tags and when it was added, and its delete. */
  function add(bookmark) {
    const remove = jasper.el('button', {type: 'button', class: 'icon', 'aria-label': 'Delete', title: 'Delete'}, '×');
    const chips = jasper.el('div', {class: 'chips'}, bookmark.tags.map(function (tag) { return jasper.el('span', {class: 'chip'}, tag); }));
    const row = jasper.el('li', {class: 'row'},
      jasper.el('div', {class: 'grow'},
        jasper.el('div', {class: 'title'}, bookmark.title || bookmark.url),
        jasper.el('a', {class: 'address', href: bookmark.url}, bookmark.url),
        bookmark.tags.length > 0 ? chips : null),
      jasper.el('span', {class: 'time'}, 'Added ' + new Date(bookmark.added).toLocaleDateString(undefined, DATE)),
      remove);
    remove.addEventListener('click', function () { forget(bookmark.id, row); });
    list.append(row);
  }

  /** Lets go of each chosen tag that no bookmark has any more, of those in tags: whether any was. */
  function letGo(tags) {
    let any = false;
    for (const tag of Array.from(chosen)) {
      if (!tags.includes(tag)) {
        chosen.delete(tag);
        any = true;
      }
    }
    return any;
  }

  /** A toggle chip for each tag any bookmark has. */
  function showTags(tags) {
    tagList.replaceChildren();
    for (const tag of tags) {
      const chip = jasper.el('button', {type: 'button', class: 'chip', 'aria-pressed': chosen.has(tag) ? 'true' : 'false'}, tag);
      chip.addEventListener('click', function () {
        if (chosen.has(tag)) chosen.delete(tag);
        else chosen.add(tag);
        ask(false);
      });
      tagList.append(chip);
    }
    tagbar.hidden = tags.length === 0;
  }

  /** Deletes one bookmark once the user agrees, and its row. */
  function forget(id, row) {
    if (!window.confirm('Delete this bookmark?')) return;
    jasper.ask({op: 'delete', id: id}).then(function () {
      row.remove();
      shownCount = Math.max(0, shownCount - 1);
      failed('');
      showEmpty();
    }, function (error) {
      failed(error.message);
    });
  }

  /** "No bookmarks found" while no bookmark shows and none is left to load. */
  function showEmpty() {
    empty.hidden = list.childElementCount > 0 || hasMore;
  }

  /** Says why the last request failed; nothing for none. */
  function failed(reason) {
    failure.textContent = reason;
    failure.hidden = reason === '';
  }

  clear.addEventListener('click', function () {
    if (!window.confirm('This will delete all bookmarks. This action cannot be undone. Continue?')) return;
    jasper.ask({op: 'clear'}).then(function () { ask(false); }, function (error) { failed(error.message); });
  });
  search.addEventListener('input', jasper.debounce(function () { ask(false); }, 300));
  sort.addEventListener('change', function () { ask(false); });
  more.addEventListener('click', function () { ask(true); });
  ask(false);
})();
