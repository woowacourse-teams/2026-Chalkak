import type { TopicStatus } from "@/shared/api/contracts";
import type { ApiError } from "@/shared/api/errors";
export const topicPhaseDisplay:Record<TopicStatus,{label:string;tone:"info"|"success"|"neutral"}>={BEFORE_OPEN:{label:"공개 전",tone:"info"},OPEN:{label:"참여 중",tone:"success"},CLOSED:{label:"종료",tone:"neutral"}};
export function getTopicErrorMessage(error:unknown){const value=error as Partial<ApiError>;if(value.status===404)return"주제를 찾을 수 없습니다.";if(value.status===403)return"관리자 API에 접근할 수 없습니다.";if(value.errorCode==="RESOURCE_STATE_CHANGED")return"공개가 시작됐거나 다른 관리자가 이미 변경한 주제입니다.";return value.message||"주제 요청을 처리하지 못했습니다.";}
export function instantToKstInput(value:string){return new Date(new Date(value).getTime()+9*60*60*1000).toISOString().slice(0,16);}
export function kstInputToInstant(value:string){return new Date(value+":00+09:00").toISOString();}
