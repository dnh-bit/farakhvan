import { useEffect, useMemo, useState } from "react";
import { parseBulk } from "../lib/phone";
import { analyze } from "../lib/sms";

const SAMPLE_LIST = `۰۹۱۲۳۴۵۶۷۸۹, 0935 123 4567
+989121112233؛ 912-555-0000
0912 345 6789
00989351234567
12345`;

const ltr = (s: string) => (
  <span dir="ltr" className="inline-block font-mono">
    {s}
  </span>
);

export function NormalizerDemo() {
  const [text, setText] = useState(SAMPLE_LIST);
  const parsed = useMemo(() => parseBulk(text), [text]);
  const valid = parsed.numbers.filter((n) => n.valid).length;
  const invalid = parsed.numbers.length - valid;

  return (
    <div className="space-y-3">
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        rows={6}
        className="w-full rounded-xl border border-slate-300 bg-white p-3 text-sm outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-200"
        placeholder="شماره‌ها را اینجا بچسبانید…"
      />
      <p className="text-sm text-slate-600">
        <b className="text-teal-700">{valid}</b> معتبر · <b className="text-red-600">{invalid}</b> نامعتبر ·{" "}
        <b>{parsed.duplicates}</b> تکراری حذف‌شده
      </p>
      <ul className="max-h-56 divide-y divide-slate-100 overflow-auto rounded-xl border border-slate-200 bg-white">
        {parsed.numbers.length === 0 && <li className="p-3 text-sm text-slate-400">چیزی برای نمایش نیست</li>}
        {parsed.numbers.map((n) => (
          <li key={n.canonical} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
            <span className={n.valid ? "text-slate-800" : "text-red-600"}>{ltr(n.canonical)}</span>
            <span className={n.valid ? "text-xs text-teal-700" : "text-xs text-red-600"}>
              {n.valid ? "معتبر" : "نامعتبر — ارسال نمی‌شود"}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function SmsCounterDemo() {
  const [message, setMessage] = useState("سلام {name} عزیز، سفارش شما آمادهٔ تحویل است.");
  const [name, setName] = useState("رضا");
  const preview = message.split("{name}").join(name);
  const info = analyze(preview);

  return (
    <div className="space-y-3">
      <textarea
        value={message}
        onChange={(e) => setMessage(e.target.value)}
        rows={4}
        className="w-full rounded-xl border border-slate-300 bg-white p-3 text-sm outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-200"
      />
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => setMessage((m) => m + "{name}")}
          className="rounded-full border border-teal-600 px-3 py-1 text-sm text-teal-700 hover:bg-teal-50"
        >
          درج {"{name}"}
        </button>
        <label className="flex items-center gap-2 text-sm text-slate-600">
          نام نمونه:
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-28 rounded-lg border border-slate-300 px-2 py-1 outline-none focus:border-teal-600"
          />
        </label>
      </div>
      <div className="rounded-xl bg-teal-50 p-3 text-sm text-teal-900">
        {info.parts === 0 ? (
          "متن پیام را بنویسید"
        ) : (
          <>
            {info.unicode ? "یونیکد (فارسی)" : "لاتین (GSM-7)"} · {info.used} از {info.perPart} نویسه ·{" "}
            <b>{info.parts} پیامک</b> برای هر گیرنده
          </>
        )}
      </div>
      <p className="rounded-xl border border-dashed border-slate-300 p-3 text-sm text-slate-600">پیش‌نمایش: {preview}</p>
    </div>
  );
}

type Status = "pending" | "sending" | "sent" | "failed";
interface Item {
  number: string;
  name: string;
  status: Status;
  reason?: string;
  willFail?: boolean;
  invalid?: boolean;
}

const initialItems = (): Item[] => [
  { number: "+989123456789", name: "رضا" },
  { number: "+989351234567", name: "مریم" },
  { number: "+989121112233", name: "علی", willFail: true },
  { number: "12345", name: "", invalid: true },
  { number: "+989125550000", name: "سارا" },
  { number: "+989131234000", name: "نیما" },
].map((i) => ({ ...i, status: "pending" as Status }));

type Phase = "idle" | "running" | "paused" | "done" | "cancelled";

export function SendDemo() {
  const [items, setItems] = useState<Item[]>(initialItems);
  const [phase, setPhase] = useState<Phase>("idle");
  const [delay, setDelay] = useState(2);

  useEffect(() => {
    if (phase !== "running") return;
    const sendingIdx = items.findIndex((i) => i.status === "sending");
    if (sendingIdx >= 0) {
      const t = setTimeout(() => {
        setItems((prev) =>
          prev.map((it, i) =>
            i === sendingIdx
              ? it.willFail
                ? { ...it, status: "failed", reason: "آنتن یا سرویس شبکه در دسترس نیست" }
                : { ...it, status: "sent" }
              : it,
          ),
        );
      }, 700);
      return () => clearTimeout(t);
    }
    const next = items.findIndex((i) => i.status === "pending");
    if (next < 0) {
      setPhase("done");
      return;
    }
    if (items[next].invalid) {
      // invalid numbers are skipped immediately, without waiting
      setItems((prev) => prev.map((it, i) => (i === next ? { ...it, status: "failed", reason: "شمارهٔ نامعتبر" } : it)));
      return;
    }
    const attemptedBefore = items.some((i) => (i.status === "sent" || i.status === "failed") && !i.invalid);
    const t = setTimeout(
      () => setItems((prev) => prev.map((it, i) => (i === next ? { ...it, status: "sending" } : it))),
      attemptedBefore ? delay * 1000 : 0,
    );
    return () => clearTimeout(t);
  }, [phase, items, delay]);

  const done = items.filter((i) => i.status === "sent" || i.status === "failed").length;
  const failed = items.filter((i) => i.status === "failed").length;
  const pct = Math.round((done / items.length) * 100);

  const cancel = () => {
    setItems((prev) =>
      prev.map((it) =>
        it.status === "pending" || it.status === "sending" ? { ...it, status: "failed", reason: "لغو شد" } : it,
      ),
    );
    setPhase("cancelled");
  };
  const retryFailed = () => {
    setItems((prev) => prev.map((it) => (it.status === "failed" ? { ...it, status: "pending", reason: undefined, willFail: false } : it)));
    setPhase("running");
  };
  const reset = () => {
    setItems(initialItems());
    setPhase("idle");
  };

  const phaseLabel: Record<Phase, string> = {
    idle: "آماده",
    running: "در حال ارسال",
    paused: "متوقف",
    done: "پایان‌یافته",
    cancelled: "لغوشده",
  };

  const btn = "rounded-xl px-4 py-2.5 text-sm font-medium transition disabled:opacity-40";

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-4">
        <div
          className="relative grid h-24 w-24 shrink-0 place-items-center rounded-full"
          style={{ background: `conic-gradient(#0f766e ${pct * 3.6}deg, #e2e8f0 0deg)` }}
        >
          <div className="grid h-[76px] w-[76px] place-items-center rounded-full bg-white text-center text-sm font-bold text-slate-800">
            {done} از {items.length}
          </div>
        </div>
        <div className="text-sm text-slate-600">
          <div className="font-semibold text-slate-900">{phaseLabel[phase]}</div>
          <div>
            ارسال‌شده: {done - failed} · ناموفق: {failed}
          </div>
          <label className="mt-2 flex items-center gap-2">
            فاصله: {delay} ثانیه
            <input type="range" min={1} max={5} value={delay} onChange={(e) => setDelay(+e.target.value)} className="accent-teal-700" />
          </label>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        {phase === "idle" && (
          <button className={`${btn} bg-teal-700 text-white hover:bg-teal-800`} onClick={() => setPhase("running")}>
            شروع ارسال
          </button>
        )}
        {phase === "running" && (
          <button className={`${btn} bg-teal-100 text-teal-900`} onClick={() => setPhase("paused")}>
            توقف موقت
          </button>
        )}
        {phase === "paused" && (
          <button className={`${btn} bg-teal-700 text-white`} onClick={() => setPhase("running")}>
            ادامه
          </button>
        )}
        {(phase === "running" || phase === "paused") && (
          <button className={`${btn} border border-red-300 text-red-600 hover:bg-red-50`} onClick={cancel}>
            لغو ارسال
          </button>
        )}
        {(phase === "done" || phase === "cancelled") && failed > 0 && (
          <button className={`${btn} bg-teal-100 text-teal-900`} onClick={retryFailed}>
            تلاش مجدد ناموفق‌ها ({failed})
          </button>
        )}
        {phase !== "idle" && phase !== "running" && (
          <button className={`${btn} border border-slate-300 text-slate-700`} onClick={reset}>
            از اول
          </button>
        )}
      </div>

      <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
        {items.map((it) => (
          <li key={it.number} className="flex items-center gap-3 px-3 py-2.5 text-sm">
            <span className="w-6 text-center text-lg">
              {it.status === "sent" && <span className="text-emerald-600">✓</span>}
              {it.status === "failed" && <span className="text-red-600">✕</span>}
              {it.status === "sending" && <span className="inline-block animate-spin text-amber-500">◌</span>}
              {it.status === "pending" && <span className="text-slate-300">●</span>}
            </span>
            <div className="min-w-0 flex-1">
              <div className={it.invalid ? "text-red-600" : "text-slate-800"}>{it.name || ltr(it.number)}</div>
              <div className="text-xs text-slate-500">
                {it.name && ltr(it.number)}
                {it.reason && <span className="mr-2 text-red-600">{it.reason}</span>}
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
