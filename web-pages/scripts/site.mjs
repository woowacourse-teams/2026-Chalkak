import {
  cp,
  mkdir,
  readFile,
  readdir,
  rm,
  stat,
  writeFile,
} from "node:fs/promises";
import { extname, join, resolve, sep } from "node:path";
import { isGoogleFormResponseUrl } from "../public/shared/scripts/survey-links.js";

export const escapeHtml = (value) =>
  String(value).replace(
    /[&<>"']/g,
    (char) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        char
      ],
  );
const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const reserved = new Set(["shared", "assets", "api"]);

export function validateConfiguration(catalog, surveys) {
  if (
    !catalog?.title ||
    !catalog?.description ||
    !Array.isArray(catalog.pages) ||
    !catalog.pages.length
  ) {
    throw new Error(
      "config/pages.json needs title, description, and at least one page",
    );
  }
  if (!surveys || typeof surveys !== "object" || Array.isArray(surveys))
    throw new Error("Invalid survey configuration");
  const slugs = new Set();
  for (const page of catalog.pages) {
    if (!slugPattern.test(page.slug) || reserved.has(page.slug))
      throw new Error(`Invalid or reserved page slug: ${page.slug}`);
    if (slugs.has(page.slug))
      throw new Error(`Duplicate page slug: ${page.slug}`);
    slugs.add(page.slug);
    if (
      ![page.title, page.description, page.category].every(
        (value) => typeof value === "string" && value.trim(),
      )
    ) {
      throw new Error(`Missing page metadata: ${page.slug}`);
    }
    if (
      typeof page.thumbnail !== "string" ||
      !page.thumbnail.startsWith(`/${page.slug}/assets/`) ||
      page.thumbnail.includes("..") ||
      page.thumbnail.includes("\\")
    )
      throw new Error(`Invalid thumbnail path: ${page.slug}`);
  }
  for (const [id, value] of Object.entries(surveys)) {
    if (!slugPattern.test(id)) throw new Error(`Invalid survey id: ${id}`);
    if (value !== null && !isGoogleFormResponseUrl(value)) {
      throw new Error(
        `Survey ${id} must be null or a public HTTPS Google Forms response URL`,
      );
    }
  }
  return catalog.pages;
}

export function renderTemplate(html, { surveys, origin }) {
  return html.replace(
    /\{\{(survey|survey-status|site):([^}]+)\}\}/g,
    (_, type, key) => {
      if (type === "site") {
        if (key !== "origin") throw new Error(`Unknown site template: ${key}`);
        return escapeHtml(origin);
      }
      if (!Object.hasOwn(surveys, key))
        throw new Error(`Unknown survey id: ${key}`);
      if (type === "survey-status")
        return surveys[key]
          ? "Google Forms에서 사용 후기를 남겨주세요."
          : "현재 사용 후기 설문을 준비하고 있어요.";
      return surveys[key] ? escapeHtml(surveys[key]) : "#survey";
    },
  );
}

export async function filesUnder(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const result = [];
  for (const entry of entries) {
    if (entry.isSymbolicLink())
      throw new Error(`Use regular assets instead of symlinks: ${entry.name}`);
    const path = join(directory, entry.name);
    if (entry.isDirectory()) result.push(...(await filesUnder(path)));
    else if (entry.isFile()) result.push(path);
  }
  return result;
}

function documentPath(root, pathname) {
  const path = resolve(root, `.${decodeURIComponent(pathname)}`);
  if (!path.startsWith(resolve(root) + sep) && path !== resolve(root))
    throw new Error("Local path leaves the public directory");
  return path;
}

export async function validateLocalReferences(root) {
  const files = await filesUnder(root);
  const ids = new Map();
  for (const path of files.filter((path) => extname(path) === ".html")) {
    const html = await readFile(path, "utf8");
    if (/\{\{[^}]+\}\}/.test(html))
      throw new Error(`Unresolved template in ${path}`);
    ids.set(
      path,
      new Set(
        [...html.matchAll(/\bid=["']([^"']+)["']/g)].map((match) => match[1]),
      ),
    );
  }
  for (const path of files.filter((path) => extname(path) === ".html")) {
    const html = await readFile(path, "utf8");
    const route =
      "/" +
      path
        .slice(resolve(root).length + 1)
        .split(sep)
        .join("/");
    const references = [
      ...[...html.matchAll(/\b(?:src|href)=["']([^"']+)["']/g)].map(
        ([, value]) => value,
      ),
      ...[...html.matchAll(/\bsrcset=["']([^"']+)["']/g)].flatMap(
        ([, value]) =>
          value.split(",").map((candidate) => candidate.trim().split(/\s+/)[0]),
      ),
    ];
    for (const raw of references) {
      if (/^(?:https?:|mailto:|tel:|data:)/i.test(raw)) continue;
      const url = new URL(raw, `https://local.invalid${route}`);
      let target = documentPath(root, url.pathname);
      try {
        if ((await stat(target)).isDirectory())
          target = join(target, "index.html");
        if (!(await stat(target)).isFile()) throw new Error("not a file");
      } catch {
        throw new Error(`Broken local reference ${raw} in ${route}`);
      }
      if (
        url.hash &&
        ids.has(target) &&
        !ids.get(target).has(decodeURIComponent(url.hash.slice(1)))
      ) {
        throw new Error(`Missing fragment ${raw} in ${route}`);
      }
    }
  }
  return files;
}

