import type { ReactNode } from "react";
import { NormalizerDemo, SendDemo, SmsCounterDemo } from "./components/Demos";

function Section({ id, title, subtitle, children }: { id?: string; title: string; subtitle?: string; children: ReactNode }) {
  return (
    <section id={id} className="mx-auto w-full max-w-5xl px-5 py-10">
      <h2 className="text-2xl font-bold text-slate-900">{title}</h2>
      {subtitle && <p className="mt-2 max-w-2xl text-slate-600">{subtitle}</p>}
      <div className="mt-6">{children}</div>
    </section>
  );
}

const features = [
  ["گروه‌ها", "ساخت، ویرایش و حذف گروه با رنگ و ایموجی؛ حذف با کشیدن همراه «بازگردانی»."],
  ["چهار راه افزودن عضو", "انتخاب از مخاطبین، تایپ یک شماره، چسباندن فهرست، کپی از گروه دیگر."],
  ["نرمال‌سازی شماره", "ارقام فارسی و عربی، +98 / 0098 / 98 / 09 / 9، حذف تکراری‌ها و علامت‌گذاری نامعتبرها."],
  ["شمارندهٔ پیامک", "تشخیص GSM-7 یا یونیکد و تعداد بخش‌های هر پیام برای هر گیرنده، همراه با {name}."],
  ["موتور ارسال", "سرویس پیش‌زمینه، فاصلهٔ ۱ تا ۳۰ ثانیه، وضعیت زندهٔ هر شماره، توقف، ادامه، لغو و تلاش مجدد."],
  ["ارسال‌های ناتمام", "پس از بسته‌شدن ناگهانی برنامه، ادامه از اولین شمارهٔ ارسال‌نشده؛ هیچ پیام تکراری فرستاده نمی‌شود."],
  ["تاریخچه و CSV", "گزارش هر ارسال با نتیجهٔ هر شماره و خروجی CSV از طریق منوی اشتراک‌گذاری."],
  ["حریم خصوصی", "بدون مجوز اینترنت، بدون حساب کاربری، بدون ردیابی؛ فقط هزینهٔ پیامک سیم‌کارت."],
];

const tree = `farakhvan-android/
├─ LICENSE (MIT — Carlos Anyona)       README.md       .gitignore
├─ settings.gradle.kts  build.gradle.kts  gradle.properties  gradlew
├─ gradle/wrapper/gradle-wrapper.properties   (Gradle 8.9)
├─ .github/workflows/build-android.yml
└─ app/
   ├─ build.gradle.kts   proguard-rules.pro
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ res/ values/strings.xml (فارسی) · themes.xml · drawable/ · xml/file_paths.xml
      └─ java/com/farakhvan/text/
         ├─ MainActivity.kt · FarakhvanApp.kt
         ├─ data/     Entities · Daos · AppDatabase · Repo · Settings
         ├─ sending/  SendController · SendService (foreground)
         ├─ util/     PhoneNormalizer · SmsUtil · ContactsHelper · Formatting
         └─ ui/       AppRoot · Groups · GroupDetail · Compose · Campaign
                      History · Settings · Dialogs · Components · Theme`;

