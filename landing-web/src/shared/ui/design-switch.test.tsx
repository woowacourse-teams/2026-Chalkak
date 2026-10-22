import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { DesignSwitch } from "./design-switch";

describe("디자인 전환", () => {
  it("두 디자인으로 가는 링크를 두고 현재 디자인을 표시한다", () => {
    render(<DesignSwitch current="v2" />);
    const classic = screen.getByRole("link", { name: "기본" });
    const v2 = screen.getByRole("link", { name: "새 디자인" });
    expect(classic).toHaveAttribute("href", "/");
    expect(v2).toHaveAttribute("href", "/v2");
    expect(v2).toHaveAttribute("aria-current", "page");
    expect(classic).not.toHaveAttribute("aria-current");
  });

  it("갤러리 디자인으로 가는 링크를 두고 현재 디자인으로 표시할 수 있다", () => {
    render(<DesignSwitch current="v3" />);
    const v3 = screen.getByRole("link", { name: "갤러리" });
    expect(v3).toHaveAttribute("href", "/v3");
    expect(v3).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "새 디자인" })).not.toHaveAttribute("aria-current");
  });
});
