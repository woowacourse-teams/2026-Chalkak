"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import common from "@/shared/ui/management.module.css";
import { StatusBadge } from "@/shared/ui/status-badge";
import { jevApi } from "./api";
import { displayDate, goldLabels, runStatus, slackLabel } from "./display";
import { Actual, Heading, Recommendation, State } from "./ui";
import styles from "./jev.module.css";

export function JevHistory() {
  const [scope, setScope] = useState("false");
  const [disposition, setDisposition] = useState("");
  const [postId, setPostId] = useState("");
  const [page, setPage] = useState(0);
  const params = new URLSearchParams({ page: String(page), pageSize: "20" });
  if (scope) params.set("preview", scope);
  if (disposition) params.set("disposition", disposition);
  if (postId) params.set("postId", postId);
  const history = useQuery({
    queryKey: ["jev", "history", params.toString()],
    queryFn: ({ signal }) => jevApi.history(params, signal),
  });
  function applyPost(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPostId(String(new FormData(event.currentTarget).get("postId") ?? "").trim());
    setPage(0);
  }
  const rows = history.data?.rows ?? [];
  return <>
    <Heading title="제브 처리 내역" subtitle="제브 추천을 확인하고 실제로 공개되거나 관리자에게 넘어간 사진을 찾아보세요." />
    <form className={common.filters} onSubmit={applyPost}>
      <label><span>검수 범위</span><select value={scope} onChange={(event) => { setScope(event.target.value); setPage(0); }}>
        <option value="false">운영</option><option value="true">시험</option><option value="">전체</option>
      </select></label>
      <label><span>서버 처리</span><select value={disposition} onChange={(event) => { setDisposition(event.target.value); setPage(0); }}>
        <option value="">전체</option><option value="AUTO_APPROVED">자동 승인</option><option value="ADMIN_REVIEW">관리자 검토</option><option value="SKIPPED">미반영</option>
      </select></label>
      <label><span>게시물 ID</span><input name="postId" placeholder="게시물 ID로 찾기" /></label>
      <button type="submit">검색</button>
    </form>
    {history.isPending || history.isError || rows.length === 0
      ? <State busy={history.isPending} error={history.error} retry={() => history.refetch()} empty="조건에 맞는 검수 이력이 없어요." />
      : <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead><tr><th scope="col">주제 · 접수 시각</th><th scope="col">제브 추천</th><th scope="col">서버 처리</th><th scope="col">실제 게시물</th><th scope="col">관리자 정답</th><th scope="col">Slack</th><th scope="col">상세</th></tr></thead>
          <tbody>{rows.map((row) => <tr key={row.jobId}>
            <td><Link className={common.primaryLink} href={`/posts/jev/${row.jobId}`}><strong>{row.topic}</strong><span>{displayDate(row.createdAt)} · {row.preview ? "시험" : "운영"}</span></Link></td>
            <td><Recommendation value={row.recommendation} /></td>
            <td>{runStatus(row.preview, row.disposition, row.finishedAt)}</td>
            <td><Actual status={row.postStatus} /></td>
            <td>{row.goldDecision ? goldLabels[row.goldDecision] : "미평가"}</td>
            <td><StatusBadge tone={row.slackStatus === "FAILED" ? "danger" : "neutral"}>{slackLabel(row.slackStatus)}</StatusBadge></td>
            <td><Link className={styles.textLink} href={`/posts/jev/${row.jobId}`} aria-label={`${row.topic} 검수 상세 보기`}>상세 보기 →</Link></td>
          </tr>)}</tbody>
        </table>
      </div>}
    <div className={styles.pager}>
      <button className={common.secondaryButton} disabled={page === 0} onClick={() => setPage((value) => value - 1)}>이전</button>
      <span>{page + 1} 페이지</span>
      <button className={common.secondaryButton} disabled={!history.data?.hasNext} onClick={() => setPage((value) => value + 1)}>다음</button>
    </div>
  </>;
}
