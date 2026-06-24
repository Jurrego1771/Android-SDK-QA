// Helpers adb para el servidor MCP exploratorio.
// El device objetivo viene de ANDROID_SERIAL (lo fija scripts/explore.sh).
// Todo es stateless: cada llamada es un round-trip adb (escalable, sin estado en el server).

import { execFile } from "node:child_process";

const SERIAL = process.env.ANDROID_SERIAL || "";

/** Prefijo -s <serial> si hay ANDROID_SERIAL; si no, adb elige el único device. */
function serialArgs() {
  return SERIAL ? ["-s", SERIAL] : [];
}

/**
 * Ejecuta adb con args. Devuelve { stdout, stderr, code }.
 * encoding "buffer" para soportar binario (screencap). Para texto, usar adbText().
 */
export function adb(args, { timeoutMs = 30_000, encoding = "utf8" } = {}) {
  return new Promise((resolve) => {
    execFile(
      "adb",
      [...serialArgs(), ...args],
      { timeout: timeoutMs, encoding, maxBuffer: 64 * 1024 * 1024 },
      (err, stdout, stderr) => {
        resolve({
          stdout: stdout ?? (encoding === "buffer" ? Buffer.alloc(0) : ""),
          stderr: (stderr ?? "").toString(),
          code: err?.code ?? 0,
          error: err ? String(err.message || err) : null,
        });
      }
    );
  });
}

export async function adbText(args, opts = {}) {
  const r = await adb(args, { ...opts, encoding: "utf8" });
  return r;
}

export async function adbBinary(args, opts = {}) {
  const r = await adb(args, { ...opts, encoding: "buffer" });
  return r;
}

export function targetSerial() {
  return SERIAL || "(auto)";
}
