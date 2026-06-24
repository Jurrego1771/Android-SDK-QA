#!/usr/bin/env node
// =============================================================================
// Servidor MCP custom — Testing exploratorio asistido por IA del SDK Mediastream
// Fase 1 (PoC): 4 tools resueltas con adb puro. Appium/UiAutomator2 se enchufa
// en F2 para tap-por-selector / gestos / a11y tree.
//
// Tools:
//   navigate_deeplink   — lanza un escenario vía sdkqa://scenario/<key>
//   observe_screenshot  — captura PNG de la pantalla actual
//   observe_ui_hierarchy— dump de la jerarquía de vistas (uiautomator)
//   observe_logcat      — slice de logcat por tags del SDK QA
//
// Device objetivo: ANDROID_SERIAL (lo fija scripts/explore.sh).
// Ver docs/testing/exploratory-ai.md
// =============================================================================

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { readFileSync } from "node:fs";
import { z } from "zod";
import { adbBinary, adbText, targetSerial } from "./adb.mjs";

// Archivo JSONL que escribe mitm_addon.py (un flow por línea). Lo fija explore.sh.
const MITM_FLOWS = process.env.MITM_FLOWS || "";

// Hosts considerados "analytics/ads" para observe_analytics.
const ANALYTICS_HOSTS = {
  mediastream: [/mdstrm\.com/i, /mediastre\.am/i],
  comscore: [/scorecardresearch\.com/i, /comscore/i],
  youbora: [/nice264\.com/i, /youbora/i, /npaw/i],
  ads: [/doubleclick\.net/i, /imasdk\.googleapis\.com/i, /googlesyndication\.com/i, /pubads/i, /googleads/i],
};

function readFlows() {
  if (!MITM_FLOWS) return { error: "MITM_FLOWS no configurado (arranca mitmdump vía explore.sh --proxy)." };
  try {
    const lines = readFileSync(MITM_FLOWS, "utf8").split(/\r?\n/).filter((l) => l.trim());
    return { flows: lines.map((l) => { try { return JSON.parse(l); } catch { return null; } }).filter(Boolean) };
  } catch (e) {
    return { error: `No se pudo leer ${MITM_FLOWS}: ${e.message}. ¿mitmdump corriendo + device proxied?` };
  }
}

// Claves de deep link válidas (debe coincidir con DeepLinkRouterActivity.SCENARIOS).
const SCENARIO_KEYS = [
  "vod", "live", "livedvr", "lived", "episode", "episode-custom", "ads", "drm",
  "pip", "reels", "switcher", "subtitles", "fullscreen", "service",
  "audio-live", "audio-vod", "audio-episode", "audio-service", "direct-hls",
];

// Tags de logcat que el repo ya emite (BaseScenarioActivity, SdkEvidenceRule, router).
const DEFAULT_TAGS = ["SDK_QA", "SDK_QA_FAIL", "SDK_QA_EVIDENCE", "SDK_QA_DEEPLINK", "AndroidRuntime", "StrictMode"];

function text(s) {
  return { content: [{ type: "text", text: s }] };
}

const server = new McpServer({ name: "sdk-qa-exploratory", version: "0.1.0" });

// ── navigate_deeplink ────────────────────────────────────────────────────────
server.registerTool(
  "navigate_deeplink",
  {
    title: "Lanzar escenario por deep link",
    description:
      "Lanza un escenario del SDK de forma determinista vía sdkqa://scenario/<key>. " +
      "Devuelve el estado del intent y la Activity que quedó en foreground. " +
      "Claves válidas: " + SCENARIO_KEYS.join(", "),
    inputSchema: { key: z.enum(SCENARIO_KEYS).describe("Clave del escenario, ej. 'vod'") },
  },
  async ({ key }) => {
    const start = await adbText([
      "shell", "am", "start", "-W",
      "-a", "android.intent.action.VIEW",
      "-d", `sdkqa://scenario/${key}`,
    ]);
    // Confirmar Activity en foreground
    const top = await adbText(["shell", "dumpsys", "activity", "activities"]);
    const m = top.stdout.match(/topResumedActivity=ActivityRecord\{[^ ]+ \w+ ([^ }]+)/);
    const resumed = m ? m[1] : "(desconocida)";
    return text(JSON.stringify({
      key,
      intentStatus: /Status:\s*ok/i.test(start.stdout) ? "ok" : "error",
      resumedActivity: resumed,
      raw: start.stdout.trim().split(/\r?\n/).slice(0, 6),
    }, null, 2));
  }
);

