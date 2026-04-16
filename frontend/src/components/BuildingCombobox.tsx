import { useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronsUpDown, Search } from "lucide-react";
import type { BuildingOption } from "../lib/buildings";

interface BuildingComboboxProps {
  label: string;
  options: BuildingOption[];
  valueId: string;
  valueName: string;
  onChange: (option: BuildingOption) => void;
  error?: string;
  placeholder?: string;
}

export function BuildingCombobox({
  label,
  options,
  valueId,
  valueName,
  onChange,
  error,
  placeholder = "输入楼栋名称或 ID 搜索"
}: BuildingComboboxProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");

  const displayValue = valueName ? `${valueName}（ID ${valueId}）` : "";

  useEffect(() => {
    if (!open) {
      setQuery(displayValue);
    }
  }, [displayValue, open]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const filteredOptions = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) {
      return options;
    }
    return options.filter((option) => option.name.toLowerCase().includes(keyword) || option.id.toLowerCase().includes(keyword));
  }, [options, query]);

  const handleSelect = (option: BuildingOption) => {
    onChange(option);
    setQuery(`${option.name}（ID ${option.id}）`);
    setOpen(false);
  };

  return (
    <label className="block">
      <span className="field-label">{label}</span>
      <div ref={containerRef} className="relative">
        <Search className="pointer-events-none absolute left-4 top-1/2 z-10 h-4 w-4 -translate-y-1/2 text-slate-400" />
        <input
          type="text"
          className={`field pl-11 pr-12 ${error ? "border-rose-300 focus:border-rose-300 focus:ring-rose-100" : ""}`}
          placeholder={placeholder}
          value={open ? query : displayValue}
          onFocus={() => {
            setOpen(true);
            setQuery(valueName || "");
          }}
          onChange={(event) => {
            setOpen(true);
            setQuery(event.target.value);
          }}
        />
        <button
          type="button"
          className="absolute right-3 top-1/2 z-10 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-xl text-slate-400 transition hover:bg-slate-100 hover:text-slate-600"
          onClick={() => {
            setOpen((current) => {
              const nextOpen = !current;
              setQuery(nextOpen ? valueName || "" : displayValue);
              return nextOpen;
            });
          }}
          aria-label="切换楼栋下拉框"
        >
          <ChevronsUpDown className="h-4 w-4" />
        </button>

        {open ? (
          <div className="absolute left-0 right-0 top-[calc(100%+10px)] z-30 overflow-hidden rounded-[24px] border border-slate-200 bg-white shadow-[0_20px_50px_rgba(15,23,42,0.14)]">
            <div className="max-h-64 overflow-y-auto p-2">
              {filteredOptions.length > 0 ? (
                filteredOptions.map((option) => {
                  const selected = option.id === valueId;
                  return (
                    <button
                      key={option.id}
                      type="button"
                      className={`flex w-full items-center justify-between rounded-2xl px-4 py-3 text-left transition ${
                        selected ? "bg-slate-900 text-white" : "text-slate-700 hover:bg-slate-50"
                      }`}
                      onMouseDown={(event) => event.preventDefault()}
                      onClick={() => handleSelect(option)}
                    >
                      <div>
                        <p className="font-semibold">{option.name}</p>
                        <p className={`text-xs ${selected ? "text-slate-200" : "text-slate-500"}`}>楼栋 ID：{option.id}</p>
                      </div>
                      {selected ? <Check className="h-4 w-4" /> : null}
                    </button>
                  );
                })
              ) : (
                <div className="rounded-2xl px-4 py-6 text-sm text-slate-500">没有匹配的楼栋，请尝试输入名称关键字或楼栋 ID。</div>
              )}
            </div>
          </div>
        ) : null}
      </div>
  
      {error ? <span className="mt-2 block text-sm text-rose-500">{error}</span> : null}
    </label>
  );
}
