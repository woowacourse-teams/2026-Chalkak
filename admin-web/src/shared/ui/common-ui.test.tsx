import { act, render, screen, within } from "@testing-library/react";
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

  it("accepts a multiline reason while retaining field constraints", async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(<ConfirmDialog open title="거절 확인" description="사유를 기록합니다." confirmLabel="거절" reasonField={{ label: "거절 사유", required: true, maxLength: 500, placeholder: "사유를 입력해 주세요." }} onCancel={vi.fn()} onConfirm={onConfirm} />);

    const reason = screen.getByRole("textbox", { name: "거절 사유" });
    expect(reason.tagName).toBe("TEXTAREA");
    expect(reason).toHaveAttribute("rows", "3");
    expect(reason).toHaveAttribute("maxlength", "500");
    expect(reason).toBeRequired();
    expect(reason).not.toHaveFocus();

    await user.type(reason, "첫째 줄{Enter}둘째 줄");
    await user.click(screen.getByRole("button", { name: "거절" }));
    expect(onConfirm).toHaveBeenCalledWith("첫째 줄\n둘째 줄");
  });

  it("announces an error inside the dialog and retains the reason for retry", async () => {
    const user = userEvent.setup();
    const props = {
      open: true,
      title: "거절 확인",
      description: "사유를 기록합니다.",
      confirmLabel: "거절",
      reasonField: { label: "거절 사유", required: true },
      onCancel: vi.fn(),
      onConfirm: vi.fn(),
    };
    const { rerender } = render(<ConfirmDialog {...props} />);
    await user.type(screen.getByRole("textbox", { name: "거절 사유" }), "확인이 필요한 내용");

    rerender(<ConfirmDialog {...props} error="처리하지 못했습니다. 다시 시도해 주세요." />);
    const dialog = screen.getByRole("dialog", { name: "거절 확인" });
    const error = within(dialog).getByRole("alert");
    expect(error).toHaveTextContent("처리하지 못했습니다. 다시 시도해 주세요.");
    expect(error).toHaveFocus();
    expect(screen.getByRole("textbox", { name: "거절 사유" })).toHaveValue("확인이 필요한 내용");
    await user.tab({ shift: true });
    expect(screen.getByRole("button", { name: "거절" })).toHaveFocus();

    rerender(<ConfirmDialog {...props} error={null} />);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "거절" }));
    expect(props.onConfirm).toHaveBeenCalledWith("확인이 필요한 내용");
  });

  it("keeps pending actions inside the modal and ignores Escape", async () => {
    const user = userEvent.setup();
    const cancel = vi.fn();
    render(<><button>외부 버튼</button><ConfirmDialog open pending title="처리 확인" description="처리하고 있습니다." confirmLabel="확인" reasonField={{ label: "처리 사유" }} onCancel={cancel} onConfirm={vi.fn()} /></>);
    const heading = screen.getByRole("heading", { name: "처리 확인" });
    expect(heading).toHaveFocus();
    expect(screen.getByRole("textbox", { name: "처리 사유" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "처리 중…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
    screen.getByRole("button", { name: "외부 버튼" }).focus();
    expect(heading).toHaveFocus();
    await user.keyboard("{Escape}");
    await user.tab();
    expect(cancel).not.toHaveBeenCalled();
    expect(heading).toHaveFocus();
  });

  it("tracks the visible keyboard viewport and removes its listeners on close", () => {
    class MockVisualViewport extends EventTarget {
      height = 360;
      offsetTop = 120;
    }
    const viewport = new MockVisualViewport();
    const subscribe = vi.spyOn(viewport, "addEventListener");
    const unsubscribe = vi.spyOn(viewport, "removeEventListener");
    vi.stubGlobal("visualViewport", viewport);

    try {
      const { unmount } = render(<ConfirmDialog open title="거절 확인" description="사유를 기록합니다." confirmLabel="거절" reasonField={{ label: "거절 사유" }} onCancel={vi.fn()} onConfirm={vi.fn()} />);
      const overlay = screen.getByRole("dialog").parentElement;
      expect(overlay).toHaveStyle({ top: "120px", height: "360px" });
      expect(screen.getByRole("heading", { name: "거절 확인" })).toHaveFocus();

      act(() => {
        viewport.height = 260;
        viewport.offsetTop = 180;
        viewport.dispatchEvent(new Event("resize"));
      });
      expect(overlay).toHaveStyle({ top: "180px", height: "260px" });

      act(() => {
        viewport.offsetTop = 210;
        viewport.dispatchEvent(new Event("scroll"));
      });
      expect(overlay).toHaveStyle({ top: "210px", height: "260px" });

      unmount();
      expect(subscribe).toHaveBeenCalledWith("resize", expect.any(Function));
      expect(subscribe).toHaveBeenCalledWith("scroll", expect.any(Function));
      for (const [event, listener] of subscribe.mock.calls) {
        expect(unsubscribe).toHaveBeenCalledWith(event, listener);
      }
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("leaves viewport geometry to CSS when visualViewport is unavailable", () => {
    vi.stubGlobal("visualViewport", undefined);
    try {
      render(<ConfirmDialog open title="작업 확인" description="내용을 확인해 주세요." confirmLabel="확인" onCancel={vi.fn()} onConfirm={vi.fn()} />);
      expect(screen.getByRole("dialog").parentElement).not.toHaveAttribute("style");
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