export default function App() {
  return (
    <div dir="rtl" className="min-h-screen">
      <header className="bg-gradient-to-br from-teal-800 via-teal-700 to-emerald-600 text-white">
        <div className="mx-auto max-w-5xl px-5 py-14">
          <div className="flex items-center gap-4">
            <div className="grid h-16 w-16 place-items-center rounded-2xl bg-white/15 text-3xl shadow-lg ring-1 ring-white/30">💬</div>
            <div>
              <h1 className="text-4xl font-bold">فراخوان</h1>
              <p className="mt-1 text-teal-50">پیامک گروهی با یک ضربه — بدون سرور، بدون اینترنت</p>
            </div>
          </div>
          <p className="mt-6 max-w-2xl leading-8 text-teal-50">
            گروه‌هایی از شماره‌ها بسازید، یک پیام بنویسید و آن را از سیم‌کارت خودتان برای همهٔ اعضا بفرستید و نتیجهٔ هر شماره را زنده ببینید.
            برنامهٔ اندروید (Kotlin + Jetpack Compose + Room) کامل در پوشهٔ <span dir="ltr" className="rounded bg-black/20 px-2 py-0.5 font-mono text-sm">farakhvan-android/</span> است.
          </p>
          <div className="mt-6 flex flex-wrap gap-2 text-sm">
            {["Material 3", "راست‌به‌چپ", "Room", "Foreground Service", "minSdk 24 · targetSdk 35", "MIT"].map((t) => (
              <span key={t} className="rounded-full bg-white/15 px-3 py-1 ring-1 ring-white/25">
                {t}
              </span>
            ))}
          </div>
        </div>
      </header>

      <Section
        title="امتحانش کنید"
        subtitle="منطق اصلی برنامه (نرمال‌سازی، شمارندهٔ پیامک و صف ارسال) به TypeScript برگردانده شده تا همین‌جا رفتارش را ببینید. در برنامهٔ واقعی همین قواعد در Kotlin اجرا می‌شوند."
      >
        <div className="grid gap-6 lg:grid-cols-2">
          <div className="rounded-2xl border border-slate-200 bg-white/70 p-5 shadow-sm">
            <h3 className="mb-3 font-semibold text-slate-900">۱. چسباندن فهرست شماره‌ها</h3>
            <NormalizerDemo />
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white/70 p-5 shadow-sm">
            <h3 className="mb-3 font-semibold text-slate-900">۲. شمارندهٔ پیامک و {"{name}"}</h3>
            <SmsCounterDemo />
          </div>
          <div className="rounded-2xl border border-slate-200 bg-white/70 p-5 shadow-sm lg:col-span-2">
            <h3 className="mb-3 font-semibold text-slate-900">۳. صف ارسال (شبیه‌سازی)</h3>
            <div className="mx-auto max-w-md">
              <SendDemo />
            </div>
          </div>
        </div>
      </Section>

      <Section title="امکانات">
        <div className="grid gap-4 sm:grid-cols-2">
          {features.map(([t, d]) => (
            <div key={t} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <h3 className="font-semibold text-teal-800">{t}</h3>
              <p className="mt-1 text-sm leading-7 text-slate-600">{d}</p>
            </div>
          ))}
        </div>
      </Section>

      <Section title="ساختار پروژه" subtitle="همهٔ فایل‌های برنامهٔ اندروید داخل پوشهٔ farakhvan-android/ این workspace ساخته شده‌اند.">
        <pre dir="ltr" className="overflow-auto rounded-2xl bg-slate-900 p-5 text-left text-xs leading-6 text-slate-100">
          {tree}
        </pre>
      </Section>

      <Section title="ساخت و انتشار">
        <div className="grid gap-4 md:grid-cols-2">
          <pre dir="ltr" className="overflow-auto rounded-2xl bg-slate-900 p-5 text-left text-xs leading-6 text-slate-100">
{`# one-time, if gradle-wrapper.jar is missing
gradle wrapper --gradle-version 8.9

./gradlew assembleRelease
# app/build/outputs/apk/release/*.apk`}
          </pre>
          <ul className="space-y-2 rounded-2xl border border-slate-200 bg-white p-5 text-sm leading-7 text-slate-700">
            <li>• push به main یا اجرای دستی ← ساخت APK و آپلود به‌عنوان artifact</li>
            <li>• push تگ v* ← ساخت و انتشار GitHub Release با فایل farakhvan-v0.1.0.apk</li>
            <li>• امضا فقط از Secrets: ANDROID_KEYSTORE_B64، ANDROID_KEYSTORE_PASSWORD، ANDROID_KEY_ALIAS؛ در غیر این صورت امضای debug</li>
            <li>• فونت وزیرمتن (SIL OFL) هنگام ساخت دانلود و داخل APK بسته‌بندی می‌شود</li>
          </ul>
        </div>
      </Section>

      <Section title="نکات صادقانه">
        <ul className="space-y-2 rounded-2xl border border-amber-200 bg-amber-50 p-5 text-sm leading-7 text-amber-900">
          <li>• سورس اصلی «Text» در اختیار این محیط نبود؛ پروژه از روی شرح نیازمندی از صفر نوشته شد و فقط مجوز MIT و هویت پکیج حفظ شده است.</li>
          <li>• این محیط Gradle و اندروید ندارد؛ کد اندروید اینجا کامپایل نشده و اولین ساخت واقعی روی CI انجام می‌شود. فایل gradle-wrapper.jar (باینری) را CI خودکار می‌سازد.</li>
          <li>• ردیف «زبان» در تنظیمات اطلاعاتی است، چون برنامه فقط فارسی است.</li>
          <li>• به قوانین ارسال پیامک انبوه در کشور خود احترام بگذارید و فقط برای کسانی بفرستید که انتظارش را دارند.</li>
        </ul>
      </Section>

      <footer className="border-t border-slate-200 py-8 text-center text-sm text-slate-500">
        فراخوان · مجوز MIT · © ۲۰۱۹ Carlos Anyona (پروژهٔ الهام‌بخش Text)
      </footer>
    </div>
  );
}
