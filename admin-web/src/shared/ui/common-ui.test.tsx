import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ConfirmDialog } from "./confirm-dialog";
import { EmptyState, ErrorState, LoadingSkeleton } from "./feedback-states";
import { Pagination } from "./pagination";

describe("common UI states", () => {
  it("renders loading, empty, and error states accessibly", () => {
    const { rerender } = render(<LoadingSkeleton rows={2} />);
    expect(screen.getByLabelText("콘텐츠 불러오는 중")).toHaveAttribute(
      "aria-busy",
      "true",
    );

    rerender(
      <EmptyState description="조건을 바꿔 보세요." title="결과가 없습니다" />,
    );
    expect(
      screen.getByRole("heading", { name: "결과가 없습니다" }),
    ).toBeInTheDocument();

    rerender(<ErrorState description="네트워크를 확인해 주세요." />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("moves between available pages", async () => {
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(
      <Pagination
        currentPage={2}
        hasNext
        onPageChange={onPageChange}
      />,
    );

    await user.click(screen.getByRole("button", { name: "이전" }));
    await user.click(screen.getByRole("button", { name: "다음" }));

    expect(onPageChange).toHaveBeenNthCalledWith(1, 1);
    expect(onPageChange).toHaveBeenNthCalledWith(2, 3);
  });

  it("requires a reason before confirming a destructive action", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(
      <ConfirmDialog
        confirmLabel="삭제"
        description="삭제 사유를 기록합니다."
        destructive
        onCancel={() => undefined}
        onConfirm={onConfirm}
        open
        reasonField={{ label: "삭제 사유", required: true }}
        title="게시물을 삭제할까요?"
      />,
    );

    const confirmButton = screen.getByRole("button", { name: "삭제" });
    expect(confirmButton).toBeDisabled();

    await user.type(screen.getByRole("textbox", { name: "삭제 사유" }), "중복");
    await user.click(confirmButton);

    expect(onConfirm).toHaveBeenCalledWith("중복");
  });
});
