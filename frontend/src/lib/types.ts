export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface RoomStatus {
  id: number;
  buildingId: string;
  buildingName: string;
  roomId: string;
  roomName: string;
  alertEmail?: string | null;
  threshold: number;
  total?: number | null;
  remain?: number | null;
  status: number;
  statusDesc: string;
  lowThreshold: boolean;
  updateTime?: string | null;
}

export interface DailyTrend {
  date: string;
  startRemain?: number | null;
  endRemain?: number | null;
  consumption?: number | null;
  rechargeDetected: boolean;
  dataComplete: boolean;
}

export type RefreshJobSource = "MANUAL" | "SCHEDULED";

export type RefreshJobStatus = "QUEUED" | "RUNNING" | "SUCCESS" | "PARTIAL_SUCCESS" | "FAILED";

export interface RefreshJob {
  jobId: string;
  source: RefreshJobSource;
  status: RefreshJobStatus;
  totalRooms: number;
  completedRooms: number;
  successRooms: number;
  failedRooms: number;
  queuedAt: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  message: string;
}

export interface CreateRoomPayload {
  buildingId: string;
  buildingName: string;
  roomId: string;
  roomName: string;
  alertEmail?: string;
  threshold: number;
}

export interface UpdateRoomPayload {
  buildingId: string;
  buildingName: string;
  roomId: string;
  roomName: string;
  alertEmail?: string;
  threshold: number;
}

export interface NoticeState {
  type: "success" | "warning" | "error";
  message: string;
}

export interface RoomFormValues {
  buildingId: string;
  buildingName: string;
  roomId: string;
  roomName: string;
  alertEmail: string;
  threshold: string;
}

export interface RoomEditorValues {
  buildingId: string;
  buildingName: string;
  roomId: string;
  roomName: string;
  alertEmail: string;
  threshold: string;
}
