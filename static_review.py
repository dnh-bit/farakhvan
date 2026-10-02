"""Static review gate for the farakhvan Android project (candidate B / mainline).

Runs before every build in CI. Any failed check raises AssertionError -> non-zero
exit -> the workflow stops. This is NOT a compiler: `./gradlew assembleRelease`
and `testDebugUnitTest` in the same workflow are the real compile + unit checks.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import sqlite3
import json

root = Path(__file__).resolve().parent
checks = []


def check(condition, message):
    if not condition:
        raise AssertionError(message)
    checks.append(message)


def strip_comments(text):
    """Remove // and /* */ comments, keeping string literals intact."""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if text.startswith("//", i):
            nl = text.find("\n", i)
            i = n if nl == -1 else nl
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            i = n if j == -1 else j + 2
        elif c in "\"'":
            quote, j = c, i + 1
            out.append(c)
            while j < n:
                if text[j] == "\\":
                    out.append(text[j:j + 2])
                    j += 2
                    continue
                out.append(text[j])
                if text[j] == quote:
                    j += 1
                    break
                j += 1
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


def scan_kotlin(text, name):
    """Balanced delimiters, closed literals/comments."""
    stack = []
    pairs = {")": "(", "]": "[", "}": "{"}
    i, n = 0, len(text)
    while i < n:
        if text.startswith("//", i):
            nl = text.find("\n", i)
            i = n if nl == -1 else nl
        elif text.startswith("/*", i):
            j, depth = i + 2, 1
            while j < n and depth:
                if text.startswith("/*", j):
                    depth += 1; j += 2
                elif text.startswith("*/", j):
                    depth -= 1; j += 2
                else:
                    j += 1
            check(depth == 0, f"{name}: closed block comments")
            i = j
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3)
            check(j != -1, f"{name}: closed multiline string")
            i = j + 3
        elif text[i] in "\"'":
            quote, j = text[i], i + 1
            closed = False
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == quote:
                    closed = True
                    j += 1
                    break
                if text[j] == "\n":
                    break
                j += 1
            check(closed, f"{name}: closed string/character literal")
            i = j
        else:
            c = text[i]
            if c in "([{":
                stack.append(c)
            elif c in ")]}":
                check(bool(stack) and stack.pop() == pairs[c], f"{name}: matched delimiter {c}")
            i += 1
    check(not stack, f"{name}: balanced delimiters")


def extract_queries(text):
    """All @Query("..." + "...") SQL statements, concatenated per annotation."""
    queries, idx = [], 0
    while True:
        i = text.find("@Query(", idx)
        if i == -1:
            return queries
        j, depth, lits, cur = i + len("@Query("), 1, [], None
        while j < len(text) and depth > 0:
            c = text[j]
            if cur is not None:
                if c == "\\":
                    cur += text[j:j + 2]
                    j += 2
                    continue
                if c == '"':
                    lits.append(cur)
                    cur = None
                    j += 1
                    continue
                cur += c
                j += 1
                continue
            if c == '"':
                cur = ""
                j += 1
            elif text.startswith("//", j):
                nl = text.find("\n", j)
                j = len(text) if nl == -1 else nl + 1
            elif c == "(":
                depth += 1
                j += 1
            elif c == ")":
                depth -= 1
                j += 1
            else:
                j += 1
        if cur is not None:
            lits.append(cur)
        idx = j
        sql = "".join(lits)
        if sql.strip():
            queries.append(sql)


# ---------------------------------------------------------------- strings.xml
strings_tree = ET.parse(root / "app/src/main/res/values/strings.xml")
strings = {node.attrib["name"]: "".join(node.itertext()) for node in strings_tree.getroot()}
check(len(strings) == len(strings_tree.getroot()), "String resource names are unique")
check(strings.get("app_name") == "فراخوان", "Persian launcher label")
check(all(re.search(r"[؀-ۿ]", s) for s in strings.values()), "Every app string contains Persian text")

# ---------------------------------------------------------------- Kotlin sources
used = set()
kotlin_files = list(root.rglob("*.kt")) + list(root.rglob("*.kts"))
for f in kotlin_files:
    text = f.read_text(encoding="utf-8")
    scan_kotlin(text, f.name)
    imports = re.findall(r"^import .+$", text, re.M)
    check(len(imports) == len(set(imports)), f"{f.name}: no duplicate imports")
    check("import android.support" not in text and "kotlinx.android.synthetic" not in text,
          f"{f.name}: no deprecated imports")
    check("objectbox" not in text.lower(), f"{f.name}: no ObjectBox")
    used.update(re.findall(r"R\.string\.(\w+)", text))
    if "/src/main/" in str(f):
        code_only = strip_comments(text)
        check(re.search(r"[؀-ۿ]", code_only) is None, f"{f.name}: no hardcoded Persian UI text")
