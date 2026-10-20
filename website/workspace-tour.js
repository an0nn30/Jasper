// Local walkthrough: genuine app typing captures; the pointer illustrates tab/rail actions.
(() => {
  const root = document.querySelector('#workspace');
  const link = document.querySelector('#plugin-image-link');
  const image = document.querySelector('#plugin-image');
  const next = document.querySelector('#plugin-image-next');
  const caption = document.querySelector('#plugin-caption');
  const pointer = root.querySelector('.demo-pointer');
  const toggle = document.querySelector('#workspace-motion');
  const buttons = [...document.querySelectorAll('[data-workflow]')];
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const views = {
    editor: ['Make a quick edit, with syntax highlighting right where you work.', 'Jasper Meadow workspace with moss selected session, deployment.yaml in Editor, wider rails and taller tabs and status bar'],
    terminal: ['Type a command. See the result. Your sessions stay close by.', 'Jasper terminal showing sample infrastructure output beside saved demo sessions'],
    focus: ['A little more room. Tuck a panel away, then bring it back.', 'Jasper terminal expanded across the workspace, with Sessions available from the left rail']
  };
  const capture = name => `assets/workspace-typing/${name}.webp?v=hires-2`;
  const defaults = {editor: 'editor-base', terminal: 'terminal-result', focus: 'focus'};
  let current = 'editor', currentFrame = '', elapsed = 0, last = 0, raf = 0, visible = false;
  let paused = reduced.matches, ready = false, loading = false, swapping = 0, generation = 0;
  function show(key, animate = true, frame = defaults[key]) {
    if (frame === currentFrame) return;
    const changedView = key !== current;
    current = key; currentFrame = frame;
    const url = capture(frame);
    clearTimeout(swapping); generation++;
    const token = generation;
    next.style.opacity = '0';
    if (animate && changedView && !reduced.matches) {
      next.src = url;
      next.style.opacity = '1';
      swapping = setTimeout(() => {
        if (token !== generation) return;
        image.src = url; next.style.transition = 'none'; next.style.opacity = '0';
        requestAnimationFrame(() => { next.style.transition = ''; });
      }, 230);
    } else { image.src = url; }
    image.alt = views[key][1]; link.href = url; caption.textContent = views[key][0];
    buttons.forEach(b => b.setAttribute('aria-pressed', String(b.dataset.workflow === key)));
  }
  // Character frames are real captures, shown without crossfading so text stays crisp.
  const typedFrame = (prefix, index) => `${prefix}-${String(index).padStart(2, '0')}`;
  const duration = 28000;
  const points = [[0,80,37],[900,80,37],[2100,38,13],[6000,38,13],
    [7100,38,13],[7800,50,3.5],[8600,50,3.5],[9200,39,82],
    [15500,39,82],[17500,2.2,5],[19200,2.2,5],[20000,65,60],
    [20900,65,60],[21800,2.2,5],[22800,2.2,5],[23800,80,3.5],
    [25300,80,3.5],[26600,80,37],[28000,80,37]];
  function tick(now) {
    if (!last) last = now;
    elapsed = (elapsed + Math.min(now-last, 100)) % duration; last = now;
    const t = elapsed;
    if (t < 2500) show('editor', true, 'editor-base');
    else if (t < 5500) show('editor', false, typedFrame('editor', Math.min(14, Math.floor((t-2500)/200))));
    else if (t < 8000) show('editor', false, 'editor-14');
    else if (t < 9500) show('terminal', true, 'terminal-00');
    else if (t < 12700) show('terminal', false, typedFrame('terminal', Math.min(16, Math.floor((t-9500)/200))));
    else if (t < 13200) show('terminal', false, 'terminal-16');
    else if (t < 18000) show('terminal', false, 'terminal-result');
    else if (t < 22000) show('focus');
    else if (t < 24000) show('terminal');
    else show('editor', true, 'editor-14');
    let i = 0; while (i < points.length-2 && t > points[i+1][0]) i++;
    const a=points[i], b=points[i+1], f=(t-a[0])/(b[0]-a[0]);
    const ease=f*f*(3-2*f);
    pointer.style.left=`${a[1]+(b[1]-a[1])*ease}%`;
    pointer.style.top=`${a[2]+(b[2]-a[2])*ease}%`;
    pointer.classList.toggle('is-clicking',[2200,7850,9300,17850,21850,23850].some(c => t>=c && t<c+400));
    raf=requestAnimationFrame(tick);
  }
  const frames = [...Object.values(defaults),
    ...Array.from({length:15},(_,i)=>typedFrame('editor',i)),
    ...Array.from({length:17},(_,i)=>typedFrame('terminal',i))];
  let loaded = 0;
  const wantsPlayback = () => visible && !document.hidden && !paused && !reduced.matches;
  async function preparePlayback() {
    if (loading || ready) return;
    loading = true;
    try {
      // Bound decode work; static/reduced-motion visitors only load their chosen still.
      while (loaded < frames.length && wantsPlayback()) {
        const batch = frames.slice(loaded, loaded + 2);
        await Promise.all(batch.map(frame => {
          const img = new Image(); img.src = capture(frame); return img.decode();
        }));
        loaded += batch.length;
      }
      ready = loaded === frames.length;
    } catch { paused = true; }
    finally { loading = false; sync(); }
  }
  function sync() {
    cancelAnimationFrame(raf); last=0;
    const running = ready && wantsPlayback();
    if (!ready && wantsPlayback()) preparePlayback();
    pointer.style.opacity = running ? '1' : '0';
    caption.setAttribute('aria-live', running ? 'off' : 'polite');
    toggle.hidden = reduced.matches;
    toggle.textContent = paused ? 'Play tour' : 'Pause tour';
    toggle.setAttribute('aria-pressed',String(paused));
    if (running) raf=requestAnimationFrame(tick);
  }
  buttons.forEach(b => b.addEventListener('click', () => {paused=true;show(b.dataset.workflow,false);sync();}));
  toggle.addEventListener('click',()=>{paused=!paused;if(!paused){elapsed=0;show('editor',false);}sync();});
  new IntersectionObserver(([entry])=>{visible=entry.isIntersecting;sync();},{threshold:.35}).observe(root);
  document.addEventListener('visibilitychange',sync);
  reduced.addEventListener('change',()=>{if(reduced.matches)paused=true;sync();});
  sync();
})();