// ── tap ──────────────────────────────────────────────────────────────────────
server.registerTool(
  "tap",
  {
    title: "Tap en coordenadas",
    description:
      "Toca la pantalla en (x, y) absolutos (adb input tap). Para tocar un elemento, " +
      "primero obtené sus bounds con observe_ui_hierarchy y usá el centro. " +
      "En F2 esto se reemplaza por tap-por-selector vía Appium/UiAutomator2.",
    inputSchema: {
      x: z.number().int().nonnegative().describe("Coordenada X absoluta"),
      y: z.number().int().nonnegative().describe("Coordenada Y absoluta"),
    },
  },
  async ({ x, y }) => {
    const r = await adbText(["shell", "input", "tap", String(x), String(y)]);
    return text(r.code === 0 ? `tap (${x},${y}) ok` : `ERROR tap: ${r.error || r.stderr}`);
  }
);

// ── observe_screenshot ───────────────────────────────────────────────────────
server.registerTool(
  "observe_screenshot",
  {
    title: "Screenshot actual",
    description: "Captura la pantalla actual del device (PNG) y la devuelve como imagen.",
    inputSchema: {},
  },
  async () => {
    const r = await adbBinary(["exec-out", "screencap", "-p"]);
    if (!r.stdout || r.stdout.length === 0) {
      return text(`ERROR: screencap vacío. ${r.error || r.stderr}`);
    }
    return {
      content: [
        { type: "text", text: `screenshot ${r.stdout.length} bytes de ${targetSerial()}` },
        { type: "image", data: r.stdout.toString("base64"), mimeType: "image/png" },
      ],
    };
  }
);

// ── observe_ui_hierarchy ─────────────────────────────────────────────────────
server.registerTool(
  "observe_ui_hierarchy",
  {
    title: "Jerarquía UI Android",
    description:
      "Dump de la jerarquía de vistas en pantalla (uiautomator). XML con resource-ids, " +
      "texto, bounds y content-desc — útil para localizar tv_status, recycler_log, etc.",
    inputSchema: {},
  },
  async () => {
    const dump = await adbText(["shell", "uiautomator", "dump", "/sdcard/sdkqa_uihier.xml"]);
    if (/ERROR|could not/i.test(dump.stdout) && !/dumped/i.test(dump.stdout)) {
      return text(`ERROR uiautomator dump: ${dump.stdout || dump.stderr}`);
    }
    const xml = await adbText(["shell", "cat", "/sdcard/sdkqa_uihier.xml"]);
    return text(xml.stdout || "(jerarquía vacía)");
  }
);

// ── observe_logcat ───────────────────────────────────────────────────────────
server.registerTool(
  "observe_logcat",
  {
    title: "Slice de logcat",
    description:
      "Devuelve líneas recientes de logcat filtradas por tags del SDK QA. " +
      "Por defecto los tags del repo (SDK_QA, SDK_QA_FAIL, AndroidRuntime, ...). " +
      "Usar 'sinceTime' (formato 'MM-DD HH:MM:SS.mmm') para acotar desde un marcador.",
    inputSchema: {
      tags: z.array(z.string()).optional().describe("Tags a incluir; default = tags del repo"),
      sinceTime: z.string().optional().describe("logcat -T, ej. '06-17 19:40:00.000'"),
      maxLines: z.number().int().positive().max(2000).optional().describe("Máx líneas (default 300)"),
    },
  },
  async ({ tags, sinceTime, maxLines }) => {
    const useTags = (tags && tags.length ? tags : DEFAULT_TAGS).map((t) => `${t}:*`);
    const args = ["logcat", "-d", "-v", "time"];
    if (sinceTime) args.push("-T", sinceTime);
    args.push(...useTags, "*:S");
    const r = await adbText(args, { timeoutMs: 20_000 });
    let lines = (r.stdout || "").split(/\r?\n/).filter((l) => l.trim());
    const limit = maxLines || 300;
    if (lines.length > limit) lines = lines.slice(-limit);
    return text(lines.length ? lines.join("\n") : "(sin entradas para esos tags)");
  }
);

// ── observe_session_state ────────────────────────────────────────────────────
server.registerTool(
  "observe_session_state",
  {
    title: "Estado de sesión del SDK",
    description:
      "Estado interno del player (que normalmente solo ve un test in-process): " +
      "callbacks en orden de llegada, callbacks fuera del main thread, player state " +
      "(positionMs, durationMs, isPlaying, playbackState) y config (id/type/env). " +
      "Vía el ContentProvider debug. Sin secretos.",
    inputSchema: {},
  },
  async () => {
    const r = await adbText([
      "shell", "content", "query",
      "--uri", "content://com.example.sdk_qa.debug.observability/session",
    ]);
    const out = (r.stdout || "").trim();
    if (/SecurityException|Error while accessing/i.test(out)) {
      return text(`ERROR: bridge inaccesible. ¿Debug build instalado? Detalle:\n${out}`);
    }
    // Formato: "Row: 0 json={...}"
    const m = out.match(/json=(\{[\s\S]*\})\s*$/);
    if (!m) return text(out || "(sin fila — ¿hay un escenario en foreground?)");
    try {
      return text(JSON.stringify(JSON.parse(m[1]), null, 2));
    } catch {
      return text(m[1]);
    }
  }
);

