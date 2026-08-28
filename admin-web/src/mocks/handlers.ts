import { delay, http, HttpResponse } from "msw";

import {
  emptyPostListFixture,
  errorFixtures,
  postDetailFixtures,
  postListFixture,
} from "./fixtures";

const adminApi = "*/api/v1/admin";

export const handlers = [
  http.get(adminApi + "/posts", async ({ request }) => {
    const scenario = new URL(request.url).searchParams.get("scenario");

    if (scenario === "delay") {
      await delay(800);
    }
    if (scenario === "empty") {
      return HttpResponse.json(emptyPostListFixture);
    }
    if (scenario === "bad-request") {
      return HttpResponse.json(errorFixtures.badRequest, { status: 400 });
    }
    if (scenario === "forbidden") {
      return HttpResponse.json(errorFixtures.forbidden, { status: 403 });
    }
    if (scenario === "not-found") {
      return HttpResponse.json(errorFixtures.notFound, { status: 404 });
    }

    return HttpResponse.json(postListFixture);
  }),
  http.get(adminApi + "/posts/:postId", ({ params }) => {
    const post = postDetailFixtures[String(params.postId)];
    if (!post) {
      return HttpResponse.json(errorFixtures.notFound, { status: 404 });
    }
    return HttpResponse.json(post);
  }),
];
