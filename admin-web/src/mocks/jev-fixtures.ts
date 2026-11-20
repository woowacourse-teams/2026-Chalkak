import type { PromptConfig } from "@/features/jev/api";

const criteria = { approve: "기준을 충족한다.", human_review: "판단이 모호하다.", reject: "기준을 위반한다." };
export const classificationExample: PromptConfig = {
  questions: { topic_category: { type: "choice", instructions: "사진 주제의 종류를 고른다.", criteria: { general: "일반적인 주제", subject: "구체적인 피사체" } } },
  categoryRules: {},
  categoryInstructions: {},
};
export const photoExample: PromptConfig = {
  questions: {
    topic_fit: { type: "choice", instructions: "사진에서 주제와 연결되는 근거를 찾는다.", criteria },
    image_safety: { type: "choice", instructions: "사진의 안전성을 판정한다.", criteria },
    image_expression: { type: "choice", instructions: "사진에 보이는 글자와 표현을 판정한다.", criteria },
  },
  categoryRules: { general: "색과 구도, 사물로 연결을 확인한다.", subject: "사진에 보이는 피사체를 확인한다." },
  categoryInstructions: {},
};
