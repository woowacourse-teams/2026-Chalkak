import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import Home from "./page";

describe("Home", () => {
  it("renders the admin dashboard entry", () => {
    render(<Home />);

    expect(
      screen.getByRole("heading", {
        name: "운영 흐름을 한눈에 확인하세요.",
      }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /게시물/ })).toHaveAttribute(
      "href",
      "/posts",
    );
  });
});
