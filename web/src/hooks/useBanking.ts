import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { bankingApi, type BankAccount } from "@/api/banking";
import { useNavigate } from "react-router-dom";

export const useBankAccounts = () => {
  return useQuery<BankAccount[]>({
    queryKey: ["banking", "accounts"],
    queryFn: bankingApi.getAccounts,
    staleTime: 1000 * 60 * 5,
  });
};

export const useStartConnect = () => {
  return useMutation({
    mutationFn: bankingApi.startConnect,
    onSuccess: (data) => {
      // Sauvegarder le state pour vérification au callback
      sessionStorage.setItem("bridge_context", data.state);
      // Rediriger vers Bridge
      window.location.href = data.connect_url;
    },
  });
};

export const useHandleCallback = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: bankingApi.handleCallback,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["banking", "accounts"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      sessionStorage.removeItem("bridge_context");
    },
  });
};

export const useSyncAccounts = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: bankingApi.sync,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["banking", "accounts"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
  });
};
