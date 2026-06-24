# Addon de mitmproxy para el harness exploratorio.
# Escribe cada flow (response) como una línea JSON en $MITM_FLOWS, para que las
# tools observe_network / observe_analytics lo lean y filtren.
#
# Uso (ver docs/testing/exploratory-ai.md):
#   set MITM_FLOWS=D:\repos\...\ai-output\mitm-flows.jsonl
#   mitmdump --listen-port 8080 -s tools/exploratory-mcp/mitm_addon.py
#
# El device debe tener el proxy apuntando al PC:8080 y el CA de mitmproxy instalado
# (network_security_config ya confía en CA de usuario en debug). Reusa el flujo de
# la skill android-network-proxy, cambiando mitmweb por mitmdump.

import json
import os

OUT = os.environ.get("MITM_FLOWS", "mitm-flows.jsonl")


def response(flow):
    _write(flow)


def error(flow):
    _write(flow)


def _write(flow):
    try:
        resp = getattr(flow, "response", None)
        rec = {
            "ts": flow.request.timestamp_start,
            "method": flow.request.method,
            "url": flow.request.pretty_url,
            "host": flow.request.host,
            "path": flow.request.path,
            "status": resp.status_code if resp else None,
            "reqContentType": flow.request.headers.get("content-type", ""),
            "respContentType": resp.headers.get("content-type", "") if resp else "",
            "respLen": len(resp.raw_content or b"") if resp and resp.raw_content else 0,
        }
        with open(OUT, "a", encoding="utf-8") as f:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    except Exception:
        pass
