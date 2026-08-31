"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useRef, useState } from "react";

import { ApiError } from "@/shared/api/errors";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { getSafeReturnTo } from "@/features/auth/model/return-to";
import {
  ErrorState,
  LoadingSkeleton,
} from "@/shared/ui/feedback-states";
import { StatusBadge } from "@/shared/ui/status-badge";
import { useToast } from "@/shared/ui/toast";

import {
  useAdminPost,
  useDeleteAdminPost,
  useModerateAdminPost,
} from "../api/post-hooks";
import {
  formatFileSize,
  formatInstant,
  getPostDisplayStatus,
  getPostErrorMessage,
} from "../model/post-display";
import { PostImageViewer } from "./post-image-viewer";
import styles from "./posts.module.css";

type PendingAction = "approve" | "reject" | "delete" | null;

function safeReturnTo(value: string | null) {
  const safe = getSafeReturnTo(value);
  return /^\/(?:posts|audit-logs)(?:\?|$)/.test(safe) ? safe : "/posts?status=PENDING";
}

export function PostDetailScreen({ postId }: { postId: string }) {
  const searchParams = useSearchParams();
  const router = useRouter();
  const postQuery = useAdminPost(postId);
  const moderation = useModerateAdminPost();
  const deletion = useDeleteAdminPost();
  const { showToast } = useToast();
  const [action, setAction] = useState<PendingAction>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [isConfirming, setIsConfirming] = useState(false);
  const requestLockRef = useRef(false);
  const isProcessing = isConfirming || moderation.isPending || deletion.isPending;
  const returnTo = safeReturnTo(searchParams.get("returnTo"));

  const openAction = (next: PendingAction) => {
    setActionError(null);
    setAction(next);
  };

  if (postQuery.isPending) {
    return <LoadingSkeleton rows={6} />;
  }

  // A temporary refresh failure must not discard an in-progress reason.
  // Access errors and unavailable posts still use the error screen.
  const keepDialogOnRefreshFailure = action !== null && postQuery.isRefetchError &&
    postQuery.error instanceof ApiError && (
      postQuery.error.kind === "network" || postQuery.error.kind === "timeout" ||
      (postQuery.error.status ?? 0) >= 500
    );
  if ((postQuery.isError && !keepDialogOnRefreshFailure) || !postQuery.data) {
    return (
      <div className={styles.detailPage}>
      <Link className={styles.backLink} href={returnTo}>← 이전 목록으로 돌아가기</Link>
      <ErrorState
        description={getPostErrorMessage(postQuery.error)}
        onRetry={() => postQuery.refetch()}
        title={
          (postQuery.error as { status?: number })?.status === 404
            ? "게시물을 찾을 수 없습니다"
            : "게시물 상세를 불러오지 못했습니다"
        }
      />
      </div>
    );
  }

  const post = postQuery.data;

  const status = getPostDisplayStatus(post);
  const canModerate =
    post.moderationStatus === "PENDING" && post.deletedAt === null;
  const canDelete = post.deletedAt === null;

  const confirmAction = async (reason: string) => {
    if (!action || isProcessing || requestLockRef.current) {
      return;
    }

    requestLockRef.current = true;
    setIsConfirming(true);
    setActionError(null);
    try {
      if (action === "approve") {
        await moderation.mutateAsync({ postId, status: "APPROVED" });
        showToast("게시물을 승인했습니다.", "success");
      } else if (action === "reject") {
        await moderation.mutateAsync({
          postId,
          status: "REJECTED",
          reason,
        });
        showToast("게시물을 거절했습니다.", "success");
      } else {
        await deletion.mutateAsync({ postId, reason });
        showToast("게시물을 삭제했습니다.", "success");
      }
      setAction(null);
      router.push(returnTo);
    } catch (error) {
      const message = getPostErrorMessage(error);
      setActionError(message);
      const latest = await postQuery.refetch();
      if (latest.data && (latest.data.deletedAt || (action !== "delete" && latest.data.moderationStatus !== "PENDING"))) {
        setAction(null);
        showToast(message, "error");
      }
    } finally {
      requestLockRef.current = false;
      setIsConfirming(false);
    }
  };

  const dialog =
    action === "approve"
      ? {
          title: "이 게시물을 승인할까요?",
          description: "승인하면 사용자 서비스에 게시물이 노출될 수 있습니다.",
          confirmLabel: "승인",
        }
      : action === "reject"
        ? {
            title: "이 게시물을 거절할까요?",
            description: "거절 사유는 처리 이력과 감사 로그에 기록됩니다.",
            confirmLabel: "거절",
            reasonField: {
              label: "거절 사유",
              placeholder: "거절 사유를 입력해 주세요.",
              required: true,
              maxLength: 500,
            },
          }
        : {
            title: "이 게시물을 삭제할까요?",
            description:
              "게시물은 사용자 화면에서 숨겨지며 원본과 썸네일은 관리자 확인을 위해 보관됩니다.",
            confirmLabel: "삭제",
            reasonField: {
              label: "삭제 사유",
              placeholder: "삭제 사유를 입력해 주세요.",
              required: true,
              maxLength: 500,
            },
          };

  return (
    <div className={styles.detailPage} data-review-mode={canModerate}>
      <div className={styles.detailTopBar}>
        <Link className={styles.backLink} href={returnTo}>
          ← 이전 목록으로 돌아가기
        </Link>
        <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
      </div>

      <div className={styles.detailGrid}>
        <div className={styles.reviewHeading}>
          <h2>{post.title ?? "제목 없음"}</h2>
          {post.topic ? <Link className={styles.topicContext} href={"/topics/" + post.topic.topicId + "?" + new URLSearchParams({ returnTo })}>{post.topic.title} · {post.topic.topicDate}</Link> : <p className={styles.topicContext}>연결된 주제 없음</p>}
        </div>
        <section className={styles.photoColumn} aria-label="검수 사진">
          <PostImageViewer
            key={post.photo?.originalImageUrl ?? "missing"}
            alt={(post.title ?? "제목 없는 게시물") + " 원본 이미지"}
            src={post.photo?.originalImageUrl}
          />
        </section>
        <section className={styles.detailPanel}>
          <h3 className={styles.infoTitle}>게시물 정보</h3>

          <dl className={styles.detailMeta}>
            <div>
              <dt>작성자</dt>
              <dd>{post.author ? <Link className={styles.relatedLink} href={"/users/" + post.author.userId + "?" + new URLSearchParams({ returnTo })}>{post.author.email ?? "이메일 정보 없음"}</Link> : "탈퇴/정보 없음"}</dd>
            </div>
            <div>
              <dt>등록 시각</dt>
              <dd>{formatInstant(post.createdAt)}</dd>
            </div>
          </dl>
          <details className={styles.extraInfo}>
            <summary>추가 정보</summary>
            <dl className={styles.detailMeta}>
              <div><dt>처리 시각</dt><dd>{formatInstant(post.moderatedAt)}</dd></div>
              <div><dt>게시물 ID</dt><dd>{post.postId}</dd></div>
              <div><dt>사진 크기</dt><dd>{post.photo?.metadata.width ?? "—"} × {post.photo?.metadata.height ?? "—"} px</dd></div>
              <div><dt>파일 크기</dt><dd>{formatFileSize(post.photo?.metadata.byteSize)}</dd></div>
              <div><dt>좋아요</dt><dd>{post.likeCount.toLocaleString("ko-KR")}개</dd></div>
            </dl>
            {canDelete ? <button className={styles.deleteButton} disabled={isProcessing} onClick={() => openAction("delete")} type="button">게시물 삭제</button> : null}
          </details>

          {post.rejectionReason ? (
            <p className={styles.moderationNote}>
              거절 사유 · {post.rejectionReason}
            </p>
          ) : null}
          {post.deletedAt ? (
            <p className={styles.moderationNote}>
              삭제 시각 · {formatInstant(post.deletedAt)}
            </p>
          ) : null}

          {canModerate ? <div aria-label="게시물 작업" className={styles.detailActions} role="group">
                <button
                  className={styles.rejectButton}
                  disabled={isProcessing}
                  onClick={() => openAction("reject")}
                  type="button"
                >
                  거절
                </button>
                <button
                  className={styles.approveButton}
                  disabled={isProcessing}
                  onClick={() => openAction("approve")}
                  type="button"
                >
                  승인
                </button>
          </div> : null}
        </section>
      </div>

      <ConfirmDialog
        confirmLabel={dialog.confirmLabel}
        description={dialog.description}
        destructive={action === "reject" || action === "delete"}
        error={actionError}
        onCancel={() => openAction(null)}
        onConfirm={confirmAction}
        open={action !== null}
        pending={isProcessing}
        reasonField={dialog.reasonField}
        title={dialog.title}
      />
    </div>
  );
}
