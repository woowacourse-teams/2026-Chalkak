import { AiPromptDetailScreen } from "@/features/ai-prompts/ui/ai-prompt-detail-screen";

export default async function AiPromptDetailPage({ params }: { params: Promise<{ promptVersionId: string }> }) {
  const { promptVersionId } = await params;
  return <AiPromptDetailScreen promptVersionId={promptVersionId} />;
}
