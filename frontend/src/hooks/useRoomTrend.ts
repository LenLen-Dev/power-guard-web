import { useEffect, useState } from "react";
import { roomApi } from "../lib/api";
import type { DailyTrend } from "../lib/types";

export function useRoomTrend(roomId: number | null, days = 7) {
  const [trend, setTrend] = useState<DailyTrend[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!roomId) {
      setTrend([]);
      setError(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    roomApi
      .trend(roomId, days)
      .then((data) => {
        if (!cancelled) {
          setTrend(data);
        }
      })
      .catch((requestError) => {
        if (!cancelled) {
          setError(requestError instanceof Error ? requestError.message : "加载趋势失败");
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [roomId, days]);

  return {
    trend,
    loading,
    error
  };
}
