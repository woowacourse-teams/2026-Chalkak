import type { ReactNode } from "react";
import { NotificationTabs } from "@/features/notices/ui/notification-tabs";

export default function NoticesLayout({ children }: { children: ReactNode }) {
  return <><NotificationTabs />{children}</>;
}
