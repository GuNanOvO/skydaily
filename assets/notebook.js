(() => {
  const paper = document.querySelector('main');
  const ruling = document.createElement('canvas');
  ruling.className = 'paper-rules';
  ruling.setAttribute('aria-hidden', 'true');
  paper.append(ruling);
  let ruleFrame;
  const drawRules = () => {
    const paperStyle = getComputedStyle(paper);
    const step = parseFloat(paperStyle.getPropertyValue('--step')) || 32;
    const tasks = paper.querySelector('.tasks');
    if (tasks) {
      const padding = parseFloat(getComputedStyle(tasks).paddingBottom) || 0;
      const contentHeight = tasks.getBoundingClientRect().height - padding;
      const gap = Math.max(0, Math.ceil((contentHeight - 0.01) / step) * step - contentHeight);
      tasks.style.setProperty('--tasks-rule-gap', gap + 'px');
    }
    const bounds = ruling.getBoundingClientRect();
    const ratio = window.devicePixelRatio || 1;
    const width = Math.round(bounds.width * ratio);
    const height = Math.round(bounds.height * ratio);
    if (!width || !height) return;
    ruling.width = width;
    ruling.height = height;
    const context = ruling.getContext('2d');
    if (!context) return;
    context.fillStyle = paperStyle.getPropertyValue('--rule').trim();
    const thickness = Math.max(1, Math.round(2 * ratio));
    const contentTop = paper.getBoundingClientRect().top
      + (parseFloat(paperStyle.borderTopWidth) || 0)
      + (parseFloat(paperStyle.paddingTop) || 0);
    const ruleCenter = (parseFloat(paperStyle.getPropertyValue('--rule-end')) || 30) - 1;
    const firstCenter = ((contentTop - bounds.top + ruleCenter) % step + step) % step;
    for (let center = firstCenter; center < bounds.height; center += step) {
      const y = Math.round(center * ratio - thickness / 2);
      context.fillRect(0, y, width, thickness);
    }
    paper.classList.add('has-paper-rules');
  };
  const scheduleRules = () => {
    cancelAnimationFrame(ruleFrame);
    ruleFrame = requestAnimationFrame(drawRules);
  };
  new ResizeObserver(scheduleRules).observe(paper);
  window.addEventListener('resize', scheduleRules);
  window.addEventListener('skydaily-theme-change', scheduleRules);
  const watchResolution = () => {
    const resolution = matchMedia('(resolution: ' + devicePixelRatio + 'dppx)');
    resolution.addEventListener('change', () => {
      scheduleRules();
      watchResolution();
    }, {once: true});
  };
  watchResolution();
  document.fonts.ready.then(scheduleRules);
  scheduleRules();
  let opener;
  let pageScroll = 0;
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  const animateSheet = (dialog, closing = false) => {
    if (reducedMotion.matches) return;
    return dialog.animate(closing
      ? [{opacity: 1, translate: '0 0'}, {opacity: 0, translate: '0 8px'}]
      : [{opacity: 0, translate: '0 12px'}, {opacity: 1, translate: '0 0'}],
    {duration: closing ? 140 : 240, easing: 'cubic-bezier(.2,.7,.2,1)'});
  };
  const closingSheets = new WeakSet();
  const closeSheet = async dialog => {
    if (!dialog?.open || closingSheets.has(dialog)) return;
    closingSheets.add(dialog);
    const animation = animateSheet(dialog, true);
    if (animation) await animation.finished.catch(() => {});
    dialog.close();
    closingSheets.delete(dialog);
  };
  const datePicker = document.querySelector('.date-picker');
  if (datePicker) {
    const months = [...datePicker.querySelectorAll('.calendar-month')];
    datePicker.querySelectorAll('[data-month-change]').forEach(button => {
      button.addEventListener('click', () => {
        const current = months.findIndex(month => !month.hidden);
        const next = current + Number(button.dataset.monthChange);
        if (!months[next]) return;
        months[current].hidden = true;
        months[next].hidden = false;
        const preferred = months[next].querySelector(`[data-month-change="${button.dataset.monthChange}"]`);
        (preferred.disabled ? months[next].querySelector('button:not(:disabled)') : preferred)?.focus({preventScroll: true});
      });
    });
    document.addEventListener('click', event => {
      if (!datePicker.contains(event.target)) datePicker.open = false;
    });
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape' && datePicker.open) {
        datePicker.open = false;
        datePicker.querySelector('summary').focus({preventScroll: true});
      }
    });
  }
  document.querySelectorAll('.task-note').forEach((note, index) => {
    note.style.setProperty('--tape-left', (24 + Math.random() * 34).toFixed(1) + '%');
    note.style.setProperty('--tape-angle', (Math.random() * 16 - 8).toFixed(1) + 'deg');
    note.style.setProperty('--tape-width', (40 + Math.random() * 20).toFixed(1) + 'px');
    const colors = ['#ecd69b80', '#edb8c980', '#add9cc80', '#becdf080'];
    note.style.setProperty('--tape-color', colors[index % colors.length]);
  });
  document.querySelectorAll('#calendar .photo-window img').forEach(img => {
    const frame = img.closest('.photo-window');
    frame.style.setProperty('--calendar-fill', 'url(' + JSON.stringify(img.src) + ')');
    img.addEventListener('error', () => frame.style.removeProperty('--calendar-fill'), {once: true});
  });
  document.querySelectorAll('.photo-note img, .image-link img').forEach(img => {
    const unavailable = () => {
      if (img.hidden) return;
      img.hidden = true;
      img.dataset.unavailable = 'true';
      const placeholder = document.createElement('span');
      placeholder.className = 'image-unavailable';
      placeholder.textContent = '图片暂时无法加载';
      placeholder.setAttribute('role', 'img');
      placeholder.setAttribute('aria-label', img.alt + '暂时无法加载');
      img.after(placeholder);
      document.querySelectorAll('.photo-note img, .image-link img').forEach(other => {
        if (other !== img && other.src === img.src && !other.hidden) other.dispatchEvent(new Event('error'));
      });
      document.querySelectorAll('dialog[data-has-text="false"]').forEach(dialog => {
        const images = [...dialog.querySelectorAll('img')];
        if (!images.length || !images.every(image => image.dataset.unavailable === 'true')) return;
        document.querySelectorAll('[data-dialog]').forEach(trigger => {
          if (trigger.dataset.dialog !== dialog.id) return;
          const plain = document.createElement('div');
          plain.append(...trigger.childNodes);
          plain.querySelectorAll('figcaption, .preview-caption').forEach(caption => caption.remove());
          trigger.closest('[data-section-dialog]')?.removeAttribute('data-section-dialog');
          trigger.replaceWith(plain);
        });
        if (dialog.open) dialog.close();
        dialog.remove();
      });
    };
    img.addEventListener('error', unavailable);
  });
  document.querySelectorAll('.photo-note img, .image-link img').forEach(img => {
    if (img.complete && img.naturalWidth === 0) img.dispatchEvent(new Event('error'));
  });
  const open = (id, trigger) => {
    const dialog = document.getElementById(id);
    if (!dialog || dialog.open) return;
    document.querySelectorAll('dialog[open]').forEach(d => d.close());
    pageScroll = window.scrollY;
    const paperBounds = paper.getBoundingClientRect();
    document.body.style.setProperty('--paper-left', paperBounds.left + 'px');
    document.body.style.setProperty('--paper-top', paperBounds.top + 'px');
    document.body.style.setProperty('--paper-width', paperBounds.width + 'px');
    opener = trigger;
    document.body.classList.add('modal-open');
    document.querySelector('main').inert = true;
    dialog.setAttribute('aria-modal', 'true');
    dialog.show();
    window.scrollTo({top: 0, behavior: 'instant'});
    dialog.querySelector('.dialog-close').focus({preventScroll: true});
    animateSheet(dialog);
  };
  document.querySelectorAll('[data-dialog]').forEach(trigger => {
    trigger.addEventListener('click', event => {
      event.preventDefault();
      history.replaceState(null, '', trigger.getAttribute('href'));
      open(trigger.dataset.dialog, trigger);
    });
  });
  document.querySelectorAll('.task-note:has([data-dialog]), [data-section-dialog]').forEach(note => {
    note.addEventListener('click', event => {
      if (event.target.closest('a, input, label') || window.getSelection()?.toString()) return;
      note.querySelector('[data-dialog]')?.click();
    });
  });
  document.querySelectorAll('dialog').forEach(dialog => {
    dialog.querySelector('.dialog-close').addEventListener('click', () => closeSheet(dialog));
    dialog.addEventListener('click', event => {
      const bounds = dialog.getBoundingClientRect();
      if (event.target === dialog && (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom)) closeSheet(dialog);
    });
    dialog.addEventListener('close', () => {
      dialog.querySelectorAll('video').forEach(video => video.pause());
      if (document.querySelector('dialog[open]')) return;
      document.body.classList.remove('modal-open');
      if (/^#(?:task-[0-9]+|weather-details|calendar-details)$/.test(location.hash)) history.replaceState(null, '', location.pathname + location.search);
      document.querySelector('main').inert = false;
      dialog.removeAttribute('aria-modal');
      opener?.focus({preventScroll: true});
      window.scrollTo({top: pageScroll, behavior: 'instant'});
    });
  });
  document.addEventListener('click', event => {
    if (event.target === document.body) closeSheet(document.querySelector('dialog[open]'));
  });
  document.addEventListener('keydown', event => {
    const dialog = document.querySelector('dialog[open]');
    if (!dialog) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      closeSheet(dialog);
    }
    if (event.key === 'Tab') {
      const focusable = [...dialog.querySelectorAll('button, a[href], input, video[controls], [tabindex="0"]')].filter(el => el.getClientRects().length);
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault(); last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first?.focus();
      }
    }
  });
  const fromHash = () => {
    const match = /^#task-([0-9]+)$/.exec(location.hash);
    if (match) open('detail-' + match[1], document.querySelector('[data-dialog="detail-' + match[1] + '"]'));
    const section = /^#(weather|calendar)-details$/.exec(location.hash);
    if (section) open('detail-' + section[1], document.querySelector('[data-dialog="detail-' + section[1] + '"]'));
  };
  window.addEventListener('hashchange', fromHash);
  fromHash();
  const checks = [...document.querySelectorAll('[data-complete]')];
  const key = 'skydaily-completed-' + document.querySelector('main').dataset.date;
  let saved = [];
  try { const value = JSON.parse(localStorage.getItem(key) || '[]'); if (Array.isArray(value)) saved = value; } catch {}
  const update = () => {
    const count = document.getElementById('complete-count');
    if (count) count.textContent = checks.filter(c => c.checked).length;
    checks.forEach(c => c.closest('.task-note').classList.toggle('completed', c.checked));
  };
  checks.forEach(check => {
    check.checked = saved.includes(check.dataset.complete);
    check.addEventListener('change', () => {
      update();
      try { localStorage.setItem(key, JSON.stringify(checks.filter(c => c.checked).map(c => c.dataset.complete))); } catch {}
    });
  });
  update();
  const regions = [...document.querySelectorAll('.page-header, .jump, .task-note, .overview section, #candles, .journal-footer')];
  const reveal = new IntersectionObserver(entries => {
    entries.filter(entry => entry.isIntersecting).forEach((entry, index) => {
      reveal.unobserve(entry.target);
      if (reducedMotion.matches) return;
      entry.target.animate([{opacity: 0, translate: '0 10px'}, {opacity: 1, translate: '0 0'}],
        {duration: 380, delay: Math.min(index * 45, 135), easing: 'cubic-bezier(.2,.7,.2,1)', fill: 'backwards'});
    });
  }, {threshold: 0.08});
  regions.forEach(region => reveal.observe(region));
})();
(() => {
  document.querySelectorAll('[data-share-url]').forEach(button => {
    button.addEventListener('click', async () => {
      const url = button.dataset.shareUrl || location.href;
      const title = document.title;
      try {
        if (navigator.share) {
          await navigator.share({ title, text: title, url });
          return;
        }
        await navigator.clipboard.writeText(url);
        const original = button.textContent;
        button.textContent = '链接已复制';
        setTimeout(() => { button.textContent = original; }, 2000);
      } catch {}
    });
  });
  document.querySelectorAll('[data-share-image]').forEach(button => {
    button.addEventListener('click', async () => {
      const original = button.textContent;
      try {
        const response = await fetch(button.dataset.shareImage);
        if (!response.ok) throw new Error('share image unavailable');
        const blob = await response.blob();
        const name = 'skydaily-' + (document.querySelector('main')?.dataset.date ?? 'share') + '.jpg';
        const file = new File([blob], name, { type: blob.type || 'image/jpeg' });
        if (navigator.canShare?.({ files: [file] })) {
          await navigator.share({ files: [file], title: document.title, text: '光遇每日任务' });
          return;
        }
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = name;
        link.click();
        URL.revokeObjectURL(link.href);
        button.textContent = '已保存图片';
        setTimeout(() => { button.textContent = original; }, 2000);
      } catch {}
    });
  });
})();
