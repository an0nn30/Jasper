/* jasper-about:onboarding-features: a row for each thing the Browser can do, asked for when the page loads. */
(function () {
  'use strict';
  const features = document.getElementById('features');
  const failure = document.getElementById('failure');

  jasper.ask({op: 'features'}).then(function (answer) {
    features.replaceChildren(...answer.features.map(function (feature) {
      return jasper.el('li', {class: 'feature'}, jasper.el('h2', {}, feature.title), jasper.el('p', {}, feature.text));
    }));
  }, function (error) {
    failure.textContent = error.message;
    failure.hidden = false;
  });
})();
