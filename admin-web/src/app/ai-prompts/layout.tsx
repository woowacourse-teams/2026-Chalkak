import type { ReactNode } from "react";
import { AiTabs } from "@/features/ai-prompts/ui/ai-tabs";

export default function AiPromptsLayout({ children }: { children: ReactNode }) {
  return <><AiTabs />{children}</>;
}
