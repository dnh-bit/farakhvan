// Approximation of the platform's SmsMessage.calculateLength (GSM-7 vs UCS-2) for the demo.

const GSM_BASIC =
  "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001BÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
const GSM_EXT = "^{}\\[~]|€\f";

export interface SmsInfo {
  parts: number;
  used: number;
  perPart: number;
  unicode: boolean;
}

export function analyze(text: string): SmsInfo {
  if (!text) return { parts: 0, used: 0, perPart: 160, unicode: false };

  let septets = 0;
  let gsm = true;
  for (const ch of text) {
    if (GSM_BASIC.includes(ch)) septets += 1;
    else if (GSM_EXT.includes(ch)) septets += 2;
    else {
      gsm = false;
      break;
    }
  }

  if (gsm) {
    const parts = septets <= 160 ? 1 : Math.ceil(septets / 153);
    return { parts, used: septets, perPart: parts === 1 ? 160 : 153 * parts, unicode: false };
  }
  const units = text.length; // UTF-16 code units, like the platform
  const parts = units <= 70 ? 1 : Math.ceil(units / 67);
  return { parts, used: units, perPart: parts === 1 ? 70 : 67 * parts, unicode: true };
}
