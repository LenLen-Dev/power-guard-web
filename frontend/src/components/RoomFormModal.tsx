import { useEffect, useState } from "react";
import { BuildingCombobox } from "./BuildingCombobox";
import { BUILDING_OPTIONS, buildRoomName } from "../lib/buildings";
import type { RoomFormValues } from "../lib/types";

interface RoomFormModalProps {
  open: boolean;
  loading?: boolean;
  onClose: () => void;
  onSubmit: (values: RoomFormValues) => Promise<void>;
}

interface FormErrors {
  [key: string]: string;
}

const INITIAL_FORM: RoomFormValues = {
  buildingId: "",
  buildingName: "",
  roomId: "",
  roomName: "",
  alertEmail: "",
  threshold: ""
};

export function RoomFormModal({ open, loading, onClose, onSubmit }: RoomFormModalProps) {
  const [form, setForm] = useState<RoomFormValues>(INITIAL_FORM);
  const [errors, setErrors] = useState<FormErrors>({});

  useEffect(() => {
    if (open) {
      setForm(INITIAL_FORM);
      setErrors({});
    }
  }, [open]);

  if (!open) {
    return null;
  }

  const validate = () => {
    const nextErrors: FormErrors = {};
    if (!form.buildingId.trim()) nextErrors.buildingId = "请选择楼栋";
    if (!form.roomId.trim()) nextErrors.roomId = "请输入房间号";
    if (!form.roomName.trim()) nextErrors.roomName = "房间名称生成失败，请检查楼栋和房间号";
    if (!form.threshold.trim() || Number(form.threshold) < 0) nextErrors.threshold = "请输入大于等于 0 的告警阈值";
    if (form.alertEmail.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.alertEmail.trim())) {
      nextErrors.alertEmail = "请输入有效的邮箱地址";
    }
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const updateField = (key: keyof RoomFormValues, value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: "" }));
  };

  const updateBuilding = (buildingId: string, buildingName: string) => {
    setForm((current) => ({
      ...current,
      buildingId,
      buildingName,
      roomName: buildRoomName(buildingName, current.roomId)
    }));
    setErrors((current) => ({ ...current, buildingId: "", roomName: "" }));
  };

  const updateRoomId = (roomId: string) => {
    setForm((current) => ({
      ...current,
      roomId,
      roomName: buildRoomName(current.buildingName, roomId)
    }));
    setErrors((current) => ({ ...current, roomId: "", roomName: "" }));
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!validate()) {
      return;
    }
    await onSubmit(form);
  };

  const renderField = (
    key: keyof RoomFormValues,
    label: string,
    placeholder: string,
    type: "text" | "email" | "number" = "text"
  ) => (
    <label>
      <span className="field-label">{label}</span>
      <input
        type={type}
        className="field"
        placeholder={placeholder}
        value={form[key]}
        onChange={(event) => updateField(key, event.target.value)}
      />
      {errors[key] ? <span className="mt-2 block text-sm text-rose-500">{errors[key]}</span> : null}
    </label>
  );

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/30 p-3 backdrop-blur-sm [-webkit-overflow-scrolling:touch] sm:p-4">
      <div className="flex min-h-full items-start justify-center sm:items-center">
        <div className="my-3 max-h-[calc(100dvh-1.5rem)] w-full max-w-3xl overflow-y-auto rounded-[30px] border border-slate-200 bg-white p-5 shadow-[0_35px_90px_rgba(15,23,42,0.18)] [-webkit-overflow-scrolling:touch] sm:my-4 sm:max-h-[calc(100dvh-2rem)] sm:p-6">
        <div className="mb-6 flex items-start justify-between gap-4">
          <div>
            <div className="mb-3 inline-flex rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-500">
              新增房间
            </div>
            <h3 className="text-2xl font-semibold text-slate-900">录入新的宿舍房间</h3>
            
          </div>
          <button type="button" className="secondary-btn" onClick={onClose}>
            关闭
          </button>
        </div>

        <form className="space-y-6" onSubmit={handleSubmit}>
          <div className="grid gap-4 md:grid-cols-2">
            <div className="md:col-span-2">
              <BuildingCombobox
                label="选择楼栋"
                options={BUILDING_OPTIONS}
                valueId={form.buildingId}
                valueName={form.buildingName}
                onChange={(option) => updateBuilding(option.id, option.name)}
                error={errors.buildingId}
              />
            </div>
            <label>
              <span className="field-label">房间号（roomId）</span>
              <input
                type="text"
                className="field"
                placeholder="例如：215"
                value={form.roomId}
                onChange={(event) => updateRoomId(event.target.value)}
              />
              {errors.roomId ? <span className="mt-2 block text-sm text-rose-500">{errors.roomId}</span> : null}
            </label>
            <label>
              <span className="field-label">房间名称（自动生成）</span>
              <input type="text" className="field bg-slate-50" value={form.roomName} readOnly placeholder="选择楼栋并输入房间号后自动生成" />
              {errors.roomName ? <span className="mt-2 block text-sm text-rose-500">{errors.roomName}</span> : null}
            </label>
            {renderField("threshold", "告警阈值 (kWh)", "例如：15", "number")}
            {renderField("alertEmail", "告警邮箱", "example@qq.com", "email")}
          </div>

          <div className="rounded-[24px] border border-slate-200 bg-slate-50 p-5">
            <div className="mb-4">
              <h4 className="text-sm font-semibold uppercase tracking-[0.2em] text-slate-600">当前提交信息</h4>
            </div>
            <div className="grid gap-4 md:grid-cols-3">
              <div className="rounded-2xl border border-slate-200 bg-white p-4">
                <p className="text-xs uppercase tracking-[0.18em] text-slate-500">楼栋 ID</p>
                <p className="mt-2 text-lg font-semibold text-slate-900">{form.buildingId || "--"}</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4">
                <p className="text-xs uppercase tracking-[0.18em] text-slate-500">楼栋名称</p>
                <p className="mt-2 text-lg font-semibold text-slate-900">{form.buildingName || "--"}</p>
              </div>
              <div className="rounded-2xl border border-slate-200 bg-white p-4">
                <p className="text-xs uppercase tracking-[0.18em] text-slate-500">房间名称</p>
                <p className="mt-2 text-lg font-semibold text-slate-900">{form.roomName || "--"}</p>
              </div>
            </div>
          </div>

          <div className="flex justify-end gap-3">
            <button type="button" className="secondary-btn" onClick={onClose} disabled={loading}>
              取消
            </button>
            <button type="submit" className="primary-btn" disabled={loading}>
              {loading ? "创建中..." : "创建房间"}
            </button>
          </div>
        </form>
      </div>
      </div>
    </div>
  );
}
