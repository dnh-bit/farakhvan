// TypeScript port of farakhvan-android/.../util/PhoneNormalizer.kt (used for the live demo).

export interface PhoneNumber {
  canonical: string;
  valid: boolean;
  raw: string;
}

export interface ParsedList {
  numbers: PhoneNumber[];
  duplicates: number;
}

const SEPARATORS = /[\s\-()/\u200B-\u200F\u202A-\u202E\u2066-\u2069\uFEFF]/g;
const CHUNK_SPLIT = /[\n\r,;\u060C\u061B]+/;

export function toLatinDigits(input: string): string {
  return input
    .replace(/[\u06F0-\u06F9]/g, (c) => String(c.charCodeAt(0) - 0x06f0))
    .replace(/[\u0660-\u0669]/g, (c) => String(c.charCodeAt(0) - 0x0660));
}

export function normalize(raw: string): PhoneNumber {
  const clean = toLatinDigits(raw).replace(SEPARATORS, "");
  if (!clean) return { canonical: "", valid: false, raw };

  const hasPlus = clean.startsWith("+");
  const body = hasPlus ? clean.slice(1) : clean;
  if (!body || /[^0-9]/.test(body)) return { canonical: clean, valid: false, raw };

  let intl: string | null = null;
  if (hasPlus) intl = body;
  else if (body.startsWith("00")) intl = body.slice(2);

  if (intl !== null) {
    if (intl.startsWith("98")) {
      let national = intl.slice(2);
      if (national.length === 11 && national.startsWith("0")) national = national.slice(1);
      return { canonical: "+98" + national, valid: national.length === 10 && national.startsWith("9"), raw };
    }
    return { canonical: "+" + intl, valid: intl.length >= 8 && intl.length <= 15 && !intl.startsWith("0"), raw };
  }

  if (body.length === 12 && body.startsWith("989")) return { canonical: "+" + body, valid: true, raw };
  if (body.length === 11 && body.startsWith("09")) return { canonical: "+98" + body.slice(1), valid: true, raw };
  if (body.length === 10 && body.startsWith("9")) return { canonical: "+98" + body, valid: true, raw };
  return { canonical: body, valid: false, raw };
}

export function parseBulk(text: string): ParsedList {
  const out = new Map<string, PhoneNumber>();
  let duplicates = 0;
  const add = (p: PhoneNumber) => {
    if (!p.canonical) return;
    if (out.has(p.canonical)) duplicates++;
    else out.set(p.canonical, p);
  };

  for (const chunk of text.split(CHUNK_SPLIT)) {
    const trimmed = chunk.trim();
    if (!trimmed) continue;
    const tokens = trimmed.split(/\s+/).filter(Boolean);
    if (tokens.length === 1) {
      add(normalize(tokens[0]));
      continue;
    }
    let acc = "";
    for (const t of tokens) {
      acc += t;
      const candidate = normalize(acc);
      if (candidate.valid) {
        add(candidate);
        acc = "";
      }
    }
    if (acc) add(normalize(acc));
  }
  return { numbers: [...out.values()], duplicates };
}
