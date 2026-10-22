import Link from "next/link";

import styles from "./design-switch.module.css";

export type Design = "classic" | "v2" | "v3";

const designs: { id: Design; href: string; label: string }[] = [
  { id: "classic", href: "/", label: "기본" },
  { id: "v2", href: "/v2", label: "새 디자인" },
  { id: "v3", href: "/v3", label: "갤러리" },
];

/** 디자인 시안을 오가는 토글. 모든 시안 페이지의 헤더에 같은 모양으로 둔다. */
export function DesignSwitch({ current, tone = "light", className }: { current: Design; tone?: "light" | "dark"; className?: string }) {
  return (
    <nav aria-label="디자인 전환" className={[styles.switch, className].filter(Boolean).join(" ")} data-tone={tone}>
      {designs.map((design) => (
        <Link key={design.id} href={design.href} className={styles.option} aria-current={design.id === current ? "page" : undefined}>
          {design.label}
        </Link>
      ))}
    </nav>
  );
}
