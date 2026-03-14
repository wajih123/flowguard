import { useQuery } from "@tanstack/react-query";
import { kpisApi } from "@/api/kpis";

export const useKpis = () =>
  useQuery({
    queryKey: ["kpis"],
    queryFn: kpisApi.get,
    staleTime: 30 * 60 * 1000, // 30 min
  });
