import { http, HttpResponse } from "msw";
import type { Call, Decision, Evaluation, GoldInput, Metrics, Prompt, PromptSummary, RunDetail, RunRow } from "@/features/jev/api";
import { classificationExample, photoExample } from "./jev-fixtures";
import { postDetailFixtures, postIds } from "./fixtures";

const base = "*/api/v1/admin";
const runPath = base + "/jev-moderation-runs";
const promptsPath = base + "/jev-prompt-versions";
const previewPath = base + "/jev-previews";
const model = "jev-1.14.0";
const now = (minutes = 0) => new Date(Date.now() - minutes * 60_000).toISOString();

const classificationPrompt: Prompt = {
  id: "2bfb8ca2-d063-4127-a41e-99b999bdf19a", role: "JEV_TOPIC_CLASSIFICATION", model, version: 2, active: true,
  memo: "주제 분류 기준", createdAt: now(60), configuration: classificationExample,
};
const photoPrompt: Prompt = {
  id: "99c656f6-b65e-4d74-83d3-969fa5c500b7", role: "JEV_PHOTO_MODERATION", model, version: 4, active: true,
  memo: "사진 검수 기준", createdAt: now(60), configuration: photoExample,
};

function evaluation(decision: Decision): Evaluation {
  function answer(result: Decision) {
    const values = result === "APPROVE" ? [0.95, 0.04, 0.01] : result === "REJECT" ? [0.06, 0.10, 0.84] : [0.33, 0.55, 0.12];
    return { choice: result.toLowerCase(), confidence: Math.max(...values), probabilities: { approve: values[0], human_review: values[1], reject: values[2] } };
  }
  return {
    answers: { topic_fit: answer(decision), image_safety: answer("APPROVE"), image_expression: answer("APPROVE") },
    decisions: { topic_fit: decision, image_safety: "APPROVE", image_expression: "APPROVE" },
    recommendation: decision, error: null,
  };
}

function call(result: unknown, latencyMs: number, tokens: number): Call {
  return { callId: crypto.randomUUID(), startedAt: now(15), receivedAt: now(14), httpStatus: 200, latencyMs,
    inputTokens: tokens, outputTokens: 42, errorCode: null, rawResponse: JSON.stringify(result, null, 2) };
}

function fixture(jobId: string, postId: string, topic: string, decision: Decision, minutes: number, preview = false): RunDetail {
  const post = postDetailFixtures[postId];
  const answer = evaluation(decision);
  const classification = { answers: { topic_category: { choice: "subject", confidence: 0.88, probabilities: { subject: 0.88, general: 0.12 } } }, decisions: {}, recommendation: null, error: null };
  return {
    result: {
      jobId, postId, topic, topicId: post.topic?.topicId ?? "bcbcbcbc-bcbc-4bcb-8bcb-bcbcbcbcbcbc", preview, classificationId: crypto.randomUUID(), category: "subject",
      classificationCalls: { calls: [call(classification, 486, 275)] }, classificationEvaluation: classification,
      photoCalls: { calls: [call(answer, 791, 1335)] }, evaluation: answer, dispatchAttempts: 1,
      dispatchError: null, createdAt: now(minutes), finishedAt: now(minutes - 1),
    },
    model, classificationPromptId: preview ? null : classificationPrompt.id, photoPromptId: preview ? null : photoPrompt.id,
    disposition: preview ? null : decision === "APPROVE" ? "AUTO_APPROVED" : "ADMIN_REVIEW",
    applicationReason: null, appliedAt: preview ? null : now(minutes - 1), postStatus: post.moderationStatus,
    postDeletedAt: post.deletedAt, originalImageUrl: post.photo?.originalImageUrl ?? null,
    slackDelivery: !preview && decision !== "APPROVE" ? {
      status: decision === "HUMAN_REVIEW" ? "SENT" : "FAILED", attempts: decision === "HUMAN_REVIEW" ? 1 : 3,
      availableAt: null, history: [{ token: crypto.randomUUID(), startedAt: now(minutes - 1),
        error: decision === "REJECT" ? "SLACK_NETWORK_ERROR" : null, httpStatus: decision === "REJECT" ? null : 200 }],
    } : null,
    goldLabel: !preview && decision === "APPROVE" ? { decision, questions: { topic_fit: decision }, reason: "사진의 중심에 주제를 확인할 수 있어요.", sampling: "RANDOM", evaluatedAt: now(12), evaluatedBy: "demo-admin" } : null,
    rejectionSuggestions: decision === "REJECT" ? { topic_fit: "사진과 오늘의 주제 사이의 연결을 확인하기 어려워요." } : {},
  };
}

