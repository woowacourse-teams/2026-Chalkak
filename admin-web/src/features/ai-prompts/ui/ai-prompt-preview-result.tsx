import type { AiPromptPreviewResponse } from "@/shared/api/contracts";
import { StatusBadge } from "@/shared/ui/status-badge";
import { failureTypeLabel, formatLatency, formatTokenCount } from "../model/ai-prompt";
import common from "@/shared/ui/management.module.css";
import styles from "./ai-prompts.module.css";

function Usage({ usage }: { usage: AiPromptPreviewResponse["usage"] }) {
  return <>
    <div><dt>입력 토큰</dt><dd>{usage ? formatTokenCount(usage.inputTokens) : "-"}</dd></div>
    <div><dt>캐시된 입력 토큰</dt><dd>{usage ? formatTokenCount(usage.cachedInputTokens) : "-"}</dd></div>
    <div><dt>출력 토큰</dt><dd>{usage ? formatTokenCount(usage.outputTokens) : "-"}</dd></div>
    <div><dt>추론 토큰</dt><dd>{usage ? formatTokenCount(usage.reasoningTokens) : "-"}</dd></div>
  </>;
}

export function AiPromptPreviewResult({ response }: { response: AiPromptPreviewResponse }) {
  const details = <dl className={common.meta}>
    <Usage usage={response.usage} />
    <div><dt>응답 시간</dt><dd>{formatLatency(response.latencyMs)}</dd></div>
    <div><dt>응답 ID</dt><dd>{response.responseId ?? "-"}</dd></div>
  </dl>;
  if (!response.succeeded) {
    return <section className={styles.result} data-succeeded="false" aria-label="미리보기 결과">
      <div className={styles.promptHeading}><h4>미리보기 실패</h4><StatusBadge tone="danger">{failureTypeLabel(response.failureType)}</StatusBadge></div>
      <p className={styles.failure}>{response.retryable
        ? "일시적인 문제일 수 있습니다. 다시 시도할 수 있습니다."
        : "다시 실행해도 같은 결과일 수 있습니다. 입력이나 설정을 확인해 주세요."}</p>
      <p className={styles.helper}>재시도 가능 여부: {response.retryable === null ? "-" : response.retryable ? "예" : "아니오"}</p>
      {details}
    </section>;
  }
  const result = response.result;
  return <section className={styles.result} data-succeeded="true" aria-label="미리보기 결과">
    <div className={styles.promptHeading}><h4>미리보기 성공</h4><StatusBadge tone="success">성공</StatusBadge></div>
    {result ? <>
      <div><span className={styles.helper}>종합 점수</span><div className={styles.score}>{result.overallScore}</div></div>
      <ul className={styles.criteria} aria-label="평가 항목별 점수">
        {result.criteria.map((criterion) => <li key={criterion.key}><span>{criterion.label}</span><strong>{criterion.score}</strong></li>)}
      </ul>
      <dl className={common.meta}><div><dt>독특한 시각</dt><dd>{result.distinctivePerspective ? "예" : "아니오"}</dd></div></dl>
      <p className={styles.feedback}>{result.feedback}</p>
    </> : null}
    {response.output ? <dl className={common.meta}>
      <div><dt>모델 원점수 · 구도</dt><dd>{response.output.compositionScore}</dd></div>
      <div><dt>모델 원점수 · 주제 표현</dt><dd>{response.output.themeExpressionScore}</dd></div>
    </dl> : null}
    {response.output ? <p className={styles.helper}>주제 표현 원점수가 기준 점수 이상이면 독특한 시각이 &apos;예&apos;입니다. 기준 점수를 조정할 때 참고하세요.</p> : null}
    {details}
  </section>;
}
