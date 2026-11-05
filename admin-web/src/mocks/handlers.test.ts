import { describe, expect, it } from "vitest";

describe("admin API mock fixtures", () => {
  it("provides an empty list scenario", async () => {
    const response = await fetch(
      "http://localhost:8080/api/v1/admin/posts?scenario=empty",
    );

    await expect(response.json()).resolves.toMatchObject({
      currentPage: 1,
      hasNext: false,
      posts: [],
    });
  });

  it("provides 400 and 404 error contracts", async () => {
    const badRequest = await fetch(
      "http://localhost:8080/api/v1/admin/posts?scenario=bad-request",
    );
    const notFound = await fetch(
      "http://localhost:8080/api/v1/admin/posts/missing",
    );

    expect(badRequest.status).toBe(400);
    await expect(badRequest.json()).resolves.toEqual({
      errorCode: "BUSINESS_ERROR",
      message: "조회 조건이 올바르지 않습니다.",
    });
    expect(notFound.status).toBe(404);
    await expect(notFound.json()).resolves.toEqual({
      errorCode: "BUSINESS_ERROR",
      message: "게시물을 찾을 수 없습니다.",
    });
  });

  it.each([
    ["users", "사용자를 찾을 수 없습니다."],
    ["topics", "주제를 찾을 수 없습니다."],
  ])("uses the backend 404 contract for missing %s", async (resource, message) => {
    const response = await fetch("http://localhost:8080/api/v1/admin/" + resource + "/missing");
    expect(response.status).toBe(404);
    await expect(response.json()).resolves.toEqual({ errorCode: "BUSINESS_ERROR", message });
  });

  describe("post list filters", () => {
    const base = "http://localhost:8080/api/v1/admin/posts?status=";
    const titles = async (query: string) => {
      const response = await fetch(base + query);
      expect(response.status).toBe(200);
      const body = await response.json() as { posts: { title: string }[] };
      return body.posts.map((post) => post.title);
    };
    const topicId = "bcbcbcbc-bcbc-4bcb-8bcb-bcbcbcbcbcbc";

    it.each([
      ["APPROVED&instagramConsent=revoked", ["초록의 오후"]],
      ["REJECTED&instagramConsent=notAgreed", ["흐린 골목"]],
      ["PENDING&instagramConsent=agreed", ["한강의 노을"]],
      ["APPROVED&instagramConsent=agreed", ["삭제된 게시물"]],
    ])("filters by consent: %s", async (query, expected) => {
      expect(await titles(query)).toEqual(expected);
    });

    it("matches the Instagram ID with the server normalization", async () => {
      expect(await titles("PENDING&instagramId=%20%40CHALKAK_")).toEqual(["한강의 노을"]);
      expect(await titles("PENDING&instagramId=nobody")).toEqual([]);
    });

    it("includes Instagram fields in list items", async () => {
      const response = await fetch(base + "PENDING");
      const body = await response.json() as { posts: Record<string, unknown>[] };
      expect(body.posts[0]).toMatchObject({ instagramUploadAgreed: true, instagramId: "chalkak_creator" });
    });

    it("sorts a topic by like count desc", async () => {
      const response = await fetch("http://localhost:8080/api/v1/admin/posts?sort=likeCountDesc&topicId=" + topicId);
      const body = await response.json() as { posts: { likeCount: number }[] };
      expect(body.posts.map((post) => post.likeCount)).toEqual([34, 12, 2, 0]);
    });

    it.each([
      "sort=likeCountDesc",
      "topicDate=2026-08-28",
      "createdAtFrom=2026-08-01T00:00:00Z",
      "createdAtTo=2026-08-31T00:00:00Z",
    ])("rejects %s like the server", async (query) => {
      const response = await fetch("http://localhost:8080/api/v1/admin/posts?" + query);
      expect(response.status).toBe(400);
      await expect(response.json()).resolves.toEqual({
        errorCode: "BUSINESS_ERROR",
        message: "조회 조건이 올바르지 않습니다.",
      });
    });
  });

  it("uses the backend forbidden error code", async () => {
    const response = await fetch("http://localhost:8080/api/v1/admin/posts?scenario=forbidden");
    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toMatchObject({ errorCode: "FORBIDDEN" });
  });
});
