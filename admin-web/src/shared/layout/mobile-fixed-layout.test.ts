import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const globalCss = readFileSync(resolve("src/app/globals.css"), "utf8");
const shellCss = readFileSync(resolve("src/shared/layout/admin-shell.module.css"), "utf8");
const postCss = readFileSync(resolve("src/features/posts/ui/posts.module.css"), "utf8");
const commonCss = readFileSync(resolve("src/shared/ui/common-ui.module.css"), "utf8");

// jsdom does not calculate layout. These assertions preserve the fixed-bar
// spacing contract; they do not replace mobile browser layout verification.
describe("mobile fixed-control spacing", () => {
  it("shares the safe-area-aware navigation height with the shell reserve", () => {
    expect(globalCss).toMatch(/--admin-bottom-nav-height:\s*0px/);
    expect(globalCss).toMatch(/@media\s*\(max-width:\s*800px\)\s*\{\s*:root\s*\{\s*--admin-bottom-nav-height:\s*calc\(69px \+ env\(safe-area-inset-bottom\)\)/);
    expect(shellCss).toMatch(/\.shell\s*\{[^}]*padding-bottom:\s*var\(--admin-bottom-nav-height\)/);
    expect(shellCss).toMatch(/\.navigation\s*\{[^}]*min-height:\s*var\(--admin-bottom-nav-height\)/);
  });

  it("keeps post actions above navigation and reserves their own height only once", () => {
    expect(globalCss).toMatch(/--admin-bottom-actions-height:\s*0px/);
    expect(postCss).toMatch(/:global\(body\):has\(\.detailActions\)\s*\{\s*--admin-bottom-actions-height:\s*69px/);
    expect(postCss).toMatch(/\.detailActions\s*\{[^}]*position:\s*fixed;[^}]*bottom:\s*var\(--admin-bottom-nav-height\)/);
    expect(postCss).toMatch(/\.detailActions\s*\{[^}]*position:\s*fixed;[^}]*padding:\s*10px 16px 10px/);
    expect(postCss).not.toContain("env(safe-area-inset-bottom)");
    expect(postCss).toMatch(/\.detailPage:has\(\.detailActions\)\s*\{\s*padding-bottom:\s*var\(--admin-bottom-actions-height\)/);
    expect(postCss).toMatch(/\.detailActions button\s*\{[^}]*min-height:\s*48px/);
  });

  it("positions mobile toasts above both fixed bars across the navigation breakpoint", () => {
    expect(commonCss).toMatch(/@media\s*\(max-width:\s*800px\)\s*\{\s*\.toastRegion\s*\{[^}]*bottom:\s*calc\(var\(--admin-bottom-nav-height\) \+ var\(--admin-bottom-actions-height\) \+ 16px\)/);
  });
});
