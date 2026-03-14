import { useMutation } from '@tanstack/react-query'
import * as flowguardApi from '../api/flowguardApi'
import { Biometric } from '../native'
import { FlowGuardError } from '../api/errors'
import type { FlashCreditRequest, FlashCreditResponse } from '../domain/FlashCredit'

export const useFlashCredit = () => {
  const mutation = useMutation<FlashCreditResponse, Error, FlashCreditRequest>({
    mutationFn: async (req: FlashCreditRequest) => {
      const ok = await Biometric.authenticate()
      if (!ok) {
        throw new FlowGuardError('BIOMETRIC_FAILED', 'Authentification biométrique requise')
      }
      return flowguardApi.requestFlashCredit(req)
    },
  })

  return {
    requestCredit: mutation.mutate,
    isLoading: mutation.isPending,
    isSuccess: mutation.isSuccess,
    data: mutation.data,
    error: mutation.error,
  }
}
