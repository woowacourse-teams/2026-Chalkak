"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { PostStatusTabs } from "@/features/posts/ui/post-status-tabs";
import postStyles from "@/features/posts/ui/posts.module.css";
import { StatusBadge } from "@/shared/ui/status-badge";
import common from "@/shared/ui/management.module.css";
import type { Decision, Evaluation } from "./api";
import { decisionLabels, errorText, probability, questionLabels, statusLabels } from "./display";
import styles from "./jev.module.css";

const routes = [
  ["/posts/jev", "제브 처리 내역"],
  ["/posts/jev/metrics", "성능 지표"],
  ["/posts/jev/prompts", "프롬프트"],
];

export function JevShell({ children }: { children: ReactNode }) {
  const path = usePathname();
  const active = routes.find(([href]) => href === path)?.[0] ?? routes[0][0];
  return <div className={postStyles.postsPage}>
    <h2 className={postStyles.pageTitle}>게시물</h2>
    <PostStatusTabs active="JEV" />
    <div className={styles.page}>
      <nav className={common.statusTabs} aria-label="Jev 사진 검수 메뉴">
        {routes.map(([href, label]) => <Link href={href} key={href} aria-current={active === href ? "page" : undefined}>{label}</Link>)}
      </nav>
      {children}
    </div>
  </div>;
}

export function Heading({ title, subtitle, actions }: {title: string; subtitle?: string; actions?: ReactNode}) {
  return <header className={styles.heading}>
    <div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>
    {actions}
  </header>;
}

export function State({ busy, error, retry, empty }: { busy?: boolean; error?: unknown; retry?: () => void; empty?: string }) {
  return <div className={styles.state} role={error ? "alert" : "status"}>
    <span>{error ? errorText(error) : busy ? "불러오는 중…" : empty}</span>
    {error != null && retry && <button className={common.secondaryButton} onClick={retry}>다시 시도</button>}
  </div>;
}

export function Recommendation({ value }: { value: Decision | null | undefined }) {
  if (!value) return <StatusBadge>추천 없음</StatusBadge>;
  return <StatusBadge tone={value === "APPROVE" ? "success" : value === "REJECT" ? "danger" : "warning"}>{decisionLabels[value]}</StatusBadge>;
}

export function Actual({ status }: { status: string | null | undefined }) {
  return <StatusBadge tone={status === "APPROVED" ? "success" : status === "REJECTED" ? "danger" : status === "PENDING" ? "warning" : "neutral"}>{status ? statusLabels[status] ?? status : "상태 없음"}</StatusBadge>;
}

const outcomes = [["approve", "승인"], ["human_review", "검토"], ["reject", "반려"]] as const;
export function QuestionResults({ evaluation }: { evaluation: Evaluation | null }) {
  const entries = Object.entries(evaluation?.answers ?? {});
  return <div className={styles.stack}>
    {evaluation?.error && <p className={styles.warning}>{statusLabels[evaluation.error] ?? evaluation.error}</p>}
    {entries.length === 0 && <p className={styles.muted}>표시할 질문별 응답이 없어요.</p>}
    {entries.map(([id, answer]) => {
      const choices = Object.keys(answer.probabilities).every((key) => outcomes.some(([choice]) => choice === key))
        ? outcomes : Object.keys(answer.probabilities).map((key) => [key, key] as const);
      return <article key={id} className={styles.question}>
        <header><h3>{questionLabels[id] ?? id}</h3>
          {evaluation?.decisions[id] ? <Recommendation value={evaluation.decisions[id]} /> : <StatusBadge>{answer.choice}</StatusBadge>}
        </header>
        <div className={styles.probabilities}>
          {choices.map(([key, label]) => <div key={key}>
            <span>{label}</span><strong>{probability(answer.probabilities[key])}</strong>
            {answer.probabilities[key] != null && <progress value={answer.probabilities[key]} max={1} aria-label={label + " 확률"} data-choice={key} />}
          </div>)}
        </div>
        <p className={styles.muted}>모델 선택 {outcomes.find(([key]) => key === answer.choice)?.[1] ?? answer.choice} · Confidence {probability(answer.confidence)}</p>
      </article>;
    })}
  </div>;
}