// ── observe_crashes ──────────────────────────────────────────────────────────
server.registerTool(
  "observe_crashes",
  {
    title: "Crashes recientes",
    description:
      "Lee el buffer 'crash' de logcat (FATAL EXCEPTION / ANR / native). Devuelve los " +
      "bloques de crash recientes o 'sin crashes'. Complementa LeakCanary (fugas).",
    inputSchema: {
      maxLines: z.number().int().positive().max(1000).optional().describe("Máx líneas (default 200)"),
    },
  },
  async ({ maxLines }) => {
    const r = await adbText(["logcat", "-b", "crash", "-d", "-v", "time"], { timeoutMs: 15_000 });
    let lines = (r.stdout || "").split(/\r?\n/).filter((l) => l.trim() && !l.startsWith("---"));
    if (!lines.length) return text("✓ sin crashes en el buffer 'crash'.");
    const limit = maxLines || 200;
    if (lines.length > limit) lines = lines.slice(-limit);
    const fatals = lines.filter((l) => /FATAL EXCEPTION|ANR in|signal \d+/i.test(l)).length;
    return text(`⚠ ${fatals} marcador(es) de crash. Buffer:\n${lines.join("\n")}`);
  }
);

// ── observe_network ──────────────────────────────────────────────────────────
server.registerTool(
  "observe_network",
  {
    title: "Peticiones de red",
    description:
      "Flows HTTPS capturados por mitmdump (método, url, host, status, content-type, tamaño). " +
      "Incluye llamadas de IMA/ads que el Network Inspector de Studio no ve. " +
      "Requiere proxy + CA (ver docs). Filtro opcional por host/método.",
    inputSchema: {
      hostContains: z.string().optional().describe("Filtra por substring de host/url"),
      method: z.string().optional().describe("Filtra por método HTTP"),
      maxFlows: z.number().int().positive().max(500).optional().describe("Máx flows (default 100)"),
    },
  },
  async ({ hostContains, method, maxFlows }) => {
    const { flows, error } = readFlows();
    if (error) return text(`ERROR: ${error}`);
    let f = flows;
    if (hostContains) f = f.filter((x) => (x.url || x.host || "").toLowerCase().includes(hostContains.toLowerCase()));
    if (method) f = f.filter((x) => (x.method || "").toUpperCase() === method.toUpperCase());
    const limit = maxFlows || 100;
    const sliced = f.slice(-limit);
    return text(JSON.stringify({ total: f.length, shown: sliced.length, flows: sliced }, null, 2));
  }
);

// ── observe_analytics ────────────────────────────────────────────────────────
server.registerTool(
  "observe_analytics",
  {
    title: "Eventos de analytics y ads",
    description:
      "Subconjunto de la red clasificado por proveedor: Mediastream, Comscore, Youbora y " +
      "beacons de ads (DoubleClick/IMA). Útil para verificar que el SDK reporta analytics y " +
      "que se disparan los beacons VAST (impression/start/quartiles/complete).",
    inputSchema: {
      provider: z.enum(["mediastream", "comscore", "youbora", "ads", "all"]).optional()
        .describe("Filtra por proveedor (default all)"),
    },
  },
  async ({ provider }) => {
    const { flows, error } = readFlows();
    if (error) return text(`ERROR: ${error}`);
    const want = provider && provider !== "all" ? [provider] : Object.keys(ANALYTICS_HOSTS);
    const result = {};
    for (const key of want) {
      const pats = ANALYTICS_HOSTS[key];
      result[key] = flows
        .filter((x) => pats.some((re) => re.test(x.url || "") || re.test(x.host || "")))
        .map((x) => ({ method: x.method, status: x.status, url: x.url }));
    }
    const counts = Object.fromEntries(Object.entries(result).map(([k, v]) => [k, v.length]));
    return text(JSON.stringify({ counts, events: result }, null, 2));
  }
);

const transport = new StdioServerTransport();
await server.connect(transport);
// stderr no interfiere con el transporte stdio (que usa stdout)
process.stderr.write(`[sdk-qa-exploratory-mcp] listo. device=${targetSerial()}\n`);
