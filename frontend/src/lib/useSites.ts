import { useQuery } from "@tanstack/react-query";
import { api } from "./apiClient";
import type { SiteResponse } from "../types/api";

export function useSites() {
  return useQuery({
    queryKey: ["sites"],
    queryFn: () => api.get<SiteResponse[]>("/api/admin/sites"),
  });
}
