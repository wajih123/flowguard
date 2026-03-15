export type UserType = 'INDIVIDUAL' | 'FREELANCE' | 'SME' | 'TPE' | 'PME'

export type Role = 'ROLE_USER' | 'ROLE_BUSINESS' | 'ROLE_ADMIN' | 'ROLE_SUPER_ADMIN'
export type KycStatus = 'PENDING' | 'VERIFIED' | 'REJECTED'
export type Plan = 'FREE' | 'PRO' | 'SCALE'

export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  role: Role
  kycStatus: KycStatus
  plan: Plan
  userType?: UserType
  createdAt?: number
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  user: User
}

export interface RegisterDto {
  firstName: string
  lastName: string
  email: string
  password: string
  userType?: UserType
}

export interface RegisterBusinessDto {
  firstName: string
  lastName: string
  email: string
  password: string
  companyName: string
  siret: string
  sector: string
  employeeCount: string
  plan: 'PRO' | 'SCALE'
}
