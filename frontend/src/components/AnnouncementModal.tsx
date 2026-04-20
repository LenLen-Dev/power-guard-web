import { CalendarDays, Gift, Mail, Sparkles, X } from "lucide-react";

interface AnnouncementModalProps {
  open: boolean;
  onClose: () => void;
  onDismissForWeek: () => void;
}

export function AnnouncementModal({ open, onClose, onDismissForWeek }: AnnouncementModalProps) {
  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/35 p-4 backdrop-blur-sm">
      <div className="w-full max-w-4xl overflow-hidden rounded-[32px] border border-sky-100 bg-white shadow-[0_35px_90px_rgba(15,23,42,0.2)]">
        <div className="bg-[radial-gradient(circle_at_top_left,_rgba(125,211,252,0.45),_transparent_42%),linear-gradient(135deg,_#eff6ff,_#ffffff_58%,_#ecfeff)] px-6 py-6 sm:px-8">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="inline-flex items-center gap-2 rounded-full border border-sky-200 bg-white/80 px-3 py-1 text-xs font-semibold uppercase tracking-[0.22em] text-sky-700">
                <Sparkles className="h-3.5 w-3.5" />
                小广播
              </div>
              <h3 className="mt-4 text-3xl font-black tracking-tight text-slate-900">提醒系统正在悄悄升级中</h3>
              <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-600 sm:text-[15px]">
                先和大家打个招呼：最近 QQ 邮箱偶尔会有点“小脾气”，少量提醒邮件可能会出现掉队的情况。
                我们已经在加速修复，争取让每一封提醒都稳稳送达。
              </p>
              <div className="mt-5 inline-flex max-w-2xl items-center gap-3 rounded-[22px] border border-rose-200 bg-rose-50/90 px-5 py-4 text-left shadow-sm">
                <Sparkles className="h-5 w-5 shrink-0 text-rose-500" />
                <p className="text-sm font-semibold leading-7 text-rose-700 sm:text-[15px]">
                  特别谢谢大家一直以来的支持和包容！！！
                </p>
              </div>
            </div>
            <button
              type="button"
              className="inline-flex h-11 w-11 items-center justify-center rounded-2xl border border-white/70 bg-white/85 text-slate-500 transition hover:text-slate-700"
              onClick={onClose}
              aria-label="关闭公告"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        <div className="grid gap-5 px-6 py-6 sm:px-8 md:grid-cols-[1fr_1.08fr]">
          <div className="rounded-[28px] border border-amber-100 bg-[linear-gradient(180deg,_rgba(255,251,235,0.95),_rgba(255,255,255,0.96))] p-6">
            <div className="flex items-center gap-3 text-amber-700">
              <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white text-amber-500 shadow-sm">
                <Mail className="h-5 w-5" />
              </div>
              <div>
                <p className="text-sm font-semibold text-slate-900">当前说明</p>
                <p className="text-xs uppercase tracking-[0.18em] text-amber-600">邮件提醒修复中</p>
              </div>
            </div>
            <p className="mt-5 text-[15px] leading-8 text-slate-600">
              如果你发现提醒邮件偶尔没到，不用担心，不是你宿舍偷偷“隐身”了，
              而是目前邮件通知出现了BUG 还在修复
            </p>
            <div className="mt-5 grid gap-3 sm:grid-cols-3">
              <div className="rounded-2xl border border-white bg-white/85 px-4 py-4 shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-amber-600">目前状态</p>
                <p className="mt-2 text-sm font-semibold text-slate-900">部分邮件可能延迟或漏收</p>
              </div>
              <div className="rounded-2xl border border-white bg-white/85 px-4 py-4 shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-amber-600">修复方向</p>
                <p className="mt-2 text-sm font-semibold text-slate-900">正在优化邮箱通道稳定性</p>
              </div>
              <div className="rounded-2xl border border-white bg-white/85 px-4 py-4 shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-amber-600">小提示</p>
                <p className="mt-2 text-sm font-semibold text-slate-900">房间数据本身不受影响</p>
              </div>
            </div>
          </div>

          <div className="rounded-[28px] border border-emerald-100 bg-[linear-gradient(180deg,_rgba(236,253,245,0.95),_rgba(255,255,255,0.98))] p-6">
            <div className="flex items-center gap-3 text-emerald-700">
              <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white text-emerald-500 shadow-sm">
                <Gift className="h-5 w-5" />
              </div>
              <div>
                <p className="text-sm font-semibold text-slate-900">彩蛋预告</p>
                <p className="text-xs uppercase tracking-[0.18em] text-emerald-600">抽奖模式准备上线</p>
              </div>
            </div>
            <div className="mt-5 space-y-4">
              <div className="rounded-[24px] border border-emerald-100 bg-white/85 p-5 shadow-sm">
                <div className="flex items-center gap-2 text-emerald-700">
                  <CalendarDays className="h-4.5 w-4.5" />
                  <p className="text-xs font-semibold uppercase tracking-[0.18em]">开奖节奏</p>
                </div>
                <p className="mt-3 text-[15px] leading-8 text-slate-600">
                  宿舍抽奖模式后面会正式上线，每月
                  <span className="mx-1 inline-flex rounded-full bg-emerald-100 px-3 py-1 text-sm font-bold text-emerald-700">1 日</span>
                  和
                  <span className="mx-1 inline-flex rounded-full bg-emerald-100 px-3 py-1 text-sm font-bold text-emerald-700">15 日</span>
                  各开奖一次，每次送出
                  <span className="mx-1 text-lg font-black text-slate-900">3 个名额</span>。
                </p>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="rounded-[24px] border border-emerald-100 bg-white/90 p-5 shadow-sm">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-emerald-600">已设置告警邮箱</p>
                  <p className="mt-3 text-3xl font-black tracking-tight text-emerald-700">15 电费</p>
              
                </div>
                <div className="rounded-[24px] border border-sky-100 bg-white/90 p-5 shadow-sm">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-sky-600">暂未设置邮箱</p>
                  <p className="mt-3 text-3xl font-black tracking-tight text-sky-700">7.5 电费</p>
                  <p className="mt-3 text-sm leading-7 text-slate-600">
                  </p>
                </div>
              </div>

              <div className="rounded-[24px] border border-dashed border-emerald-200 bg-white/80 px-5 py-4 text-[15px] leading-8 text-slate-700">
                小建议：把告警邮箱先填上，提醒更稳，抽奖奖励也更香。也再次谢谢大家一路支持，
                后面我们会把体验继续打磨得更好。
              </div>
            </div>
          </div>
        </div>

        <div className="flex flex-col-reverse gap-3 border-t border-slate-100 px-6 py-5 sm:flex-row sm:items-center sm:justify-end sm:px-8">
          <button type="button" className="secondary-btn" onClick={onClose}>
            先逛逛
          </button>
          <button type="button" className="primary-btn" onClick={onDismissForWeek}>
            一周内不再弹出
          </button>
        </div>
      </div>
    </div>
  );
}