export const sampleJevJobId = "a61f6106-b7dc-45c6-b08f-c746d5dffb02";
let records: RunDetail[];
let prompts: Prompt[];

export function resetJevMocks() {
  records = [
    fixture(sampleJevJobId, postIds.pending, "오늘의 빛", "HUMAN_REVIEW", 13),
    fixture("24210431-0e3c-4784-8683-1cb647061b21", postIds.approved, "초록", "APPROVE", 32),
    fixture("24210431-0e3c-4784-8683-1cb647061b22", postIds.rejected, "조명", "REJECT", 58),
    fixture("24210431-0e3c-4784-8683-1cb647061b23", postIds.approved, "초록 · 초안 시험", "APPROVE", 80, true),
  ];
  prompts = structuredClone([classificationPrompt, photoPrompt,
    { ...photoPrompt, id: "62409009-b910-43e2-b1f1-2f98648bceb3", version: 3, active: false, memo: "이전 사진 검수 기준" }]);
}
resetJevMocks();

function rows(): RunRow[] {
  return records.map(({ result, disposition, postStatus, goldLabel, slackDelivery }) => ({
    jobId: result.jobId, postId: result.postId, topicId: result.topicId, topic: result.topic, preview: result.preview,
    disposition, recommendation: result.evaluation?.recommendation ?? null, postStatus, goldDecision: goldLabel?.decision ?? null,
    slackStatus: slackDelivery?.status ?? null, createdAt: result.createdAt, finishedAt: result.finishedAt,
  }));
}

function notFound() {
  return HttpResponse.json({ errorCode: "NOT_FOUND", message: "검수 정보가 없어요." }, { status: 404 });
}

const agreement = (rated: number, matched: number, falseApprovals = 0, falseRejections = 0, missingPredictions = 0) => ({
  rated, matched, falseApprovals, falseRejections, missingPredictions, matchPercent: rated ? 100 * matched / rated : null,
});

function calculateMockMetrics(url: URL): Metrics {
  const preview = url.searchParams.get("preview") === "true";
  const from = Date.parse(url.searchParams.get("from") ?? "");
  const to = Date.parse(url.searchParams.get("to") ?? "");
  const items = records.filter((entry) => {
    const createdAt = Date.parse(entry.result.createdAt);
    return entry.result.preview === preview && createdAt >= from && createdAt < to;
  });
  const rated = items.filter((row) => row.goldLabel);
  const final = (group: RunDetail[]) => agreement(group.length, group.filter((entry) => entry.result.evaluation?.recommendation === entry.goldLabel?.decision).length,
    group.filter((entry) => entry.result.evaluation?.recommendation === "APPROVE" && entry.goldLabel?.decision !== "APPROVE").length,
    group.filter((entry) => entry.result.evaluation?.recommendation === "REJECT" && entry.goldLabel?.decision !== "REJECT").length);
  const calls = Array.from(new Map(items.flatMap(({result}) => [...(result.classificationCalls?.calls ?? []), ...(result.photoCalls?.calls ?? [])])
    .map((entry) => [entry.callId, entry])).values());
  return {
    runs: items.length, completed: items.filter((entry) => entry.result.finishedAt).length, autoApproved: items.filter((entry) => entry.disposition === "AUTO_APPROVED").length,
    adminReview: items.filter((entry) => entry.disposition === "ADMIN_REVIEW").length, skipped: 0, technicalErrors: 0, truncated: false,
    finalAgreement: final(rated), bySampling: { RANDOM: final(rated.filter((entry) => entry.goldLabel?.sampling === "RANDOM")),
      ERROR_ANALYSIS: final(rated.filter((entry) => entry.goldLabel?.sampling === "ERROR_ANALYSIS")),
      UNSPECIFIED: final(rated.filter((entry) => entry.goldLabel?.sampling === "UNSPECIFIED")) },
    questions: Object.fromEntries(["topic_fit", "image_safety", "image_expression"].map((key) => {
      const group = rated.filter((entry) => entry.goldLabel?.questions[key]);
      return [key, agreement(group.length, group.filter((entry) => entry.goldLabel?.questions[key] === entry.result.evaluation?.decisions[key]).length)];
    })),
    ratedAutoApprovals: rated.filter((entry) => entry.disposition === "AUTO_APPROVED").length,
    falseAutoApprovals: rated.filter((entry) => entry.disposition === "AUTO_APPROVED" && entry.goldLabel?.decision !== "APPROVE").length,
    repeatedGroups: 0, stableGroups: 0, consistencyPercent: null,
    usage: { reservedAttempts: calls.length, observedHttpCalls: calls.length, timedHttpCalls: calls.length,
      httpP99Ms: calls.length ? Math.max(...calls.map((entry) => entry.latencyMs ?? 0)) : null,
      knownInputTokens: calls.reduce((sum, entry) => sum + (entry.inputTokens ?? 0), 0),
      knownOutputTokens: calls.reduce((sum, entry) => sum + (entry.outputTokens ?? 0), 0), unknownInputUsageCalls: 0, unknownOutputUsageCalls: 0 },
  };
}

