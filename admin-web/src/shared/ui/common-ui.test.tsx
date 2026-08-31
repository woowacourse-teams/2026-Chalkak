import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { useState } from "react";

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

  it("focuses context first, traps tab focus, and restores the trigger", async () => {
    const user = userEvent.setup();
    function Example() {
      const [open, setOpen] = useState(false);
      return <><button onClick={() => setOpen(true)}>작업 열기</button><ConfirmDialog open={open} title="작업 확인" description="내용을 확인해 주세요." confirmLabel="확인" onCancel={() => setOpen(false)} onConfirm={() => setOpen(false)} /></>;
    }
    render(<Example />);
    const trigger = screen.getByRole("button", { name: "작업 열기" });
    await user.click(trigger);
    expect(screen.getByRole("heading", { name: "작업 확인" })).toHaveFocus();
    await user.tab({ shift: true });
    expect(screen.getByRole("button", { name: "확인" })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole("button", { name: "취소" })).toHaveFocus();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("clears the prior reason each time the dialog reopens", async () => {
    const user = userEvent.setup();
    function Example() {
      const [open, setOpen] = useState(false);
      return <><button onClick={() => setOpen(true)}>사유 입력</button><ConfirmDialog open={open} title="거절 확인" description="사유를 입력해 주세요." confirmLabel="거절" reasonField={{ label: "거절 사유", required: true }} onCancel={() => setOpen(false)} onConfirm={() => setOpen(false)} /></>;
    }
    render(<Example />);
    await user.click(screen.getByRole("button", { name: "사유 입력" }));
    expect(screen.getByRole("heading", { name: "거절 확인" })).toHaveFocus();
    await user.type(screen.getByRole("textbox", { name: "거절 사유" }), "이전 사유");
    await user.click(screen.getByRole("button", { name: "거절" }));
    await user.click(screen.getByRole("button", { name: "사유 입력" }));
    expect(screen.getByRole("textbox", { name: "거절 사유" })).toHaveValue("");
    expect(screen.getByRole("button", { name: "거절" })).toBeDisabled();
  });

  it("keeps pending actions inside the modal and ignores Escape", async () => {
    const user = userEvent.setup();
    const cancel = vi.fn();
    render(<><button>외부 버튼</button><ConfirmDialog open pending title="처리 확인" description="처리하고 있습니다." confirmLabel="확인" onCancel={cancel} onConfirm={vi.fn()} /></>);
    const heading = screen.getByRole("heading", { name: "처리 확인" });
    expect(heading).toHaveFocus();
    screen.getByRole("button", { name: "외부 버튼" }).focus();
    expect(heading).toHaveFocus();
    await user.keyboard("{Escape}");
    await user.tab();
    expect(cancel).not.toHaveBeenCalled();
    expect(heading).toHaveFocus();
  });
});
