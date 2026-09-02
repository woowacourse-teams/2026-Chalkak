import type { TopicStatus } from "@/shared/api/contracts";
import type { AdminTopicFilters, AdminTopicSort } from "../api/topic-api";
import { withQueryPatch } from "@/features/posts/model/post-filter-query";
const phases=new Set<TopicStatus>(["BEFORE_OPEN","OPEN","CLOSED"]);
const sorts=new Set<AdminTopicSort>(["topicDateDesc","topicDateAsc","createdAtDesc","createdAtAsc"]);
function positive(value:string|null,fallback:number){const parsed=Number(value);return Number.isInteger(parsed)&&parsed>0?parsed:fallback;}
export function readAdminTopicFilters(params:Pick<URLSearchParams,"get">):AdminTopicFilters{const phase=params.get("phase") as TopicStatus|null;const sort=params.get("sort") as AdminTopicSort|null;return{phase:phase&&phases.has(phase)?phase:undefined,dateFrom:params.get("dateFrom")||undefined,dateTo:params.get("dateTo")||undefined,sort:sort&&sorts.has(sort)?sort:"topicDateDesc",page:positive(params.get("page"),1),pageSize:Math.min(100,positive(params.get("pageSize"),20))};}
export {withQueryPatch};