check(not (used - set(strings)), "All Kotlin string references exist in strings.xml")

# ---------------------------------------------------------------- resources / XML
for f in root.rglob("*.xml"):
    ET.parse(f)
check(True, "All XML is well formed")
for f in (root / "app/src/main/res").rglob("*"):
    if f.is_file():
        check(re.fullmatch(r"[a-z][a-z0-9_]*", f.stem) is not None, f"{f.name}: valid Android resource name")

# ---------------------------------------------------------------- manifest
android = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
permissions = {n.get(android + "name") for n in manifest.findall("uses-permission")}
check("android.permission.INTERNET" not in permissions, "No INTERNET permission (app is offline)")
required = {"SEND_SMS", "READ_CONTACTS", "POST_NOTIFICATIONS", "READ_PHONE_STATE",
            "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_DATA_SYNC"}
check({"android.permission." + p for p in required} <= permissions,
      "All runtime/foreground permissions declared")
app = manifest.find("application")
check(app.get(android + "supportsRtl") == "true", "RTL enabled")
check(app.get(android + "allowBackup") == "false", "No automatic backup of phone-number database")
check(app.find("service").get(android + "foregroundServiceType") == "dataSync",
      "Declared data-sync foreground service")
for tag in ["activity", "service", "receiver", "provider"]:
    check(all(e.get(android + "exported") in {"true", "false"} for e in app.findall(tag)),
          f"{tag}: android:exported is explicit")

# ---------------------------------------------------------------- version + workflow
gradle_app = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
check(re.search(r'versionName = "\d+\.\d+\.\d+"', gradle_app) is not None, "Semantic versionName present")
check(re.search(r"versionCode = \d+", gradle_app) is not None, "versionCode present")
workflow = (root / ".github/workflows/build-android.yml").read_text(encoding="utf-8")
check("./gradlew assembleRelease" in workflow, "Exact CI build command")
check("./gradlew testDebugUnitTest" in workflow, "Unit tests run in CI")
check("python3 static_review.py" in workflow, "This review gate runs in CI")
check("workflow_dispatch:" in workflow and "tags: ['v*']" in workflow, "Manual and version-tag CI triggers")
check("softprops/action-gh-release@v2" in workflow and "gradle/actions/setup-gradle@v4" in workflow,
      "CI release and Gradle actions")

