import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { PostImageViewer } from "./post-image-viewer";

const imageAlt = "검수할 게시물 사진";
const imageSrc = "/test-photo.jpg";

describe("PostImageViewer", () => {
  let previousBodyOverflow: string;

  beforeEach(() => {
    previousBodyOverflow = document.body.style.overflow;
  });

  afterEach(() => {
    cleanup();
    document.body.style.overflow = previousBodyOverflow;
    vi.restoreAllMocks();
  });

  it("shows the supplied image and an accessible expand button", () => {
    render(<PostImageViewer alt={imageAlt} src={imageSrc} />);

    expect(screen.getByRole("img", { name: imageAlt })).toBeVisible();
    expect(screen.getByRole("button", { name: "사진 확대" })).toBeVisible();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it.each([undefined, null, ""])(
    "shows a fallback without an expand button for an absent source: %s",
    (src) => {
      render(<PostImageViewer alt={imageAlt} src={src} />);

      expect(
        screen.getByRole("img", { name: "이미지를 불러올 수 없음" }),
      ).toHaveTextContent("이미지 없음");
      expect(
        screen.queryByRole("button", { name: "사진 확대" }),
      ).not.toBeInTheDocument();
    },
  );

  it("replaces a failed preview with an accessible fallback and disables expansion", () => {
    render(<PostImageViewer alt={imageAlt} src={imageSrc} />);

    fireEvent.error(screen.getByRole("img", { name: imageAlt }));

    expect(
      screen.getByRole("img", { name: "이미지를 불러올 수 없음" }),
    ).toHaveTextContent("이미지 없음");
    expect(
      screen.queryByRole("button", { name: "사진 확대" }),
    ).not.toBeInTheDocument();
  });

  it("retries a new image source after the previous preview failed", () => {
    const { rerender } = render(
      <PostImageViewer alt={imageAlt} src={imageSrc} />,
    );
    fireEvent.error(screen.getByRole("img", { name: imageAlt }));

    rerender(<PostImageViewer alt={imageAlt} src="/replacement-photo.jpg" />);

    expect(screen.getByRole("img", { name: imageAlt })).toHaveAttribute(
      "src",
      expect.stringMatching(/\/replacement-photo\.jpg$/),
    );
    expect(screen.getByRole("button", { name: "사진 확대" })).toBeVisible();
    expect(
      screen.queryByRole("img", { name: "이미지를 불러올 수 없음" }),
    ).not.toBeInTheDocument();
  });

  it("opens an accessible dialog in fit mode and switches zoom modes", async () => {
    const user = userEvent.setup();
    render(<PostImageViewer alt={imageAlt} src={imageSrc} />);

    await user.click(screen.getByRole("button", { name: "사진 확대" }));

    const dialog = screen.getByRole("dialog", { name: "사진 확대" });
    const fitButton = within(dialog).getByRole("button", { name: "화면 맞춤" });
    const zoomButton = within(dialog).getByRole("button", { name: "2배 확대" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(within(dialog).getByRole("img", { name: imageAlt })).toBeVisible();
    expect(fitButton).toHaveAttribute("aria-pressed", "true");
    expect(zoomButton).toHaveAttribute("aria-pressed", "false");
    expect(within(dialog).getByRole("button", { name: /닫기/ })).toBeVisible();

    await user.click(zoomButton);
    expect(zoomButton).toHaveAttribute("aria-pressed", "true");
    expect(fitButton).toHaveAttribute("aria-pressed", "false");

    await user.click(zoomButton);
    expect(zoomButton).toHaveAttribute("aria-pressed", "true");

    await user.click(fitButton);
    expect(fitButton).toHaveAttribute("aria-pressed", "true");
    expect(zoomButton).toHaveAttribute("aria-pressed", "false");
  });

  it("closes with its close button and restores focus to the expand button", async () => {
    const user = userEvent.setup();
    render(<PostImageViewer alt={imageAlt} src={imageSrc} />);
    const expandButton = screen.getByRole("button", { name: "사진 확대" });
    await user.click(expandButton);

    const dialog = screen.getByRole("dialog", { name: "사진 확대" });
    const closeButton = within(dialog).getByRole("button", { name: /닫기/ });
    expect(closeButton).toHaveFocus();
    await user.click(closeButton);

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(expandButton).toHaveFocus();
  });

  it("closes with Escape and resets to fit mode when reopened", async () => {
    const user = userEvent.setup();
    render(<PostImageViewer alt={imageAlt} src={imageSrc} />);
    const expandButton = screen.getByRole("button", { name: "사진 확대" });
    await user.click(expandButton);
    await user.click(
      within(screen.getByRole("dialog", { name: "사진 확대" })).getByRole(
        "button",
        { name: "2배 확대" },
      ),
    );

    await user.keyboard("{Escape}");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(expandButton).toHaveFocus();
    await user.click(expandButton);
    const reopenedDialog = screen.getByRole("dialog", { name: "사진 확대" });
    expect(
      within(reopenedDialog).getByRole("button", { name: "화면 맞춤" }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(
      within(reopenedDialog).getByRole("button", { name: "2배 확대" }),
    ).toHaveAttribute("aria-pressed", "false");
  });

  it("traps forward and backward Tab focus across the toolbar and scroll region", async () => {
    const user = userEvent.setup();
    render(
      <>
        <button type="button">이전 배경 작업</button>
        <PostImageViewer alt={imageAlt} src={imageSrc} />
        <button type="button">다음 배경 작업</button>
      </>,
    );
    await user.click(screen.getByRole("button", { name: "사진 확대" }));

    const dialog = screen.getByRole("dialog", { name: "사진 확대" });
    const fitButton = within(dialog).getByRole("button", { name: "화면 맞춤" });
    const zoomButton = within(dialog).getByRole("button", { name: "2배 확대" });
    const closeButton = within(dialog).getByRole("button", { name: /닫기/ });
    const scrollRegion = within(dialog).getByRole("region", { name: "확대 사진" });
    expect(closeButton).toHaveFocus();

    await user.tab();
    expect(scrollRegion).toHaveFocus();
    await user.tab();
    expect(fitButton).toHaveFocus();
    await user.tab();
    expect(zoomButton).toHaveFocus();
    await user.tab();
    expect(closeButton).toHaveFocus();

    await user.tab({ shift: true });
    expect(zoomButton).toHaveFocus();
    await user.tab({ shift: true });
    expect(fitButton).toHaveFocus();
    await user.tab({ shift: true });
    expect(scrollRegion).toHaveFocus();
    await user.tab({ shift: true });
    expect(closeButton).toHaveFocus();
  });

  it("locks body scrolling while open and restores the existing value on close", async () => {
    document.body.style.overflow = "scroll";
    const user = userEvent.setup();
    render(<PostImageViewer alt={imageAlt} src={imageSrc} />);

    await user.click(screen.getByRole("button", { name: "사진 확대" }));
    expect(document.body.style.overflow).toBe("hidden");

    await user.keyboard("{Escape}");
    expect(document.body.style.overflow).toBe("scroll");
  });

  it("restores body scrolling when an open viewer unmounts", async () => {
    document.body.style.overflow = "auto";
    const user = userEvent.setup();
    const { unmount } = render(
      <PostImageViewer alt={imageAlt} src={imageSrc} />,
    );

    await user.click(screen.getByRole("button", { name: "사진 확대" }));
    expect(document.body.style.overflow).toBe("hidden");
    unmount();

    expect(document.body.style.overflow).toBe("auto");
  });

  it("keeps the dialog closable after its expanded image fails", async () => {
    const user = userEvent.setup();
    render(<PostImageViewer alt={imageAlt} src={imageSrc} />);
    const expandButton = screen.getByRole("button", { name: "사진 확대" });
    await user.click(expandButton);
    const dialog = screen.getByRole("dialog", { name: "사진 확대" });

    fireEvent.error(within(dialog).getByRole("img", { name: imageAlt }));

    expect(
      within(dialog).getByRole("img", { name: "이미지를 불러올 수 없음" }),
    ).toHaveTextContent("이미지 없음");
    expect(screen.getByRole("img", { name: imageAlt })).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: /닫기/ }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(expandButton).toHaveFocus();
  });

  it("does not expose moderation actions or issue requests during image inspection", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    const xhrOpenSpy = vi.spyOn(XMLHttpRequest.prototype, "open");
    const user = userEvent.setup();
    render(<PostImageViewer alt={imageAlt} src={imageSrc} />);

    await user.click(screen.getByRole("button", { name: "사진 확대" }));
    const dialog = screen.getByRole("dialog", { name: "사진 확대" });
    expect(
      within(dialog).queryByRole("button", { name: /승인|거절|삭제/ }),
    ).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "2배 확대" }));
    await user.click(within(dialog).getByRole("button", { name: "화면 맞춤" }));
    await user.click(within(dialog).getByRole("button", { name: /닫기/ }));

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(xhrOpenSpy).not.toHaveBeenCalled();
  });
});
