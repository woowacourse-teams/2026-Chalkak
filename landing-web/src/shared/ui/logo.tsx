/** 앱 아이콘의 뷰파인더·렌즈·사인을 옮긴 로고 마크 */
export function LogoMark({ size = 32, className }: { size?: number; className?: string }) {
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 100 100" fill="none" aria-hidden="true">
      <rect width="100" height="100" rx="22" fill="#FDFBF8" />
      <g stroke="#17332D" strokeWidth="3.6" strokeLinecap="round" strokeLinejoin="round">
        <path d="M30 43v-7a6.5 6.5 0 0 1 6.5-6.5H42.5" />
        <path d="M57.5 29.5H63.5a6.5 6.5 0 0 1 6.5 6.5v6.5" />
        <path d="M30 58v6a6.5 6.5 0 0 0 6.5 6.5H42.5" />
        <circle cx="50" cy="50" r="8.6" />
        <path d="M56.3 70.6c1.6-3.4 4.4-5.9 7.2-5.9 2.6 0 2.4 3 4.6 3 1.2 0 2.4-.7 3.1-1.3" />
      </g>
    </svg>
  );
}

export function Logo({ className }: { className?: string }) {
  return (
    <span className={className} style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
      <LogoMark size={30} />
      <span style={{ fontWeight: 700, fontSize: 19, letterSpacing: "-0.02em" }}>찰캌</span>
    </span>
  );
}
