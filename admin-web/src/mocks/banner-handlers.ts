import { http, HttpResponse } from "msw";
import { normalizeBanner, validateBanner, type Banner, type BannerInput } from "@/features/banners/model/banner";

export const bannerFixtures: Banner[] = [
  { id: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbb01", title: "일상 속 작은 순간을 담아보세요", subtitle: "이번 주의 사진 주제를 만나보세요.", webViewUrl: "https://chalkak.vercel.app/weekly", thumbnailUrl: "https://demo.chalkak.example/weekly.svg", createdAt: "2026-10-20T01:00:00Z" },
  { id: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbb02", title: "Chalkak과 함께하는 가을", subtitle: "사진 한 장으로 나만의 이야기를 전해요.", webViewUrl: "https://chalkak.vercel.app/autumn", thumbnailUrl: "https://demo.chalkak.example/autumn.svg", createdAt: "2026-10-19T00:00:00Z" },
];
let banners = structuredClone(bannerFixtures);
const deleted = new Set<string>();
export function resetBannerMocks() { banners = structuredClone(bannerFixtures); deleted.clear(); }
const path = "*/api/v1/admin/banners";
const error = (message: string, status = 400) => HttpResponse.json({ errorCode: "BUSINESS_ERROR", message }, { status });
export function createBannerHandlers(record: (action: string, id: string, before: Record<string, unknown>, after: Record<string, unknown>) => void) {
  return [
    http.get(path, ({ request }) => {
      const params = new URL(request.url).searchParams;
      const page = Number(params.get("page") ?? 1), pageSize = Number(params.get("pageSize") ?? 20);
      if (!Number.isInteger(page) || page < 1 || !Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100 || (page - 1) * pageSize > 2147483647) return error("조회 조건이 올바르지 않습니다.");
      const sorted = [...banners].sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id));
      return HttpResponse.json({ currentPage: page, pageSize, hasNext: page * pageSize < sorted.length, banners: sorted.slice((page - 1) * pageSize, page * pageSize) });
    }),
    http.post(path, async ({ request }) => {
      const input = await request.json() as BannerInput;
      if (!input || ["title", "subtitle", "webViewUrl", "thumbnailUrl"].some((key) => typeof input[key as keyof BannerInput] !== "string")) return error("배너 정보를 확인해 주세요.");
      const errors = validateBanner(input);
      if (Object.keys(errors).length) return error(Object.values(errors)[0]!);
      const body = normalizeBanner(input);
      if (banners.some((banner) => banner.webViewUrl === body.webViewUrl)) return error("동일한 웹뷰 URL의 배너가 이미 존재합니다.");
      const banner: Banner = { ...body, id: crypto.randomUUID(), createdAt: new Date().toISOString() };
      banners.unshift(banner); record("BANNER_CREATED", banner.id, {}, { id: banner.id, deleted: false });
      return HttpResponse.json(banner, { status: 201 });
    }),
    http.delete(path + "/:id", async ({ request, params }) => {
      if (await request.text()) return error("삭제 요청에는 본문을 보내지 않습니다.");
      const id = String(params.id);
      if (deleted.has(id)) return new HttpResponse(null, { status: 204 });
      if (!banners.some((banner) => banner.id === id)) return error("배너를 찾을 수 없습니다.", 404);
      banners = banners.filter((banner) => banner.id !== id); deleted.add(id);
      record("BANNER_DELETED", id, { id, deleted: false }, { id, deleted: true });
      return new HttpResponse(null, { status: 204 });
    }),
    http.get("https://demo.chalkak.example/:asset", ({ params }) => new HttpResponse(
      `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="400" viewBox="0 0 800 400"><rect width="800" height="400" fill="${params.asset === "autumn.svg" ? "#eadbc7" : "#dfe6e0"}"/><circle cx="650" cy="50" r="210" fill="#ffffff" opacity=".35"/><rect x="80" y="95" width="170" height="210" rx="8" fill="#fff" transform="rotate(-7 165 200)"/><rect x="95" y="110" width="140" height="140" fill="#a7b5a3"/><text x="310" y="178" font-family="sans-serif" font-size="38" fill="#3c4a42">Chalkak</text><text x="310" y="222" font-family="sans-serif" font-size="20" fill="#627269">A moment, your story.</text></svg>`, { headers: { "Content-Type": "image/svg+xml" } })),
  ];
}
