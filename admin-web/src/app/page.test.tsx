import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import Home from "./page";

describe("Home", () => {
  it("renders the admin web foundation", () => {
    render(<Home />);

    expect(
      screen.getByRole("heading", { name: "관리자 웹 개발 기반" }),
    ).toBeInTheDocument();
  });
});
