export type ApiErrorKind =
  | "configuration"
  | "api"
  | "network"
  | "timeout"
  | "invalid-response";

interface ApiErrorOptions {
  kind: ApiErrorKind;
  message: string;
  errorCode?: string;
  status?: number;
  cause?: unknown;
}

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly errorCode?: string;
  readonly status?: number;

  constructor(options: ApiErrorOptions) {
    super(options.message, { cause: options.cause });
    this.name = "ApiError";
    this.kind = options.kind;
    this.errorCode = options.errorCode;
    this.status = options.status;
  }
}
