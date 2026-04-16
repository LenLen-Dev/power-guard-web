import type { ApiResponse, CreateRoomPayload, DailyTrend, RoomStatus, UpdateRoomPayload } from "./types";

const API_BASE_URL = normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL ?? "");

export class ApiError extends Error {
  code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

function normalizeBaseUrl(value: string) {
  return value.trim().replace(/\/+$/, "");
}

function buildRequestTargets(path: string) {
  if (API_BASE_URL) {
    return [`${API_BASE_URL}${path}`];
  }

  const targets = [path];
  if (typeof window === "undefined") {
    targets.push(`http://localhost:8080${path}`);
    return targets;
  }

  const { protocol, hostname, port } = window.location;
  const backendProtocol = protocol === "https:" ? "https:" : "http:";
  const backendHost = hostname || "localhost";
  const backendOrigin = `${backendProtocol}//${backendHost}:8080`;
  const currentOrigin = window.location.origin;

  if (port !== "8080" && currentOrigin !== backendOrigin) {
    targets.push(`${backendOrigin}${path}`);
  }

  return [...new Set(targets)];
}

function buildNetworkErrorMessage(path: string, targets: string[]) {
  const readableTargets = targets.join(" 或 ");
  return `无法连接接口 ${path}。已尝试 ${readableTargets}，请确认前端开发服务器或后端服务是否正在运行。`;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const targets = buildRequestTargets(path);
  let networkError: unknown;

  for (const target of targets) {
    try {
      const response = await fetch(target, {
        headers: {
          "Content-Type": "application/json",
          ...(init?.headers ?? {})
        },
        ...init
      });

      if (!response.ok) {
        throw new ApiError(response.status, `请求失败 (${response.status})`);
      }

      const payload = (await response.json()) as ApiResponse<T>;
      if (payload.code !== 0 && payload.code !== 200) {
        throw new ApiError(payload.code, payload.message || "接口返回错误");
      }

      return payload.data;
    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }
      networkError = error;
    }
  }

  if (networkError instanceof Error) {
    throw new ApiError(0, buildNetworkErrorMessage(path, targets));
  }
  throw new ApiError(0, buildNetworkErrorMessage(path, targets));
}

export const roomApi = {
  listStatus: () => request<RoomStatus[]>("/api/rooms/status"),
  create: (body: CreateRoomPayload) =>
    request<RoomStatus>("/api/rooms", {
      method: "POST",
      body: JSON.stringify(body)
    }),
  manualRefresh: () =>
    request<RoomStatus[]>("/api/rooms/refresh", {
      method: "POST"
    }),
  update: (id: number, body: UpdateRoomPayload) =>
    request<RoomStatus>(`/api/rooms/${id}`, {
      method: "PUT",
      body: JSON.stringify(body)
    }),
  remove: (id: number) =>
    request<void>(`/api/rooms/${id}`, {
      method: "DELETE"
    }),
  trend: (id: number, days = 7) => request<DailyTrend[]>(`/api/rooms/${id}/trend?days=${days}`)
};
