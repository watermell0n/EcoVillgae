import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type PropsWithChildren } from "react";

import { ApiError } from "@/api/client";

export function QueryProvider({ children }: PropsWithChildren) {
  const [client] = useState(() => new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: (failureCount, error) => error instanceof ApiError && error.retryable && failureCount < 2,
        refetchOnReconnect: true
      },
      mutations: { retry: false }
    }
  }));

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
