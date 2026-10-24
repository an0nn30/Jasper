// One continuous meadow, with camera destinations chosen by the section crossing the viewport centre.
// Native scroll snapping handles settling; wheel/touch events and keyboard input are never cancelled.
(() => {
  const button = document.querySelector('.meadow-motion');
  const world = document.querySelector('.meadow-world');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const sections = [...document.querySelectorAll('main > section')];
  const cameras = [
    [0, 0, 1, 0],       // Welcome: a wide view from the meadow's edge.
    [-8, -12, 1.35, -1], // Workspace: move closer into the clearing.
    [11, -25, 1.7, 1],   // Tools: turn toward the flowers.
    [-14, -17, 1.45, -1],// Platforms: look across the hillside.
    [8, -32, 1.9, .5],   // SDK: another step into the foreground.
    [0, -18, 1.25, 0],   // Home: settle into an open clearing.
  ];
  let paused = false;
  let observer;
  let current = 0;
  sections.forEach(section => {
    [...section.children].forEach(child => child.classList.add('scene-content'));
  });
  function moveCamera(index) {
    current = index;
    sections.forEach((section, i) => {
      section.classList.toggle('scene-current', i === index);
      section.classList.toggle('scene-pending', i !== index);
    });
    const [x, y, zoom, roll] = cameras[index];
    world.style.setProperty('--camera-x', `${x}vw`);
    world.style.setProperty('--camera-y', `${y}vh`);
    world.style.setProperty('--camera-zoom', zoom);
    world.style.setProperty('--camera-roll', `${roll}deg`);
  }
  function sync() {
    const anchor = sections[current];
    const previousTop = anchor.getBoundingClientRect().top;
    const active = !paused && !reduced.matches;
    document.documentElement.classList.toggle('meadow-cinematic', active);
    // Keep the section being read in place when the user switches presentation modes.
    window.scrollBy({ top: anchor.getBoundingClientRect().top - previousTop, behavior: 'instant' });
    button.hidden = reduced.matches;
    button.textContent = paused ? 'Resume meadow journey' : 'Use regular scrolling';
    button.setAttribute('aria-pressed', String(paused));
    observer?.disconnect();
    if (!active) {
      sections.forEach(section => section.classList.remove('scene-pending', 'scene-current'));
      return;
    }
    // Observe a thin band at the centre: tall sections stay active while all their content is explored.
    observer = new IntersectionObserver(entries => {
      const entered = entries.filter(entry => entry.isIntersecting);
      if (entered.length) moveCamera(sections.indexOf(entered[entered.length - 1].target));
    }, { rootMargin: '-45% 0px -45% 0px', threshold: 0 });
    sections.forEach(section => observer.observe(section));
    moveCamera(current);
  }
  button.addEventListener('click', () => { paused = !paused; sync(); });
  reduced.addEventListener('change', sync);
  sync();
})();
