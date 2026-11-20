import { JevDetail } from "@/features/jev/detail";
export default async function Page({ params }: { params: Promise<{ jobId: string }> }) {
  const { jobId } = await params;
  return <JevDetail jobId={jobId} />;
}
