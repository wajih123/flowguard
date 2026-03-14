import axios from 'axios'

const SWAN_BASE_URL = 'https://api.swan.io/partner/graphql'

const swanClient = axios.create({
  baseURL: SWAN_BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

const getSwanToken = async (): Promise<string> => {
  const clientId = process.env.EXPO_PUBLIC_SWAN_CLIENT_ID ?? 'demo_id'
  const clientSecret = process.env.EXPO_PUBLIC_SWAN_CLIENT_SECRET ?? 'demo_secret'

  const { data } = await axios.post('https://oauth.swan.io/oauth2/token', {
    grant_type: 'client_credentials',
    client_id: clientId,
    client_secret: clientSecret,
  })

  return data.access_token as string
}

const graphqlRequest = async <T>(query: string, variables?: Record<string, unknown>): Promise<T> => {
  const token = await getSwanToken()
  const { data } = await swanClient.post(
    '',
    { query, variables },
    { headers: { Authorization: `Bearer ${token}` } },
  )

  if (data.errors && data.errors.length > 0) {
    throw new Error(data.errors[0].message)
  }

  return data.data as T
}

export interface SwanOnboardingResult {
  onboardingUrl: string
}

export const createOnboarding = async (
  email: string,
  redirectUrl: string,
): Promise<SwanOnboardingResult> => {
  const query = `
    mutation CreateOnboarding($input: CreateIndividualOnboardingInput!) {
      createIndividualOnboarding(input: $input) {
        ... on CreateIndividualOnboardingSuccessPayload {
          onboarding {
            id
            onboardingUrl
          }
        }
        ... on ValidationRejection {
          message
        }
      }
    }
  `

  const variables = {
    input: {
      email,
      redirectUrl,
      language: 'fr',
    },
  }

  const result = await graphqlRequest<{
    createIndividualOnboarding: {
      onboarding?: { id: string; onboardingUrl: string }
      message?: string
    }
  }>(query, variables)

  const onboarding = result.createIndividualOnboarding.onboarding
  if (!onboarding) {
    throw new Error(result.createIndividualOnboarding.message ?? "Échec de l'onboarding Swan")
  }

  return { onboardingUrl: onboarding.onboardingUrl }
}

export interface SwanAccountData {
  id: string
  iban: string
  balance: { value: string; currency: string }
  holder: { firstName: string; lastName: string }
}

export const getAccount = async (): Promise<SwanAccountData> => {
  const query = `
    query GetAccountMemberships {
      accountMemberships(first: 1, filters: { status: Enabled }) {
        edges {
          node {
            account {
              id
              IBAN
              balances {
                available {
                  value
                  currency
                }
              }
              holder {
                info {
                  ... on AccountHolderIndividualInfo {
                    firstName
                    lastName
                  }
                }
              }
            }
          }
        }
      }
    }
  `

  const result = await graphqlRequest<{
    accountMemberships: {
      edges: Array<{
        node: {
          account: {
            id: string
            IBAN: string
            balances: { available: { value: string; currency: string } }
            holder: { info: { firstName: string; lastName: string } }
          }
        }
      }>
    }
  }>(query)

  const edge = result.accountMemberships.edges[0]
  if (!edge) {
    throw new Error('Aucun compte Swan trouvé')
  }

  const account = edge.node.account
  return {
    id: account.id,
    iban: account.IBAN,
    balance: account.balances.available,
    holder: account.holder.info,
  }
}

export const getStatement = async (): Promise<{ iban: string; balance: string }> => {
  const query = `
    query GetStatement {
      accountMemberships(first: 1, filters: { status: Enabled }) {
        edges {
          node {
            account {
              IBAN
              balances {
                available {
                  value
                }
              }
            }
          }
        }
      }
    }
  `

  const result = await graphqlRequest<{
    accountMemberships: {
      edges: Array<{
        node: {
          account: {
            IBAN: string
            balances: { available: { value: string } }
          }
        }
      }>
    }
  }>(query)

  const edge = result.accountMemberships.edges[0]
  if (!edge) {
    throw new Error('Aucun compte Swan trouvé')
  }

  return {
    iban: edge.node.account.IBAN,
    balance: edge.node.account.balances.available.value,
  }
}
