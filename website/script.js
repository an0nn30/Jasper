const platforms = {
  windows: { name: 'Windows 11', width: 1549, height: 989, theme: 'Jasper Meadow Light' },
  macos: { name: 'macOS', width: 2322, height: 1184, theme: 'Jasper Meadow Light' },
  linux: { name: 'Ubuntu Linux', width: 1552, height: 992, theme: 'Jasper Meadow Light' }
};
const image = document.querySelector('#platform-image');
const caption = document.querySelector('#platform-caption');
const buttons = [...document.querySelectorAll('[data-platform]')];
for (const button of buttons) {
  button.addEventListener('click', () => {
    const key = button.dataset.platform;
    const platform = platforms[key];
    image.src = `assets/${key}.png`;
    image.alt = `Jasper on ${platform.name} using ${platform.theme}, showing sample infrastructure commands and output`;
    image.width = platform.width;
    image.height = platform.height;
    caption.textContent = `Jasper on ${platform.name} · ${platform.theme}`;
    for (const other of buttons) other.setAttribute('aria-pressed', String(other === button));
  });
}

const tour = {
 sessions: ['REMOTE / SESSIONS', 'Your next connection, ready.', 'Group your SSH hosts, favorite the ones you use most, and open a session without hunting for connection details.', 'Saved demo hosts', 'Remote Sessions panel with three saved hosts grouped under Staging'],
 sftp: ['REMOTE / SFTP', 'Files, right beside your shell.', 'Browse remote folders, transfer files, and open a remote configuration in the built-in Editor.', 'Sample directory & transfer', 'SFTP demo showing config, logs, releases, deployment.yaml, nginx.conf and README.md, with a sample file transfer'],
 tunnels: ['REMOTE / TUNNELS', 'A shorter path to your services.', 'Set up local, remote, or SOCKS forwarding with a clear view of where each connection goes.', 'Example local tunnel · configuration view', 'Tunnel configuration forwarding localhost port 15432 through a staging host to port 5432'],
 sharing: ['FILE SERVER', 'A folder becomes a handoff.', 'Share files over HTTP, SFTP, FTP, and more. Choose the folder, access, and reach that fit the job.', 'Demo folder · server configuration', 'File Server configuration for a demo folder with protocol and access controls'],
 vault: ['CREDENTIAL VAULT', 'Credentials have a home, too.', 'Protect saved credentials with a master password and control which plugins can use them.', 'Fictional accounts · passwords masked', 'Credential Vault with three fictional accounts and Staging gateway selected, showing demo-operator and a masked password']
};
const toolButtons = [...document.querySelectorAll('[data-tool]')];
for (const button of toolButtons) button.addEventListener('click', () => {
 const key = button.dataset.tool, view = tour[key];
 const introductions = {
  sessions: 'Start with your connections. Keep your regular hosts close at hand.',
  sftp: 'Now, bring the files along. Browse and transfer without leaving your workspace.',
  tunnels: 'Need to reach a service? Give it a path through your SSH connection.',
  sharing: 'Something to hand over? Turn a folder into a file server.',
  vault: 'And for the credentials: one protected place, with you in control.'
 };
 document.querySelector('#buddy-tour-line').textContent = introductions[key];
 ['family','title','description','detail'].forEach((name, i) => document.querySelector(`#tour-${name}`).textContent = view[i]);
 const img = document.querySelector('#tour-image');
 img.src = `assets/tour-${key}.png?v=moss-1`; img.alt = view[4];
 document.querySelector('#tour-link').href = img.src;
 for (const other of toolButtons) other.setAttribute('aria-pressed', String(other === button));
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
reducedMotion.addEventListener('change', syncJasperMotion);
syncJasperMotion();
