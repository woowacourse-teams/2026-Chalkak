import styles from "./common-ui.module.css";

interface EmptyStateProps {
  title: string;
  description: string;
}

export function EmptyState({ title, description }: EmptyStateProps) {
  return (
    <section className={styles.feedbackState}>
      <span aria-hidden="true" className={styles.stateMark}>
        0
      </span>
      <h2>{title}</h2>
      <p>{description}</p>
    </section>
  );
}

interface ErrorStateProps {
  title?: string;
  description: string;
  onRetry?: () => void;
}

export function ErrorState({
  title = "정보를 불러오지 못했습니다",
  description,
  onRetry,
}: ErrorStateProps) {
  return (
    <section className={styles.feedbackState} role="alert">
      <span aria-hidden="true" className={styles.errorMark}>
        !
      </span>
      <h2>{title}</h2>
      <p>{description}</p>
      {onRetry ? (
        <button onClick={onRetry} type="button">
          다시 시도
        </button>
      ) : null}
    </section>
  );
}

export function LoadingSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div aria-busy="true" aria-label="콘텐츠 불러오는 중" className={styles.skeleton}>
      {Array.from({ length: rows }, (_, index) => (
        <span key={index} />
      ))}
    </div>
  );
}
