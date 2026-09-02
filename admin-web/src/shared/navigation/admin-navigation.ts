import type { AdminIconName } from "@/shared/ui/admin-icon";

export interface AdminNavigationItem {
  href: string;
  label: string;
  icon: AdminIconName;
}

export const adminNavigation: AdminNavigationItem[] = [
  { href: "/posts", label: "게시물", icon: "image" },
  { href: "/users", label: "사용자", icon: "users" },
  { href: "/topics", label: "주제", icon: "topic" },
  { href: "/audit-logs", label: "처리 이력", icon: "shield" },
];

export function getCurrentNavigation(pathname: string): AdminNavigationItem {
  if (pathname === "/pushes" || pathname.startsWith("/pushes/")) {
    return { href: "/pushes", label: "알림", icon: "clock" };
  }
  return adminNavigation.find((item) =>
    pathname === item.href || pathname.startsWith(`${item.href}/`),
  ) ?? adminNavigation[0];
}
