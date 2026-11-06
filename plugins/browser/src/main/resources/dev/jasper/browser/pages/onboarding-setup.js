/*
 * jasper-about:onboarding-setup: a switch for each setting Jasper offers here, read when the page loads, and Start
 * Browsing, which finishes the onboarding and shows the welcome page. With no switch the note about Settings stays hidden.
 */
(function () {
  'use strict';
  const switches = document.getElementById('switches');
  const note = document.getElementById('note');
  const failure = document.getElementById('failure');
  const finish = document.getElementById('finish');

  function failed(message) {
    failure.textContent = message;
    failure.hidden = message === '';
  }

  function row(setting) {
    const box = jasper.el('input', {type: 'checkbox', id: 'switch-' + setting.id, role: 'switch', checked: setting.on});
    box.addEventListener('change', function () {
      box.disabled = true;
      jasper.ask({op: 'switch', id: setting.id, on: String(box.checked)}).then(function (answer) {
        failed('');
        box.checked = answer.on === true;
        box.disabled = false;
      }, function (error) {
        box.checked = !box.checked;
        box.disabled = false;
        failed(error.message);
      });
    });
    return jasper.el('li', {class: 'feature'}, jasper.el('label', {for: box.id}, box, ' ', jasper.el('strong', {}, setting.title)),
      jasper.el('p', {}, setting.description));
  }

  jasper.ask({op: 'switches'}).then(function (answer) {
    switches.replaceChildren(...answer.switches.map(row));
    note.hidden = answer.switches.length === 0;
  }, function (error) {
    failed(error.message);
  });

  finish.addEventListener('click', function () {
    finish.disabled = true;
    jasper.ask({op: 'finish'}).then(function () {
      location.replace('jasper-about:welcome');
    }, function (error) {
      finish.disabled = false;
      failed(error.message);
    });
  });
})();
