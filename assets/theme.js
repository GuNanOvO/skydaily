(() => {
  const key = 'skydaily-theme';
  const hour = 60 * 60 * 1000;
  const beijingOffset = 8 * hour;
  const root = document.documentElement;
  let preference = null;
  let boundaryTimer;
  const valid = value => value === 'light' || value === 'dark';
  try {
    const saved = localStorage.getItem(key);
    if (valid(saved)) preference = saved;
  } catch {}

  const automaticTheme = () => {
    const localHour = new Date(Date.now() + beijingOffset).getUTCHours();
    return localHour >= 19 || localHour < 7 ? 'dark' : 'light';
  };
  const nextBoundary = () => {
    const local = new Date(Date.now() + beijingOffset);
    const localHour = local.getUTCHours();
    const boundaryHour = localHour < 7 ? 7 : localHour < 19 ? 19 : 31;
    const boundary = Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate(), boundaryHour) - beijingOffset;
    return Math.max(1, boundary - Date.now());
  };
  const updateButtons = () => {
    const label = root.dataset.theme === 'dark' ? '切换到亮色模式' : '切换到暗色模式';
    document.querySelectorAll('.theme-toggle').forEach(button => {
      button.setAttribute('aria-label', label);
      button.title = label;
    });
  };
  const apply = theme => {
    const changed = root.dataset.theme !== theme;
    root.dataset.theme = theme;
    root.style.colorScheme = theme;
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = theme === 'dark' ? '#14161c' : '#f4f4f4';
    updateButtons();
    if (changed) window.dispatchEvent(new CustomEvent('skydaily-theme-change', {detail: {theme}}));
  };
  const sync = () => {
    clearTimeout(boundaryTimer);
    apply(preference || automaticTheme());
    if (!preference) boundaryTimer = setTimeout(sync, nextBoundary());
  };

  root.classList.add('theme-ready');
  sync();
  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.theme-toggle').forEach(button => {
      button.addEventListener('click', () => {
        preference = root.dataset.theme === 'dark' ? 'light' : 'dark';
        try { localStorage.setItem(key, preference); } catch {}
        sync();
      });
      const images = [...button.querySelectorAll('img')];
      Promise.all(images.map(image => image.decode().catch(() => {}))).then(() => {
        if (images.every(image => image.naturalWidth > 0)) {
          button.disabled = false;
          button.hidden = false;
        }
      });
    });
    updateButtons();
  }, {once: true});
  window.addEventListener('storage', event => {
    if (event.key !== key && event.key !== null) return;
    preference = valid(event.newValue) ? event.newValue : null;
    sync();
  });
  window.addEventListener('pageshow', sync);
  window.addEventListener('focus', sync);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) sync();
  });
})();
