(() => {
  const { photos, signatures } = window.betaData;
  const rows = document.getElementById('signature-rows');
  for (let row = 0; row < 2; row++) {
    const viewport = document.createElement('div');
    viewport.className = 'signature-row';
    const track = document.createElement('div');
    track.className = 'signature-track';
    const subset = signatures.filter((_, index) => index % 2 === row);
    // Two identical fixed-width groups make the loop continuous even as images load.
    track.style.setProperty('--duration', `${subset.length * 6}s`);
    for (let copy = 0; copy < 2; copy++) {
      const group = document.createElement('div');
      group.className = 'signature-group';
      if (copy) group.setAttribute('aria-hidden', 'true');
      subset.forEach((url, index) => {
        const tile = document.createElement('div');
        tile.className = 'signature-tile';
        const image = new Image();
        image.draggable = false;
        image.src = url;
        image.alt = copy ? '' : `베타테스터 사인 ${index * 2 + row + 1}`;
        image.decoding = 'async';
        image.referrerPolicy = 'no-referrer';
        // Preserve the tile width so a failed image never disrupts the loop.
        image.addEventListener('error', () => { image.hidden = true; tile.setAttribute('aria-hidden', 'true'); });
        tile.append(image);
        group.append(tile);
      });
      track.append(group);
    }
    viewport.append(track);
    rows.append(viewport);
    let drag = null;
    viewport.addEventListener('pointerdown', (event) => {
      if (!event.isPrimary || event.button !== 0 || drag) return;
      if (rows.querySelector('.dragging')) return;
      const targets = Array.from(rows.children, (row) => {
        const rowTrack = row.firstElementChild;
        const animation = rowTrack.getAnimations()[0];
        return {
          row,
          animation,
          time: animation?.currentTime ?? 0,
          width: rowTrack.firstElementChild.getBoundingClientRect().width,
          scroll: row.scrollLeft,
        };
      });
      if (targets.some((target) => !target.width)) return;
      drag = {
        id: event.pointerId,
        x: event.clientX,
        targets,
      };
      targets.forEach(({ row }) => row.classList.add('dragging'));
      viewport.setPointerCapture(event.pointerId);
    });
    viewport.addEventListener('pointermove', (event) => {
      if (!drag || event.pointerId !== drag.id) return;
      const distance = event.clientX - drag.x;
      drag.targets.forEach(({ row, animation, time: startTime, width, scroll }) => {
        if (animation) {
          const { duration, delay } = animation.effect.getTiming();
          const time = startTime - delay - distance * duration / width;
          // Wrap within one duplicated group so dragging never reaches an empty end.
          animation.currentTime = delay + ((time % duration) + duration) % duration;
        } else {
          row.scrollLeft = scroll - distance;
        }
      });
    });
    const endDrag = (event) => {
      if (!drag || event.pointerId !== drag.id) return;
      drag.targets.forEach(({ row }) => row.classList.remove('dragging'));
      drag = null;
      if (viewport.hasPointerCapture(event.pointerId)) viewport.releasePointerCapture(event.pointerId);
    };
    viewport.addEventListener('pointerup', endDrag);
    viewport.addEventListener('pointercancel', endDrag);
    viewport.addEventListener('lostpointercapture', endDrag);
  }
  const toggle = document.getElementById('motion-toggle');
  toggle.addEventListener('click', () => {
    const paused = document.querySelector('.signatures').classList.toggle('paused');
    toggle.setAttribute('aria-pressed', String(paused));
    toggle.textContent = paused ? '사인 움직임 다시 재생' : '사인 움직임 멈추기';
  });
  const gallery = document.getElementById('beta-gallery');
  photos.filter((photo) => photo.date >= '2026-09-17').forEach((photo, index) => {
    const figure = document.createElement('figure');
    figure.className = 'beta-photo';
    const image = new Image();
    image.src = photo.url;
    image.alt = photo.title || `${photo.topic} 주제의 사진`;
    image.loading = index < 6 ? 'eager' : 'lazy';
    image.decoding = 'async';
    image.referrerPolicy = 'no-referrer';
    image.addEventListener('error', () => {
      const fallback = document.createElement('div');
      fallback.className = 'photo-unavailable';
      fallback.textContent = '사진을 불러오지 못했어요';
      image.replaceWith(fallback);
    });
    const caption = document.createElement('figcaption');
    caption.textContent = `${photo.date.slice(5).replace('-', '.')} · ${photo.topic}${photo.title ? ` / ${photo.title}` : ''}`;
    figure.append(image, caption);
    gallery.append(figure);
  });
})();
