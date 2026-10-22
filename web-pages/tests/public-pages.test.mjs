import test from "node:test";
import assert from "node:assert/strict";
import { cp, mkdtemp, readFile, writeFile, mkdir, rm } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import {
  buildSite,
  renderTemplate,
  validateConfiguration,
} from "../scripts/site.mjs";
import { isGoogleFormResponseUrl } from "../public/shared/scripts/survey-links.js";
const source = fileURLToPath(new URL("../", import.meta.url));
const responseUrl =
  "https://docs.google.com/forms/d/e/example-form-id/viewform?usp=sf_link";

function surveyHrefs(html) {
  return [...html.matchAll(/<a\b[^>]*>/g)]
    .filter(([anchor]) => /\bdata-survey-link\b/.test(anchor))
    .map(([anchor]) => /\bhref="([^"]+)"/.exec(anchor)?.[1]);
}

async function fixture(t) {
  const root = await mkdtemp(join(tmpdir(), "chalkak-pages-"));
  t.after(() => rm(root, { recursive: true, force: true }));
  await cp(join(source, "public"), join(root, "public"), { recursive: true });
  await cp(join(source, "config"), join(root, "config"), { recursive: true });
  return root;
}

async function configure(root, file, change) {
  const path = join(root, "config", file);
  const json = JSON.parse(await readFile(path, "utf8"));
  await writeFile(path, JSON.stringify(change(json)));
}

test("accept response links and reject editor links, credentials, and lookalike hosts", () => {
  for (const value of [
    responseUrl,
    "https://forms.gle/Example123",
    "https://docs.google.com/forms/d/form-id/viewform",
  ]) {
    assert.equal(isGoogleFormResponseUrl(value), true, value);
  }
  for (const value of [
    null,
    "",
    "http://forms.gle/abc",
    "https://forms.gle/",
    "https://forms.gle.evil.example/abc",
    "https://docs.google.com/forms/d/id/edit",
    "https://user:secret@forms.gle/abc",
    "https://forms.gle:444/abc",
    "javascript:alert(1)",
  ]) {
    assert.equal(isGoogleFormResponseUrl(value), false, String(value));
  }
});

test("unconfigured deployment has working routes and local survey fallback", async (t) => {
  const root = await fixture(t);
  await configure(root, "surveys.json", (data) => ({
    ...data,
    "ai-feedback": null,
  }));
  const result = await buildSite({
    projectRoot: root,
    env: { PUBLIC_SITE_URL: "https://pages.example.test" },
  });
  assert.deepEqual(result.pages, ["/", "/ai-feedback/"]);
  const page = await readFile(
    join(root, "dist/ai-feedback/index.html"),
    "utf8",
  );
  assert.deepEqual(surveyHrefs(page), ["#survey", "#survey"]);
  assert.match(page, /현재 사용 후기 설문을 준비하고 있어요/);
  assert.match(page, /data-survey-dialog/);
  assert.doesNotMatch(
    page,
    /\{\{|clearbell2\.chatgpt\.site|디자인 초안|실제 개발 시/,
  );
  assert.match(
    page,
    /https:\/\/pages\.example\.test\/ai-feedback\/assets\/cover\.webp/,
  );
  assert.match(
    await readFile(join(root, "dist/index.html"), "utf8"),
    /href="\/ai-feedback\/"/,
  );
});

test("configured deployment gives both survey anchors the same native external URL", async (t) => {
  const root = await fixture(t);
  await configure(root, "surveys.json", (data) => ({
    ...data,
    "ai-feedback": responseUrl,
  }));
  await buildSite({ projectRoot: root, env: {} });
  const page = await readFile(
    join(root, "dist/ai-feedback/index.html"),
    "utf8",
  );
  assert.deepEqual(surveyHrefs(page), [responseUrl, responseUrl]);
  assert.match(page, /Google Forms에서 사용 후기를 남겨주세요/);
});

test("adding another page publishes its independent route and automatically adds the index and sitemap entry", async (t) => {
  const root = await fixture(t);
  await mkdir(join(root, "public/another-story/assets"), { recursive: true });
  await cp(
    join(root, "public/ai-feedback/assets/cover.webp"),
    join(root, "public/another-story/assets/cover.webp"),
  );
  await writeFile(
    join(root, "public/another-story/index.html"),
    '<!doctype html><html lang="ko"><title>새 소식</title><body><h1>새 소식</h1><a href="/">목록</a><img alt="소식" src="/another-story/assets/cover.webp"></body></html>',
  );
  await configure(root, "pages.json", (data) => ({
    ...data,
    pages: [
      ...data.pages,
      {
        slug: "another-story",
        title: "새 소식",
        description: "별도 안내 페이지",
        category: "소식",
        thumbnail: "/another-story/assets/cover.webp",
      },
    ],
  }));
  const result = await buildSite({
    projectRoot: root,
    env: { PUBLIC_SITE_URL: "https://pages.example.test" },
  });
  assert.deepEqual(result.pages, ["/", "/ai-feedback/", "/another-story/"]);
  assert.match(
    await readFile(join(root, "dist/index.html"), "utf8"),
    /href="\/another-story\/"/,
  );
  assert.match(
    await readFile(join(root, "dist/sitemap.xml"), "utf8"),
    /https:\/\/pages\.example\.test\/another-story\//,
  );
  assert.match(
    await readFile(join(root, "dist/ai-feedback/index.html"), "utf8"),
    /찰캌의 AI는 사진을/,
  );
});

test("broken assets fail the build instead of publishing a broken page", async (t) => {
  const root = await fixture(t);
  const path = join(root, "public/ai-feedback/index.html");
  await writeFile(
    path,
    (await readFile(path, "utf8")).replace(
      "/ai-feedback/assets/example.webp",
      "/ai-feedback/assets/missing.webp",
    ),
  );
  await assert.rejects(
    buildSite({ projectRoot: root, env: {} }),
    /Broken local reference/,
  );
});

test("a broken in-page link fails validation", async (t) => {
  const root = await fixture(t);
  const path = join(root, "public/ai-feedback/index.html");
  await writeFile(
    path,
    (await readFile(path, "utf8")).replace(
      'href="#main"',
      'href="#missing-section"',
    ),
  );
  await assert.rejects(
    buildSite({ projectRoot: root, env: {} }),
    /Missing fragment/,
  );
});

test("survey typos and unsafe configuration fail before deployment", async (t) => {
  const root = await fixture(t);
  await configure(root, "surveys.json", (data) => ({
    ...data,
    "ai-feedback": "https://example.com/collect",
  }));
  await assert.rejects(
    buildSite({ projectRoot: root, env: {} }),
    /public HTTPS Google Forms response URL/,
  );
  assert.throws(
    () =>
      renderTemplate("{{survey:unknown}}", {
        surveys: {},
        origin: "https://example.test",
      }),
    /Unknown survey id/,
  );
});

test("duplicate or traversing page slugs cannot overwrite other routes", () => {
  const page = {
    slug: "story",
    title: "제목",
    description: "내용",
    category: "소식",
    thumbnail: "/story/assets/cover.webp",
  };
  assert.throws(
    () =>
      validateConfiguration(
        { title: "소식", description: "설명", pages: [page, page] },
        {},
      ),
    /Duplicate page/,
  );
  for (const slug of ["../outside", "shared", "with/slash", "UpperCase"]) {
    assert.throws(
      () =>
        validateConfiguration(
          { title: "소식", description: "설명", pages: [{ ...page, slug }] },
          {},
        ),
      /Invalid or reserved/,
    );
  }
});
