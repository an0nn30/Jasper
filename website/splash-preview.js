'use strict';
const stages = [
  ['Starting Java runtime…', 12],
  ['Reading configuration…', 24],
  ['Loading plugin: Credential Vault', 40],
  ['Loading plugin: Remote', 58],
  ['Loading plugin: Editor', 72],
  ['Restoring workspace…', 90],
  ['Ready. Make yourself at home.', 100],
];
let current = 0;
let timer;
let mascotTimer;
let pose = 0;
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
// Existing approved sprite frames, kept on exact pixel boundaries.
const poses = [0,0,0,0,1,0,0,0,3,4,3,4,3,0,0,0,0,0,1,0];
function rest() {
  clearInterval(mascotTimer);
  document.querySelector('.mascot').style.backgroundPosition = '0 0';
}
function animate() {
  rest();
  pose = 0;
  if (reducedMotion.matches) return;
  mascotTimer = setInterval(() => {
    if (!document.hidden) {
      document.querySelector('.mascot').style.backgroundPosition = `${-168 * poses[pose++ % poses.length]}px 0`;
    }
  }, 220);
}
reducedMotion.addEventListener('change', () => { if (timer) animate(); });
const play = document.querySelector('#play');
function stop() {
  rest();
  clearInterval(timer);
  timer = undefined;
  play.textContent = current === stages.length - 1 ? 'Replay startup' : 'Play startup';
}
function show() {
  const [label, amount] = stages[current];
  document.querySelector('#status').textContent = label;
  document.querySelector('.fill').style.width = `${amount}%`;
  document.querySelector('.track').setAttribute('aria-valuenow', amount);
}
play.addEventListener('click', () => {
  if (timer) { stop(); return; }
  current = 0;
  show();
  play.textContent = 'Pause startup';
  animate();
  timer = setInterval(() => {
    current++;
    show();
    if (current === stages.length - 1) stop();
  }, 1100);
});
document.querySelector('#next').addEventListener('click', () => {
  current = (current + 1) % stages.length;
  stop();
  show();
});
document.querySelector('#background').addEventListener('click', event => {
  const dark = document.body.classList.toggle('dark');
  event.currentTarget.setAttribute('aria-pressed', dark);
  event.currentTarget.textContent = dark ? 'Light desktop' : 'Dark desktop';
});
function fit() {
  const scale = Math.min(1, (document.querySelector('main').getBoundingClientRect().width - 48) / 720);
  document.querySelector('.viewport').style.width = `${720 * scale}px`;
  document.querySelector('.viewport').style.height = `${440 * scale}px`;
  document.querySelector('.splash').style.transform = `scale(${scale})`;
}
window.addEventListener('resize', fit);
new ResizeObserver(fit).observe(document.querySelector('main'));
fit();
