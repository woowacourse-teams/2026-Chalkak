import { describe, expect, it } from "vitest";

import { serializeQuery } from "./query-string";

describe("serializeQuery", () => {
  it("serializes filters and pagination deterministically", () => {
    expect(
      serializeQuery({
        status: "PENDING",
        pageSize: 20,
        page: 2,
        userId: undefined,
        createdAtFrom: new Date("2026-08-01T00:00:00Z"),
      }),
    ).toBe(
      "?createdAtFrom=2026-08-01T00%3A00%3A00.000Z&page=2&pageSize=20&status=PENDING",
    );
  });

  it("omits empty values and repeats array values", () => {
    expect(
      serializeQuery({
        status: ["PENDING", "APPROVED"],
        topicId: "",
        page: null,
      }),
    ).toBe("?status=PENDING&status=APPROVED");
  });
});
