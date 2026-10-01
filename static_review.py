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

def scan_kotlin(text, name):
    stack = []
    i = 0
    pairs = {")": "(", "]": "[", "}": "{"}
    while i < len(text):
        if text.startswith("//", i):
            end = text.find("\n", i)
            i = len(text) if end == -1 else end + 1
        elif text.startswith("/*", i):
            depth = 1
            i += 2
            while i < len(text) and depth:
                if text.startswith("/*", i):
                    depth += 1
                    i += 2
                elif text.startswith("*/", i):
                    depth -= 1
                    i += 2
                else:
                    i += 1
            check(depth == 0, f"{name}: closed block comments")
        elif text.startswith('"""', i):
            end = text.find('"""', i + 3)
            check(end != -1, f"{name}: closed multiline strings")
            i = end + 3
        elif text[i] in "\"'":
            quote = text[i]
            i += 1
            closed = False
            while i < len(text):
                if text[i] == "\\":
                    i += 2
                elif text[i] == quote:
                    i += 1
                    closed = True
                    break
                elif text[i] == "\n":
                    break
                else:
                    i += 1
            check(closed, f"{name}: closed string/character literal")
        else:
            c = text[i]
            if c in "([{":
                stack.append(c)
            elif c in ")]}":
                check(bool(stack) and stack.pop() == pairs[c], f"{name}: matched delimiter {c}")
            i += 1
    check(not stack, f"{name}: balanced delimiters")

used = set()
strings_tree = ET.parse(root / "app/src/main/res/values/strings.xml")
strings = {node.attrib["name"]: "".join(node.itertext()) for node in strings_tree.getroot()}
check(len(strings) == len(strings_tree.getroot()), "String resource names are unique")
check(strings["app_name"] == "فراخوان", "Persian launcher label")
check(all(re.search(r"[\u0600-\u06ff]", s) for s in strings.values()), "Every app string contains Persian text")
for f in list(root.rglob("*.kt")) + list(root.rglob("*.kts")):
    text = f.read_text()
    scan_kotlin(text, f.name)
    imports = re.findall(r"^import .+$", text, re.M)
    check(len(imports) == len(set(imports)), f"{f.name}: no duplicate imports")
    check("import android.support" not in text and "kotlinx.android.synthetic" not in text,
          f"{f.name}: no deprecated imports")
    check("objectbox" not in text.lower(), f"{f.name}: no ObjectBox")
    used.update(re.findall(r"R\.string\.(\w+)", text))
    if "/src/main/" in str(f):
        check(re.search(r"[\u0600-\u06ff]", text) is None, f"{f.name}: no hardcoded Persian UI text")
check(not (used - strings.keys()), "All Kotlin string references exist")
for f in root.rglob("*.xml"):
    ET.parse(f)
check(True, "All XML is well formed")
for f in (root / "app/src/main/res").rglob("*"):
    if f.is_file():
        check(re.fullmatch(r"[a-z][a-z0-9_]*", f.stem) is not None, f"{f.name}: valid Android resource name")
android = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
permissions = {n.get(android+"name") for n in manifest.findall("uses-permission")}
check("android.permission.INTERNET" not in permissions, "No INTERNET permission")
required = {"SEND_SMS", "READ_CONTACTS", "POST_NOTIFICATIONS", "READ_PHONE_STATE",
            "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_SPECIAL_USE"}
check({"android.permission."+p for p in required} <= permissions, "All requested runtime/foreground permissions declared")
app = manifest.find("application")
check(app.get(android+"supportsRtl") == "true", "RTL manifest enabled")
check(app.get(android+"allowBackup") == "false", "No automatic backup of phone-number database")
check(app.find("service").get(android+"foregroundServiceType") == "specialUse", "Declared special-use foreground service")
for tag in ["activity", "service", "receiver", "provider"]:
    check(all(e.get(android+"exported") in {"true", "false"} for e in app.findall(tag)), f"{tag}: exported is explicit")
workflow = (root / ".github/workflows/build-android.yml").read_text()
check("./gradlew assembleRelease" in workflow, "Exact CI build command")
check("workflow_dispatch:" in workflow and "tags: ['v*']" in workflow, "Manual, main and version-tag CI triggers")
check("softprops/action-gh-release@v2" in workflow and "gradle/actions/setup-gradle@v4" in workflow, "CI release and Gradle actions")
wrapper = (root / "gradle/wrapper/gradle-wrapper.properties").read_text()
check("gradle-8.9-bin.zip" in wrapper and "distributionSha256Sum=" in wrapper, "Compatible pinned Gradle with SHA-256")
check("Copyright (c) 2019 Carlos Anyona" in (root / "LICENSE").read_text(), "Original MIT copyright retained")
check((root / "app/src/main/res/font/vazirmatn.ttf").stat().st_size > 100000, "Bundled real Vazirmatn font")
check((root / "app/src/main/assets/licenses/OFL-Vazirmatn.txt").exists(), "Font OFL notice bundled in APK assets")
check((root / "app/src/main/assets/licenses/LICENSE.txt").read_bytes() == (root / "LICENSE").read_bytes(), "App MIT notice bundled in APK assets")

