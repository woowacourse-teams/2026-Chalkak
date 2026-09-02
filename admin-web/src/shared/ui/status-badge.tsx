import styles from "./common-ui.module.css";

type StatusTone = "neutral" | "info" | "warning" | "success" | "danger";

interface StatusBadgeProps {
  children: string;
  tone?: StatusTone;
}

export function StatusBadge({
  children,
  tone = "neutral",
}: StatusBadgeProps) {
  return (
    <span className={[styles.statusBadge, styles[tone]].join(" ")}>
      {children}
    </span>
  );
}
