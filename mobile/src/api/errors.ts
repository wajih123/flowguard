export type ErrorCode =
  | 'UNAUTHORIZED'
  | 'NETWORK'
  | 'SERVER'
  | 'NOT_FOUND'
  | 'VALIDATION'
  | 'BIOMETRIC_FAILED'
  | 'UNKNOWN'

export class FlowGuardError extends Error {
  constructor(
    public code: ErrorCode,
    message: string,
    public fields?: Record<string, string>,
    public statusCode?: number,
  ) {
    super(message)
    this.name = 'FlowGuardError'
  }
}