# ---------------------------------------------------------------- wrapper / license / assets
wrapper = (root / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
check("gradle-8.9-bin.zip" in wrapper and "distributionSha256Sum=" in wrapper,
      "Pinned Gradle with SHA-256 verification")
check((root / "gradle/wrapper/gradle-wrapper.jar").stat().st_size > 30000, "Gradle wrapper jar committed")
check("Copyright (c) 2019 Carlos Anyona" in (root / "LICENSE").read_text(encoding="utf-8"),
      "Original MIT copyright retained")
for weight in ["regular", "medium", "bold"]:
    p = root / f"app/src/main/res/font/vazirmatn_{weight}.ttf"
    check(p.exists() and p.stat().st_size > 100000, f"Bundled real Vazirmatn {weight} font")
check((root / "app/src/main/assets/licenses/OFL-Vazirmatn.txt").exists(),
      "Font OFL notice bundled in APK assets")
check((root / "app/src/main/assets/licenses/LICENSE.txt").read_bytes() == (root / "LICENSE").read_bytes(),
      "App MIT notice bundled in APK assets")

# ---------------------------------------------------------------- Room SQL
db = sqlite3.connect(":memory:")
db.executescript("""
PRAGMA foreign_keys=ON;
CREATE TABLE contact_groups(id INTEGER PRIMARY KEY, name TEXT, emoji TEXT, color INTEGER,
  createdAt INTEGER, lastUsedAt INTEGER);
CREATE TABLE members(id INTEGER PRIMARY KEY, groupId INTEGER REFERENCES contact_groups(id)
  ON DELETE CASCADE, number TEXT, name TEXT, valid INTEGER, addedAt INTEGER,
  UNIQUE(groupId, number));
CREATE TABLE campaigns(id INTEGER PRIMARY KEY, groupId INTEGER, targetName TEXT, message TEXT,
  createdAt INTEGER, simSubId INTEGER, delayMs INTEGER, status TEXT);
CREATE TABLE send_results(id INTEGER PRIMARY KEY, campaignId INTEGER
  REFERENCES campaigns(id) ON DELETE CASCADE, number TEXT, name TEXT, body TEXT,
  status TEXT, reason TEXT, updatedAt INTEGER);
""")
daos = (root / "app/src/main/java/com/revosleap/text/data/Daos.kt").read_text(encoding="utf-8")
queries = extract_queries(daos)
check(len(queries) >= 15, f"Room queries found ({len(queries)})")
for sql in queries:
    db.execute("EXPLAIN " + re.sub(r":[A-Za-z_]\w*", "1", sql))
check(True, f"{len(queries)} Room SQL statements parse in SQLite")


def findq(fragment):
    for q in queries:
        if fragment in q:
            return q
    raise AssertionError("missing Room query: " + fragment)


# Behavioural guards, executed against the real SQL.
def findq(fragment):
    for q in queries:
        if fragment in q:
            return q
    raise AssertionError("missing Room query: " + fragment)


def execq(fragment, **subs):
    sql = findq(fragment)
    for name, value in subs.items():
        sql = sql.replace(":" + name, value)
    db.execute(re.sub(r":[A-Za-z_]\w*", "1", sql))


db.execute("INSERT INTO campaigns VALUES(1,1,'g','b',0,-1,3000,'RUNNING')")
db.execute("INSERT INTO send_results VALUES(1,1,'+989121234567','n','body','SENT','',0)")
db.execute("INSERT INTO send_results VALUES(2,1,'+98912345678','n','body','SENDING','',0)")

# 1. crash/kill guard: a row stuck in SENDING is failed, SENT rows are never touched
execq("WHERE status = 'SENDING'", reason="'timeout'")
check(db.execute("SELECT status FROM send_results WHERE id=1").fetchone()[0] == "SENT",
      "Stale-SENDING guard does not overwrite sent rows")
check(db.execute("SELECT status FROM send_results WHERE id=2").fetchone()[0] == "FAILED",
      "Stale-SENDING row is failed after a crash")

# 2. individual retry only re-opens FAILED rows
execq("WHERE id = :id AND status = 'FAILED'", id="1")
check(db.execute("SELECT status FROM send_results WHERE id=1").fetchone()[0] == "SENT",
      "Individual retry never re-opens a sent row")

# 3. bulk retry of failed rows in a campaign leaves SENT alone
db.execute("UPDATE send_results SET status='FAILED' WHERE id=2")
execq("campaignId = :campaignId AND status = 'FAILED'", campaignId="1")
check(db.execute("SELECT status FROM send_results WHERE id=1").fetchone()[0] == "SENT",
      "Campaign retry leaves sent rows untouched")
check(db.execute("SELECT status FROM send_results WHERE id=2").fetchone()[0] == "PENDING",
      "Campaign retry re-opens failed rows")

# 4. cancel marks only unfinished rows
execq("status IN ('PENDING', 'SENDING')", campaignId="1", reason="'cancelled'", now="0")
check(db.execute("SELECT status FROM send_results WHERE id=1").fetchone()[0] == "SENT",
      "Cancel/failUnsent never touches completed rows")

# 5. markInterrupted does not disturb finished campaigns
db.execute("INSERT INTO campaigns VALUES(2,1,'g','b',0,-1,3000,'DONE')")
execq("status IN ('RUNNING', 'PAUSED')")
check(db.execute("SELECT status FROM campaigns WHERE id=2").fetchone()[0] == "DONE",
      "Interrupt query does not touch finished campaigns")

# 6. schema guards
db.execute("INSERT INTO contact_groups VALUES(1,'g','',0,0,0)")
db.execute("INSERT OR IGNORE INTO members VALUES(1,1,'+989121234567','n',1,0)")
db.execute("INSERT OR IGNORE INTO members VALUES(2,1,'+989121234567','n',1,0)")
check(db.execute("SELECT COUNT(*) FROM members").fetchone()[0] == 1, "Group member uniqueness constraint")
db.execute("DELETE FROM contact_groups WHERE id=1")
check(db.execute("SELECT COUNT(*) FROM members").fetchone()[0] == 0, "Member cascade on group deletion")
check(db.execute("SELECT COUNT(*) FROM campaigns").fetchone()[0] == 2, "Group deletion keeps campaign history")

summary = {
    "static_checks": len(checks),
    "kotlin_files": len(list(root.rglob("*.kt"))),
    "string_resources": len(strings),
    "room_sql_statements": len(queries),
    "xml_files": len(list(root.rglob("*.xml"))),
    "compiled": False,
    "device_tested": False,
}
(root / "static-review-summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
print(json.dumps(summary, indent=2))
