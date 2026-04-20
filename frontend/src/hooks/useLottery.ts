import { useEffect, useState } from "react";
import { lotteryApi } from "../lib/api";
import type { LotteryDraw } from "../lib/types";

const LOTTERY_REFRESH_INTERVAL_MS = 60 * 1000;

export function useLottery() {
  const [latestDraw, setLatestDraw] = useState<LotteryDraw | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const fetchLatestDraw = async (showLoading = false) => {
      if (showLoading) {
        setLoading(true);
      }
      try {
        const response = await lotteryApi.getLatest();
        if (!cancelled) {
          setLatestDraw(response);
          setError(null);
        }
      } catch (requestError) {
        if (!cancelled) {
          setError(requestError instanceof Error ? requestError.message : "加载开奖结果失败");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void fetchLatestDraw(true);
    const timer = window.setInterval(() => {
      void fetchLatestDraw(false);
    }, LOTTERY_REFRESH_INTERVAL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  return {
    latestDraw,
    loading,
    error
  };
}