# SQLite parse-check every Room SQL statement against an equivalent v1 schema.
# This is not Room/KSP compilation or an Android integration test.
db = sqlite3.connect(":memory:")
db.executescript("""
PRAGMA foreign_keys=ON;
CREATE TABLE groups(id INTEGER PRIMARY KEY,name TEXT,emoji TEXT,color INTEGER,lastUsed INTEGER,deleted INTEGER);
CREATE TABLE members(id INTEGER PRIMARY KEY,groupId INTEGER REFERENCES groups(id) ON DELETE CASCADE,
  number TEXT,name TEXT,valid INTEGER,UNIQUE(groupId,number));
CREATE TABLE campaigns(id INTEGER PRIMARY KEY,createdAt INTEGER,target TEXT,body TEXT,
  subscriptionId INTEGER,delaySeconds INTEGER,state TEXT);
CREATE TABLE send_results(id INTEGER PRIMARY KEY,campaignId INTEGER REFERENCES campaigns(id),
  number TEXT,name TEXT,renderedBody TEXT,valid INTEGER,status TEXT,reason TEXT,timestamp INTEGER,
  attempt INTEGER,partCount INTEGER);
CREATE TABLE send_parts(resultId INTEGER,attempt INTEGER,part INTEGER,code INTEGER,
  PRIMARY KEY(resultId,attempt,part));
""")
data = (root / "app/src/main/java/com/revosleap/text/data/Database.kt").read_text()
queries = re.findall(r'@Query\((?:"""([\s\S]*?)"""|"((?:[^"\\]|\\.)*)")\)', data)
for multiline, single in queries:
    sql = multiline or single
    sql = re.sub(r":\w+", "1", sql)
    db.execute("EXPLAIN " + sql)
check(True, f"{len(queries)} Room SQL statements parse in SQLite")
# A delayed callback/timeout race may not turn an already-sent row into unknown.
db.execute("INSERT INTO campaigns VALUES(1,0,'g','b',-1,3,'running')")
db.execute("INSERT INTO send_results VALUES(1,1,'+989121234567','n','b',1,'sent','',1,1,1)")
db.execute("UPDATE send_results SET status='unknown',reason='timeout' WHERE id=1 AND status='sending'")
check(db.execute("SELECT status FROM send_results WHERE id=1").fetchone()[0] == "sent", "Timeout guard does not overwrite sent rows")
db.execute("UPDATE send_results SET status='pending' WHERE id=1 AND valid=1 AND status IN ('failed','unknown')")
check(db.execute("SELECT status FROM send_results WHERE id=1").fetchone()[0] == "sent", "Individual retry guard excludes sent rows")
db.execute("INSERT INTO send_parts VALUES(1,1,0,-1)")
db.execute("INSERT OR IGNORE INTO send_parts VALUES(1,1,0,-1)")
check(db.execute("SELECT count(*) FROM send_parts").fetchone()[0] == 1, "Part primary key deduplicates callbacks")
db.execute("INSERT INTO groups VALUES(1,'g','',1,0,0)")
db.execute("INSERT INTO members VALUES(1,1,'+989121234567','n',1)")
db.execute("INSERT OR IGNORE INTO members VALUES(2,1,'+989121234567','n',1)")
check(db.execute("SELECT count(*) FROM members").fetchone()[0] == 1, "Group member uniqueness constraint")
db.execute("DELETE FROM groups WHERE id=1")
check(db.execute("SELECT count(*) FROM members").fetchone()[0] == 0, "Member cascade on group deletion")
check(db.execute("SELECT count(*) FROM campaigns").fetchone()[0] == 1, "Group deletion leaves campaign history intact")
summary = {
    "static_checks": len(checks),
    "kotlin_files": len(list(root.rglob("*.kt"))),
    "string_resources": len(strings),
    "room_sql_statements": len(queries),
    "xml_files": len(list(root.rglob("*.xml"))),
    "compiled": False,
    "device_tested": False,
}
(root / "static-review-summary.json").write_text(json.dumps(summary, indent=2))
print(json.dumps(summary, indent=2))
