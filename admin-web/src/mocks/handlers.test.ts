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
      errorCode: "INVALID_REQUEST",
      message: "조회 조건이 올바르지 않습니다.",
    });
    expect(notFound.status).toBe(404);
    await expect(notFound.json()).resolves.toEqual({
      errorCode: "POST_NOT_FOUND",
      message: "게시물을 찾을 수 없습니다.",
    });
  });
});