export const jevHandlers = [
  http.get(runPath, ({ request }) => {
    const params = new URL(request.url).searchParams;
    const selected = rows().filter((entry) =>
      (!params.has("preview") || String(entry.preview) === params.get("preview")) &&
      (!params.has("disposition") || entry.disposition === params.get("disposition")) &&
      (!params.has("postId") || entry.postId === params.get("postId")));
    const page = Number(params.get("page") ?? 0);
    const size = Number(params.get("pageSize") ?? 20);
    return HttpResponse.json({ rows: selected.slice(page * size, (page + 1) * size), hasNext: selected.length > (page + 1) * size });
  }),
  http.get(runPath + "/metrics", ({ request }) => HttpResponse.json(calculateMockMetrics(new URL(request.url)))),
  http.get(runPath + "/:id", ({ params }) => {
    const record = records.find((row) => row.result.jobId === params.id);
    return record ? HttpResponse.json(record) : notFound();
  }),
  http.put(runPath + "/:id/evaluation", async ({ request, params }) => {
    const record = records.find((row) => row.result.jobId === params.id);
    if (!record) return notFound();
    const submitted = await request.json() as GoldInput;
    record.goldLabel = { ...submitted, evaluatedAt: now(), evaluatedBy: "demo-admin" };
    return HttpResponse.json(record.goldLabel);
  }),
  http.get(promptsPath, ({request}) => {
    const params = new URL(request.url).searchParams;
    const page = Number(params.get("page") ?? 1);
    const size = Number(params.get("pageSize") ?? 20);
    const selected = prompts.filter((prompt) => prompt.role === params.get("role")).sort((a, b) => b.version - a.version);
    const promptVersions: PromptSummary[] = selected.slice((page - 1) * size, page * size).map(({ id, role, version, model, memo, active, createdAt }) => ({ id, role, version, model, memo, active, createdAt }));
    return HttpResponse.json({ promptVersions, hasNext: selected.length > page * size, currentPage: page, pageSize: size });
  }),
  http.get(promptsPath + "/:id", ({ params }) => {
    const prompt = prompts.find((entry) => entry.id === params.id);
    return prompt ? HttpResponse.json(prompt) : notFound();
  }),
  http.post(promptsPath, async ({ request }) => {
    const body = await request.json() as Pick<Prompt, "role" | "model" | "configuration" | "memo">;
    const version = Math.max(0, ...prompts.filter((entry) => entry.role === body.role).map((entry) => entry.version)) + 1;
    const value = { ...body, version, active: false, id: crypto.randomUUID(), createdAt: now() };
    prompts.push(value);
    return HttpResponse.json(value, { status: 201 });
  }),
  http.put(promptsPath + "/:id/activation", async ({ request, params }) => {
    const prompt = prompts.find((entry) => entry.id === params.id);
    if (!prompt) return notFound();
    const { active } = await request.json() as { active: boolean };
    if (active) prompts.filter((entry) => entry.role === prompt.role).forEach((entry) => { entry.active = false; });
    prompt.active = active;
    return HttpResponse.json(prompt);
  }),
  http.post(previewPath, async ({ request }) => {
    const body = await request.json() as { postId: string; topicId: string };
    if (!postDetailFixtures[body.postId]) return notFound();
    const record = fixture(crypto.randomUUID(), body.postId, "초안 시험", "APPROVE", 1, true);
    record.result.topicId = body.topicId;
    records.unshift(record);
    return HttpResponse.json(record.result, { status: 202 });
  }),
  http.get(previewPath + "/:id", ({ params }) => {
    const record = records.find((entry) => entry.result.jobId === params.id && entry.result.preview);
    return record ? HttpResponse.json(record.result) : notFound();
  }),
];

export function updateMockJevPost(postId: string, changes: Partial<Pick<RunDetail, "postStatus" | "postDeletedAt" | "originalImageUrl">>) {
  records = records.map((entry) => entry.result.postId === postId ? { ...entry, ...changes } : entry);
}
