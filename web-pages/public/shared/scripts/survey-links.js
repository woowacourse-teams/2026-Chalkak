/** Only public Google Forms response links belong in config/surveys.json. */
export function isGoogleFormResponseUrl(value) {
  if (typeof value !== "string") return false;
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password || url.port)
      return false;
    if (url.hostname === "forms.gle")
      return /^\/[a-zA-Z0-9_-]+\/?$/.test(url.pathname);
    return (
      url.hostname === "docs.google.com" &&
      /^\/forms\/(?:u\/\d+\/)?d\/(?:e\/)?[a-zA-Z0-9_-]+\/viewform\/?$/.test(
        url.pathname,
      )
    );
  } catch {
    return false;
  }
}

export function initializeSurveyLinks(root = document) {
  const dialog = root.querySelector("[data-survey-dialog]");
  for (const link of root.querySelectorAll("[data-survey-link]")) {
    if (isGoogleFormResponseUrl(link.getAttribute("href"))) {
      // Native anchors open the same form without an async popup or page scrolling.
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      link.removeAttribute("aria-haspopup");
    } else {
      link.addEventListener("click", (event) => {
        if (!dialog || typeof dialog.showModal !== "function") return;
        event.preventDefault();
        dialog.showModal();
      });
    }
  }
  if (!dialog) return;
  for (const button of dialog.querySelectorAll("[data-survey-close]")) {
    button.addEventListener("click", () => dialog.close());
  }
  dialog.addEventListener("click", (event) => {
    if (event.target !== dialog) return;
    const rect = dialog.getBoundingClientRect();
    if (
      event.clientX < rect.left ||
      event.clientX > rect.right ||
      event.clientY < rect.top ||
      event.clientY > rect.bottom
    )
      dialog.close();
  });
}

if (typeof document !== "undefined") initializeSurveyLinks();
