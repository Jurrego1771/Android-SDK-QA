#!/usr/bin/env node
// Cliente MCP de verificación: arranca el server por stdio, lista las tools y
// llama a cada una contra el device (ANDROID_SERIAL). Imprime un resumen.
// No es parte del runtime — solo prueba que el server habla MCP correctamente.

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const serverPath = join(here, "src", "server.mjs");

const transport = new StdioClientTransport({
  command: process.execPath,
  args: [serverPath],
  env: process.env, // hereda ANDROID_SERIAL
});

const client = new Client({ name: "test-client", version: "0.1.0" });
await client.connect(transport);

const { tools } = await client.listTools();
console.log("TOOLS:", tools.map((t) => t.name).join(", "));

function summarize(label, res) {
  const parts = (res.content || []).map((c) => {
    if (c.type === "text") return c.text.length > 400 ? c.text.slice(0, 400) + "…" : c.text;
    if (c.type === "image") return `<image ${c.mimeType} b64:${c.data.length} chars>`;
    return `<${c.type}>`;
  });
  console.log(`\n=== ${label} ===\n${parts.join("\n")}`);
}

// 1) navegar a VOD
summarize("navigate_deeplink(vod)", await client.callTool({ name: "navigate_deeplink", arguments: { key: "vod" } }));

// dar tiempo a que cargue
await new Promise((r) => setTimeout(r, 6000));

// 2) screenshot
summarize("observe_screenshot", await client.callTool({ name: "observe_screenshot", arguments: {} }));

// 3) jerarquía UI
summarize("observe_ui_hierarchy", await client.callTool({ name: "observe_ui_hierarchy", arguments: {} }));

// 4) logcat
summarize("observe_logcat", await client.callTool({ name: "observe_logcat", arguments: { tags: ["SDK_QA", "SDK_QA_DEEPLINK"], maxLines: 40 } }));

// 5) estado de sesión (bridge, F2)
summarize("observe_session_state", await client.callTool({ name: "observe_session_state", arguments: {} }));

// 6) crashes (F2)
summarize("observe_crashes", await client.callTool({ name: "observe_crashes", arguments: { maxLines: 20 } }));

// 7) network (F2) — requiere MITM_FLOWS
summarize("observe_network", await client.callTool({ name: "observe_network", arguments: { hostContains: "mdstrm" } }));

// 8) analytics (F2)
summarize("observe_analytics", await client.callTool({ name: "observe_analytics", arguments: {} }));

await client.close();
console.log("\nOK — el server respondió a las 8 tools.");
