(function () {
  "use strict";
  var root = document.documentElement;

  // Theme: follows the system until the reader picks one; the choice is remembered when storage allows.
  function stored() { try { return localStorage.getItem("jasper-sdk-theme"); } catch (e) { return null; } }
  function store(value) { try { localStorage.setItem("jasper-sdk-theme", value); } catch (e) { } }
  var saved = stored();
  if (saved === "dark" || saved === "light") root.setAttribute("data-theme", saved);
  function effective() {
    var chosen = root.getAttribute("data-theme");
    if (chosen) return chosen;
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  var themeButton = document.querySelector(".theme");
  if (themeButton) themeButton.addEventListener("click", function () {
    var next = effective() === "dark" ? "light" : "dark";
    root.setAttribute("data-theme", next);
    store(next);
  });

  // Navigation drawer on narrow screens.
  var menu = document.querySelector(".menu");
  var side = document.getElementById("side");
  function closeNav() { document.body.classList.remove("nav-open"); if (menu) menu.setAttribute("aria-expanded", "false"); }
  if (menu) menu.addEventListener("click", function () {
    var open = document.body.classList.toggle("nav-open");
    menu.setAttribute("aria-expanded", open ? "true" : "false");
  });
  if (side) side.addEventListener("click", function (event) { if (event.target.closest("a")) closeNav(); });

  // Opening a type or member reveals its package in the navigation.
  function reveal(hash) {
    if (!hash || hash.length < 2) return;
    var id = decodeURIComponent(hash.slice(1));
    var best = null;
    document.querySelectorAll(".nav-packages details").forEach(function (details) {
      var link = details.querySelector("summary a");
      var pkg = link ? decodeURIComponent(link.getAttribute("href").slice(1)).replace(/^package-/, "") : "";
      if (pkg && (id === "package-" + pkg || id.indexOf(pkg + ".") === 0)) {
        if (!best || pkg.length > best.pkg.length) best = { details: details, pkg: pkg };
      }
    });
    if (best) best.details.open = true;
    document.querySelectorAll(".side a.current").forEach(function (a) { a.classList.remove("current"); });
    var match = null;
    document.querySelectorAll(".side a[href]").forEach(function (a) {
      var target = decodeURIComponent(a.getAttribute("href").slice(1));
      if (id === target || (id.indexOf(target + ".") === 0 && (!match || target.length > match.target.length))) match = { a: a, target: target };
    });
    if (match) match.a.classList.add("current");
  }
  window.addEventListener("hashchange", function () { reveal(location.hash); });
  reveal(location.hash);

  // Search over every package, type and member.
  var input = document.getElementById("search");
  var list = document.getElementById("results");
  var data = [];
  try { data = JSON.parse(document.getElementById("search-index").textContent); } catch (e) { data = []; }
  var selected = -1;
  function score(entry, query) {
    var name = entry[0].toLowerCase();
    var simple = name.replace(/\(.*$/, "");
    var last = simple.slice(simple.lastIndexOf(".") + 1);
    if (last === query || simple === query) return 0;
    if (last.indexOf(query) === 0) return 1;
    if (simple.indexOf(query) === 0) return 2;
    if (last.indexOf(query) >= 0) return 3;
    if (name.indexOf(query) >= 0) return 4;
    if (entry[1].toLowerCase().indexOf(query) >= 0) return 6;
    return -1;
  }
  function escapeHtml(text) {
    return text.replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;" }[c]; });
  }
  function render() {
    var query = input.value.trim().toLowerCase();
    selected = -1;
    if (!query) { list.hidden = true; list.innerHTML = ""; return; }
    var hits = [];
    for (var i = 0; i < data.length; i++) {
      var s = score(data[i], query);
      if (s >= 0) hits.push([s, data[i][0].length, i]);
    }
    hits.sort(function (a, b) { return a[0] - b[0] || a[1] - b[1] || a[2] - b[2]; });
    hits = hits.slice(0, 50);
    if (!hits.length) { list.innerHTML = "<li class=\"empty\">No matches</li>"; list.hidden = false; return; }
    list.innerHTML = hits.map(function (hit, index) {
      var entry = data[hit[2]];
      return "<li role=\"option\" id=\"r" + index + "\"><a href=\"#" + encodeURIComponent(entry[3]).replace(/%2E/g, ".") + "\"><span class=\"rn\">"
        + escapeHtml(entry[0]) + "</span><span class=\"rk\">" + escapeHtml(entry[2]) + "</span><span class=\"rp\">" + escapeHtml(entry[1]) + "</span></a></li>";
    }).join("");
    list.hidden = false;
  }
  function move(delta) {
    var items = list.querySelectorAll("li[role=option]");
    if (!items.length) return;
    if (selected >= 0) items[selected].removeAttribute("aria-selected");
    selected = (selected + delta + items.length) % items.length;
    items[selected].setAttribute("aria-selected", "true");
    items[selected].scrollIntoView({ block: "nearest" });
  }
  if (input && list) {
    input.addEventListener("input", render);
    input.addEventListener("keydown", function (event) {
      if (event.key === "ArrowDown") { event.preventDefault(); move(1); }
      else if (event.key === "ArrowUp") { event.preventDefault(); move(-1); }
      else if (event.key === "Enter") {
        var items = list.querySelectorAll("li[role=option] a");
        var target = items[selected >= 0 ? selected : 0];
        if (target) { event.preventDefault(); location.hash = target.getAttribute("href"); list.hidden = true; input.blur(); }
      } else if (event.key === "Escape") { input.value = ""; render(); input.blur(); }
    });
    list.addEventListener("click", function (event) { if (event.target.closest("a")) { list.hidden = true; closeNav(); } });
    document.addEventListener("click", function (event) { if (!event.target.closest(".search")) list.hidden = true; });
    document.addEventListener("keydown", function (event) {
      if (event.key === "/" && document.activeElement !== input && !/input|textarea/i.test(document.activeElement.tagName)) {
        event.preventDefault();
        input.focus();
        input.select();
      }
    });
  }

  // Copy buttons on code samples.
  document.querySelectorAll("pre.code").forEach(function (pre) {
    var button = document.createElement("button");
    button.type = "button";
    button.className = "copy";
    button.textContent = "Copy";
    button.addEventListener("click", function () {
      var text = pre.querySelector("code").innerText;
      function done(label) { button.textContent = label; setTimeout(function () { button.textContent = "Copy"; }, 1400); }
      if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(function () { done("Copied"); }, function () { done("Failed"); });
      else done("Failed");
    });
    pre.appendChild(button);
  });

  // Highlight the guide section being read.
  if ("IntersectionObserver" in window) {
    var links = {};
    document.querySelectorAll(".nav-guide a").forEach(function (a) { links[a.getAttribute("href").slice(1)] = a; });
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        var link = links[entry.target.id];
        if (!link) return;
        document.querySelectorAll(".nav-guide a.current").forEach(function (a) { a.classList.remove("current"); });
        link.classList.add("current");
      });
    }, { rootMargin: "-20% 0px -70% 0px" });
    document.querySelectorAll("section.guide").forEach(function (section) { observer.observe(section); });
  }
})();
