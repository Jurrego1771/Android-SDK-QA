#!/usr/bin/env node
// =============================================================================
// Exploration runner (Fase 1) — el loop observar→actuar→detectar→registrar.
//
// Conduce el escenario vía el SERVIDOR MCP (no adb directo) para ejercitar el
// pipeline real, aplica los oráculos de business-rules.md sobre las observaciones
// y escribe un reporte + bundle de evidencia.
//
// Uso:  ANDROID_SERIAL=<serial> node explore.mjs <scenarioKey> <outDir>
//
// Esta es la variante DETERMINISTA (oráculos codificados) para CI/headless. La
// variante con agente LLM se conecta al mismo server vía .mcp.json (ver
// docs/testing/exploratory-ai.md) para exploración abierta.
// =============================================================================

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { writeFileSync, mkdirSync } from "node:fs";

const scenarioKey = process.argv[2] || "vod";
const outDir = process.argv[3] || join(process.cwd(), "exploration-out");
mkdirSync(outDir, { recursive: true });

const here = dirname(fileURLToPath(import.meta.url));
const transport = new StdioClientTransport({
  command: process.execPath,
  args: [join(here, "src", "server.mjs")],
  env: process.env,
});
const client = new Client({ name: "explore-runner", version: "0.1.0" });
await client.connect(transport);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const firstText = (res) => (res.content || []).find((c) => c.type === "text")?.text || "";
const firstImage = (res) => (res.content || []).find((c) => c.type === "image")?.data || null;
const callJson = async (name, args = {}) => {
  const t = firstText(await client.callTool({ name, arguments: args }));
  try { return JSON.parse(t); } catch { return { _raw: t }; }
};

const findings = [];
const record = (severity, title, detail) => findings.push({ severity, title, detail });

// ── 1. Actuar: lanzar escenario ───────────────────────────────────────────────
const nav = await callJson("navigate_deeplink", { key: scenarioKey });
if (nav.intentStatus !== "ok") record("high", "Deep link falló", JSON.stringify(nav));

// ── 2. Esperar carga y observar (vía el bridge — estado interno estructurado) ──
await sleep(8000);
const sess1 = await callJson("observe_session_state");
const logcat = firstText(await client.callTool({
  name: "observe_logcat", arguments: { tags: ["SDK_QA", "SDK_QA_FAIL", "AndroidRuntime", "SDK_QA_DEEPLINK"], maxLines: 200 },
}));
const crashes = firstText(await client.callTool({ name: "observe_crashes", arguments: { maxLines: 60 } }));
const shot = firstImage(await client.callTool({ name: "observe_screenshot", arguments: {} }));

// ── 3. Segunda lectura para confirmar que la posición avanza ───────────────────
await sleep(3000);
const sess2 = await callJson("observe_session_state");

// ── 4. Detectar: oráculos de business-rules.md sobre el estado del bridge ──────
const events = sess1.events || [];
const has = (e) => events.includes(e) || (sess2.events || []).includes(e);
const hasOnReady = has("onReady");
const hasOnPlay = has("onPlay");
const errorEvents = ["onError", "onPlaybackErrors", "onEmbedErrors"].filter(has);
const offMain = sess1.offMainThread || [];
const pos1 = sess1.player?.positionMs ?? null;
const pos2 = sess2.player?.positionMs ?? null;
const dur = sess2.player?.durationMs ?? sess1.player?.durationMs ?? 0;
const isLive = dur <= 0; // live = sin duración fija
const advancing = pos1 != null && pos2 != null && pos2 > pos1;
const crashed = /FATAL EXCEPTION|ANR in/.test(crashes);

if (!hasOnReady) record("high", "onReady no llegó", "Esperado < 15s VOD / 30s live (business-rules.md).");
if (errorEvents.length) record("high", "Callback de error en flujo válido", `Disparó: ${errorEvents.join(", ")}`);
if (crashed) record("critical", "Crash detectado", "observe_crashes reporta FATAL EXCEPTION/ANR.");
if (hasOnReady && !hasOnPlay) record("medium", "onPlay no llegó tras onReady", "autoplay=true debería disparar onPlay.");
if (offMain.length) record("medium", "Callbacks fuera del main thread", `Contrato de threading violado: ${offMain.join(", ")} (cf. CORE-DEF-005).`);
if (!isLive && !advancing) record("medium", "Posición no avanza", `positionMs ${pos1} → ${pos2}. El player podría estar congelado.`);

const verdict = findings.some((f) => f.severity === "critical" || f.severity === "high")
  ? "FAIL" : findings.length ? "WARN" : "PASS";

// ── 5. Registrar: artefactos + reporte ────────────────────────────────────────
if (shot) writeFileSync(join(outDir, "screenshot.png"), Buffer.from(shot, "base64"));
writeFileSync(join(outDir, "session-state.json"), JSON.stringify({ sess1, sess2 }, null, 2));
writeFileSync(join(outDir, "logcat.txt"), logcat);
writeFileSync(join(outDir, "crashes.txt"), crashes);

const posCell = isLive
  ? "n/a (live)"
  : advancing ? `✓ (${pos1} → ${pos2} ms)` : `✗ (${pos1} → ${pos2} ms)`;

const report = `# Reporte de exploración — escenario \`${scenarioKey}\`

- **Veredicto:** ${verdict}
- **Device:** ${process.env.ANDROID_SERIAL || "(auto)"}
- **Activity:** ${nav.resumedActivity}
- **Deep link:** sdkqa://scenario/${scenarioKey} → intent ${nav.intentStatus}
- **Callbacks (orden):** ${events.join(" → ") || "(ninguno)"}
- **Player:** pos ${pos2 ?? "?"}ms / dur ${dur}ms · playbackState ${sess2.player?.playbackState ?? "?"}

## Oráculos (business-rules.md, vía bridge)
| Invariante | Resultado |
|---|---|
| onReady llegó | ${hasOnReady ? "✓" : "✗"} |
| onPlay tras onReady | ${hasOnPlay ? "✓" : "✗"} |
| Sin callbacks de error | ${errorEvents.length ? "✗ " + errorEvents.join(",") : "✓"} |
| Callbacks en main thread | ${offMain.length ? "✗ " + offMain.join(",") : "✓"} |
| Sin crash | ${crashed ? "✗ (crash)" : "✓"} |
| Posición avanza | ${posCell} |

## Hallazgos
${findings.length ? findings.map((f) => `- **[${f.severity}]** ${f.title} — ${f.detail}`).join("\n") : "Sin hallazgos."}

## Evidencia
- screenshot.png
- session-state.json (estado del bridge, 2 lecturas)
- logcat.txt
- crashes.txt

_Generado por el harness de testing exploratorio (Fase 2 — oráculos vía bridge observe_session_state)._
`;
writeFileSync(join(outDir, "report.md"), report);

await client.close();
console.log(`VEREDICTO: ${verdict} — reporte en ${join(outDir, "report.md")}`);
process.exit(verdict === "FAIL" ? 1 : 0);
