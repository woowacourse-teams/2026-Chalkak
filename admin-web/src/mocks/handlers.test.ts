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

  it("uses the backend forbidden error code", async () => {
    const response = await fetch("http://localhost:8080/api/v1/admin/posts?scenario=forbidden");
    expect(response.status).toBe(403);
    await expect(response.json()).resolves.toMatchObject({ errorCode: "FORBIDDEN" });
  });
});
