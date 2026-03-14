import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { flashCreditApi } from "@/api/flashCredit";
import type { FlashCreditRequest } from "@/domain/FlashCredit";

export const useFlashCredits = () =>
  useQuery({
    queryKey: ["flash-credits"],
    queryFn: flashCreditApi.list,
    staleTime: 5 * 60 * 1000,
  });

export const useRequestFlashCredit = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: FlashCreditRequest) => flashCreditApi.request(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["flash-credits"] }),
  });
};

export const useRetractCredit = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (creditId: string) => flashCreditApi.retract(creditId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["flash-credits"] }),
  });
};