function siteOrigin(env) {
  const candidate =
    env.PUBLIC_SITE_URL ||
    (env.VERCEL_PROJECT_PRODUCTION_URL
      ? `https://${env.VERCEL_PROJECT_PRODUCTION_URL}`
      : "http://localhost:4173");
  const url = new URL(candidate);
  if (
    !["http:", "https:"].includes(url.protocol) ||
    url.username ||
    url.password ||
    url.pathname !== "/" ||
    url.search ||
    url.hash
  ) {
    throw new Error("PUBLIC_SITE_URL must be a plain HTTP(S) origin");
  }
  return url.origin;
}

function hubDocument(catalog, origin) {
  const cards = catalog.pages
    .map(
      (page) =>
        `<a class="page-card" href="/${page.slug}/"><img src="${escapeHtml(page.thumbnail)}" alt="${escapeHtml(page.title)}" loading="lazy"><div class="page-card-body"><small>${escapeHtml(page.category)}</small><h2>${escapeHtml(page.title)}</h2><p>${escapeHtml(page.description)}</p><span class="read-more">읽어보기 ↗</span></div></a>`,
    )
    .join("\n");
  return `<!doctype html><html lang="ko"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escapeHtml(catalog.title)}</title><meta name="description" content="${escapeHtml(catalog.description)}"><link rel="canonical" href="${escapeHtml(origin)}/"><link rel="stylesheet" href="/shared/styles/tokens.css"><link rel="stylesheet" href="/shared/styles/base.css"><link rel="stylesheet" href="/shared/styles/hub.css"></head><body><header class="hub-header"><a href="/" aria-label="찰캌 소식"><img src="/shared/assets/chalkak-logo.png" alt="찰캌"></a><span>CHALKAK JOURNAL</span></header><main class="hub-main"><span class="hub-eyebrow">STORIES & EXPERIENCES</span><h1>${escapeHtml(catalog.title)}</h1><p class="hub-description">${escapeHtml(catalog.description)}</p><div class="page-grid">${cards}</div></main><footer class="hub-footer"><span>© Chalkak</span><span>서로의 시선을 나누는 곳</span></footer></body></html>`;
}

export async function buildSite({
  projectRoot,
  outputDirectory = join(projectRoot, "dist"),
  env = process.env,
}) {
  const root = resolve(projectRoot);
  const output = resolve(outputDirectory);
  if (
    output === root ||
    output === join(root, "public") ||
    !output.startsWith(root + sep)
  ) {
    throw new Error(
      "Build output must be a separate directory inside the project",
    );
  }
  const catalog = JSON.parse(
    await readFile(join(root, "config/pages.json"), "utf8"),
  );
  const surveys = JSON.parse(
    await readFile(join(root, "config/surveys.json"), "utf8"),
  );
  validateConfiguration(catalog, surveys);
  const origin = siteOrigin(env);
  for (const page of catalog.pages) {
    await stat(join(root, "public", page.slug, "index.html"));
    await stat(documentPath(join(root, "public"), page.thumbnail));
  }
  await rm(output, { recursive: true, force: true });
  await mkdir(output, { recursive: true });
  await cp(join(root, "public"), output, { recursive: true });
  for (const path of (await filesUnder(output)).filter(
    (path) => extname(path) === ".html",
  )) {
    await writeFile(
      path,
      renderTemplate(await readFile(path, "utf8"), { surveys, origin }),
    );
  }
  await writeFile(join(output, "index.html"), hubDocument(catalog, origin));
  await writeFile(
    join(output, "robots.txt"),
    `User-agent: *\nAllow: /\nSitemap: ${origin}/sitemap.xml\n`,
  );
  const locations = ["/", ...catalog.pages.map((page) => `/${page.slug}/`)];
  await writeFile(
    join(output, "sitemap.xml"),
    `<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${locations.map((path) => `<url><loc>${escapeHtml(origin + path)}</loc></url>`).join("")}</urlset>`,
  );
  const files = await validateLocalReferences(output);
  return {
    output,
    pages: locations,
    files: files.length,
    surveysReady: Object.values(surveys).filter(Boolean).length,
  };
}
