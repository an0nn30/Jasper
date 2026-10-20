// One motion language for every segmented view switch.
const activePanelMotions = new Map();
function slidePanel(panel, update) {
 const viewport = panel.parentElement;
 activePanelMotions.get(panel)?.();
 const animate = !reducedMotion.matches && typeof panel.animate === 'function';
 const previousHeight = panel.getBoundingClientRect().height;
 const previous = animate ? panel.cloneNode(true) : null;
 if (previous) {
   if (previous.id) previous.classList.add(`snapshot-${previous.id}`);
   previous.querySelectorAll('[id]').forEach(node => node.classList.add(`snapshot-${node.id}`));
   previous.removeAttribute('id');
   previous.querySelectorAll('[id]').forEach(node => node.removeAttribute('id'));
   previous.querySelectorAll('[aria-live]').forEach(node => node.removeAttribute('aria-live'));
   previous.setAttribute('aria-hidden', 'true');
   previous.inert = true;
   previous.classList.add('tour-outgoing');
 }

 update();
 if (!previous) return;
 const nextHeight = panel.getBoundingClientRect().height;
 viewport.append(previous);
 const easing = 'cubic-bezier(.16, 1, .3, 1)';
 const outgoing = previous.animate([
   { transform: 'translateX(0)', opacity: 1 },
   { transform: 'translateX(-72px)', opacity: 0 }
 ], { duration: 280, easing, fill: 'forwards' });
 const incoming = panel.animate([
   { transform: 'translateX(72px)', opacity: 0 },
   { transform: 'translateX(0)', opacity: 1 }
 ], { duration: 460, easing });
 const resize = viewport.animate([
   { height: `${previousHeight}px` }, { height: `${nextHeight}px` }
 ], { duration: 460, easing });
 let finished = false;
 const cleanup = () => {
   if (finished) return;
   finished = true;
   previous.remove();
   if (activePanelMotions.get(panel) === cleanup) activePanelMotions.delete(panel);
   outgoing.cancel(); incoming.cancel(); resize.cancel();
 };
 activePanelMotions.set(panel, cleanup);
 incoming.finished.then(cleanup, cleanup);
}
const platforms = {
  windows: { name: 'Windows 11', width: 3200, height: 1920, theme: 'Jasper Meadow Light' },
  macos: { name: 'macOS', width: 3200, height: 1920, theme: 'Jasper Meadow Light' },
  linux: { name: 'Ubuntu Linux', width: 3200, height: 1920, theme: 'Jasper Meadow Light' }
};
const image = document.querySelector('#platform-image');
const caption = document.querySelector('#platform-caption');
const buttons = [...document.querySelectorAll('[data-platform]')];
let platformRequest = 0;
let activePlatform = 'windows';
for (const button of buttons) {
  button.addEventListener('click', async () => {
    const request = ++platformRequest;
    const key = button.dataset.platform;
    if (key === activePlatform) return;
    const ready = new Image();
    ready.src = `assets/${key}.png?v=hires-2`;
    try { await ready.decode(); } catch { return; }
    if (request !== platformRequest) return;
    activePlatform = key;
    const platform = platforms[key];
    slidePanel(image.parentElement, () => {
    image.src = `assets/${key}.png?v=hires-2`;
    image.alt = `Jasper on ${platform.name} using ${platform.theme}, showing sample infrastructure commands and output`;
    image.width = platform.width;
    image.height = platform.height;
    caption.textContent = `Jasper on ${platform.name} · ${platform.theme}`;
    for (const other of buttons) other.setAttribute('aria-pressed', String(other === button));
    });
  });
}

