#!/usr/bin/env python3
"""Minimal webhook sink for the local and CI end-to-end flow.

Accepts any POST, answers 200, prints the payload size. Standard library only.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import sys


class Handler(BaseHTTPRequestHandler):
    def do_POST(self) -> None:  # noqa: N802 - stdlib naming
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length) if length else b""
        print(f"webhook received path={self.path} bytes={len(body)}", flush=True)
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"status":"ok"}')

    def log_message(self, *_args) -> None:
        return


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9999
    print(f"webhook receiver listening on {port}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
