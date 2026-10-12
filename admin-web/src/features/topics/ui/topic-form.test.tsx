import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TopicForm } from "./topic-form";

function change(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

function mount() {
  const submit = vi.fn().mockResolvedValue(undefined);
  render(<TopicForm pending={false} onSubmit={submit} />);
  change("주제 제목", "하루의 기록");
  change("주제 날짜", "2026-12-31");
  return submit;
}

describe("topic participation time input", () => {
  it("fills next-day KST end and submits UTC instants", async () => {
    const submit = mount();
    change("참여 시작", "2026-12-31T09:30");
    expect(screen.getByLabelText("참여 종료")).toHaveValue("2027-01-01T09:30");
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(submit).toHaveBeenCalledWith({
      title: "하루의 기록", topicDate: "2026-12-31",
      startsAt: "2026-12-31T00:30:00.000Z", endsAt: "2027-01-01T00:30:00.000Z",
    }));
  });

  it("allows a manually chosen duration other than 24 hours", async () => {
    const submit = mount();
    change("참여 시작", "2026-12-31T09:30");
    change("참여 종료", "2027-01-02T18:00");
    expect(screen.getByLabelText("참여 종료")).toHaveValue("2027-01-02T18:00");
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(submit).toHaveBeenCalledWith(expect.objectContaining({ endsAt: "2027-01-02T09:00:00.000Z" })));
  });

  it("recalculates when start changes, then still allows a manual end", () => {
    mount();
    change("참여 시작", "2026-12-31T09:30");
    change("참여 종료", "2027-01-02T18:00");
    change("참여 시작", "2026-12-31T11:00");
    expect(screen.getByLabelText("참여 종료")).toHaveValue("2027-01-01T11:00");
    change("참여 종료", "2027-01-01T12:00");
    expect(screen.getByLabelText("참여 종료")).toHaveValue("2027-01-01T12:00");
  });

  it("preserves a stored custom duration until its start changes", () => {
    render(<TopicForm pending={false} onSubmit={vi.fn()} initial={{
      title: "기존 주제", topicDate: "2026-12-31",
      startsAt: "2026-12-31T00:30:00Z", endsAt: "2027-01-02T09:00:00Z",
    }} />);
    expect(screen.getByLabelText("참여 종료")).toHaveValue("2027-01-02T18:00");
    change("주제 제목", "제목만 변경");
    expect(screen.getByLabelText("참여 종료")).toHaveValue("2027-01-02T18:00");
    change("참여 시작", "2026-12-31T11:00");
    expect(screen.getByLabelText("참여 종료")).toHaveValue("2027-01-01T11:00");
  });

  it("clears both time values when start is erased", () => {
    mount();
    change("참여 시작", "2026-12-31T09:30");
    change("참여 시작", "");
    expect(screen.getByLabelText("참여 종료")).toHaveValue("");
  });

  it("retains existing end-before-start and topic-date validation", () => {
    const submit = mount();
    change("참여 시작", "2026-12-31T09:30");
    change("참여 종료", "2026-12-31T09:00");
    change("주제 날짜", "2027-01-01");
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(screen.getByText("종료 시각은 시작 시각보다 뒤여야 합니다.")).toBeInTheDocument();
    expect(screen.getByText("주제 날짜는 참여 시작일과 같아야 합니다.")).toBeInTheDocument();
    expect(submit).not.toHaveBeenCalled();
  });
});