const tour = {
 sessions: ['REMOTE / SESSIONS', 'Your next connection, ready.', 'Group your SSH hosts, favorite the ones you use most, and open a session without hunting for connection details.', 'Saved demo hosts', 'Remote Sessions panel with three saved hosts grouped under Staging'],
 sftp: ['REMOTE / SFTP', 'Files, right beside your shell.', 'Browse remote folders, transfer files, and open a remote configuration in the built-in Editor.', 'Sample directory & transfer', 'SFTP demo showing config, logs, deployment.yaml and nginx.conf, with a sample file transfer'],
 tunnels: ['REMOTE / TUNNELS', 'A shorter path to your services.', 'Set up local, remote, or SOCKS forwarding with a clear view of where each connection goes.', 'Saved local forwards · demo configuration', 'Two saved local forwards through Staging gateway: database on port 15432 and metrics on port 13000'],
 sharing: ['FILE SERVER', 'A folder becomes a handoff.', 'Share files over HTTP, SFTP, FTP, and more. Choose the folder, access, and reach that fit the job.', 'Demo folder · server configuration', 'File Server configuration for a demo folder with protocol and access controls'],
 vault: ['CREDENTIAL VAULT', 'Credentials have a home, too.', 'Protect saved credentials with a master password and control which plugins can use them.', 'Fictional accounts · passwords masked', 'Credential Vault with three fictional accounts and Staging gateway selected, showing demo-operator and a masked password']
};
const toolButtons = [...document.querySelectorAll('[data-tool]')];
const tourViewport = document.querySelector('.tour-viewport');
const tourPanel = tourViewport.querySelector('.tool-tour');
let tourRequest = 0;
let activeTool = 'vault';
for (const button of toolButtons) button.addEventListener('click', async () => {
 const request = ++tourRequest;
 const key = button.dataset.tool, view = tour[key];
 if (key === activeTool) return;
 const source = `assets/tour-${key}.png?v=${key === 'sharing' ? 'hires-2' : 'readable-2'}`;
 const ready = new Image();
 ready.src = source;
 try { await ready.decode(); } catch { return; }
 if (request !== tourRequest) return; // A newer click owns the next panel.
 slidePanel(tourPanel, () => {
 activeTool = key;
 const introductions = {
  sessions: 'Start with your connections. Keep your regular hosts close at hand.',
  sftp: 'Now, bring the files along. Browse and transfer without leaving your workspace.',
  tunnels: 'Need to reach a service? Give it a path through your SSH connection.',
  sharing: 'Something to hand over? Turn a folder into a file server.',
  vault: 'Start with your credentials. One protected place, with you in control.'
 };
 document.querySelector('#buddy-tour-line').textContent = introductions[key];
 ['family','title','description','detail'].forEach((name, i) => document.querySelector(`#tour-${name}`).textContent = view[i]);
 const img = document.querySelector('#tour-image');
 img.src = source; img.alt = view[4];
 document.querySelector('#tour-link').href = img.src;
 for (const other of toolButtons) other.setAttribute('aria-pressed', String(other === button));
 });
});

// Animate only while Jasper is visible, the tab is active, and motion is welcome.
const buddy = document.querySelector('.buddy-welcome');
const buddyMotion = buddy.querySelector('.buddy-motion');
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
let buddyVisible = false;
let buddyPaused = false;
function syncJasperMotion() {
  buddy.classList.toggle('is-animated', buddyVisible && !document.hidden && !buddyPaused && !reducedMotion.matches);
  buddyMotion.hidden = reducedMotion.matches;
  buddyMotion.setAttribute('aria-pressed', String(buddyPaused));
  buddyMotion.textContent = buddyPaused ? 'Resume Jasper' : 'Pause Jasper';
}
buddyMotion.addEventListener('click', () => { buddyPaused = !buddyPaused; syncJasperMotion(); });
new IntersectionObserver(([entry]) => {
  buddyVisible = entry.isIntersecting;
  syncJasperMotion();
}, { threshold: 0.25 }).observe(buddy);
document.addEventListener('visibilitychange', syncJasperMotion);
reducedMotion.addEventListener('change', () => {
  if (reducedMotion.matches)  syncJasperMotion();
});
syncJasperMotion();
