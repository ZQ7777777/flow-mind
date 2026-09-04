import type { GenerationContextCapability } from "@flowmind/agent-contracts";

export const LOCAL_EMBEDDING_MODEL = "LOCAL_SEMANTIC_HASH_V1" as const;
export const LOCAL_EMBEDDING_DIMENSIONS = 256 as const;
export const LOCAL_CHUNKER_VERSION = "CODEPOINT_800_OVERLAP_100_V1" as const;
export const LOCAL_INDEX_VERSION = "LOCAL_RAG_INDEX_V1" as const;

const CONCEPTS: Array<[string, RegExp]> = [
  ["concept_form", /表单|申请|录入|填写|form|application/giu],
  ["concept_validation", /校验|验证|必填|格式|范围|validation|required/giu],
  ["concept_multiselect", /多选|多个选项|批量选择|multi[\s_-]?select/giu],
  ["concept_reference", /参考数据|基础数据|字典|下拉选项|联动|带出|reference|lookup|option/giu],
  ["concept_cascade", /级联|依赖选择|上下游参数|cascade|dependent/giu],
  ["concept_query", /查询|检索|资金|余额|权益|query|fetch|funds/giu],
  ["concept_calculation", /计算|公式|金额|合计|乘数|比例|calculation|formula|amount|decimal/giu],
  ["concept_check", /核查|业务规则|准入|条件判断|check|rule|constraint/giu],
  ["concept_readonly", /只读|不可编辑|自动带出|read[\s_-]?only/giu],
];

export function chunkForLocalEmbedding(value: string): string[] {
  const points = [...value];
  if (points.length <= 800) return [value];
  const chunks: string[] = [];
  for (let start = 0; start < points.length; start += 700) {
    chunks.push(points.slice(start, start + 800).join(""));
    if (start + 800 >= points.length) break;
  }
  return chunks;
}

export function embedLocal(value: string, capabilities: GenerationContextCapability[]): number[] {
  let normalized = value.toLowerCase();
  const features: string[] = capabilities.map((capability) => `capability_${capability.toLowerCase()}`);
  for (const [concept, pattern] of CONCEPTS) {
    const matches = normalized.match(pattern);
    if (matches?.length) features.push(...Array(matches.length).fill(concept));
    normalized = normalized.replace(pattern, ` ${concept} `);
  }
  features.push(...(normalized.match(/[a-z0-9_]+/g) || []));
  for (const run of normalized.match(/[\u3400-\u9fff]+/g) || []) {
    const points = [...run];
    features.push(...(points.length === 1 ? points : points.slice(0, -1).map((point, index) => `${point}${points[index + 1]}`)));
  }
  const vector = Array<number>(LOCAL_EMBEDDING_DIMENSIONS).fill(0);
  for (const feature of features) {
    const first = fnv1a(feature);
    const second = fnv1a(`sign:${feature}`);
    vector[first % LOCAL_EMBEDDING_DIMENSIONS] += (second & 1) === 0 ? 1 : -1;
  }
  const norm = Math.sqrt(vector.reduce((sum, valueAt) => sum + valueAt * valueAt, 0));
  return norm ? vector.map((valueAt) => Number((valueAt / norm).toFixed(8))) : vector;
}

export function cosineSimilarity(left: number[], right: number[]): number {
  if (left.length !== LOCAL_EMBEDDING_DIMENSIONS || right.length !== LOCAL_EMBEDDING_DIMENSIONS) return 0;
  const score = left.reduce((sum, value, index) => sum + value * right[index], 0);
  return Number(Math.max(0, Math.min(1, score)).toFixed(6));
}

function fnv1a(value: string): number {
  let hash = 0x811c9dc5;
  for (const character of value) {
    hash ^= character.codePointAt(0) || 0;
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}
