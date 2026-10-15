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
