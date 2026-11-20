import type { ReactNode } from "react";
import { JevShell } from "@/features/jev/ui";
export default function Layout({ children }: { children: ReactNode }) { return <JevShell>{children}</JevShell>; }
