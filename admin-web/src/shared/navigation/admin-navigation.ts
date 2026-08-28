export interface AdminNavigationItem {
  href: string;
  label: string;
  shortLabel: string;
  description: string;
}

export const adminNavigation = [
  {
    href: "/",
    label: "대시보드",
    shortLabel: "DB",
    description: "서비스 운영 현황",
  },
  {
    href: "/posts",
    label: "게시물",
    shortLabel: "PO",
    description: "게시물 검수와 처리",
  },
  {
    href: "/users",
    label: "사용자",
    shortLabel: "US",
    description: "사용자 상태 관리",
  },
  {
    href: "/topics",
    label: "주제",
    shortLabel: "TO",
    description: "오늘의 주제 관리",
  },
  {
    href: "/pushes",
    label: "푸시",
    shortLabel: "PU",
    description: "사용자 알림 발송",
  },
  {
    href: "/audit-logs",
    label: "감사 로그",
    shortLabel: "AL",
    description: "관리자 작업 이력",
  },
] satisfies AdminNavigationItem[];

export function getCurrentNavigation(pathname: string) {
  return (
    adminNavigation.find((item) =>
      item.href === "/" ? pathname === "/" : pathname.startsWith(item.href),
    ) ?? adminNavigation[0]
  );
}
