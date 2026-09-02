type QueryScalar = string | number | boolean | Date;
export type QueryValue =
  | QueryScalar
  | readonly QueryScalar[]
  | null
  | undefined;

function normalizeQueryValue(value: QueryScalar) {
  return value instanceof Date ? value.toISOString() : String(value);
}

export function serializeQuery(
  query: Readonly<Record<string, QueryValue>>,
): string {
  const params = new URLSearchParams();

  for (const key of Object.keys(query).sort()) {
    const value = query[key];
    if (value === undefined || value === null || value === "") {
      continue;
    }

    const values = Array.isArray(value) ? value : [value];
    for (const item of values) {
      if (item !== "") {
        params.append(key, normalizeQueryValue(item));
      }
    }
  }

  const serialized = params.toString();
  return serialized ? "?" + serialized : "";
}
