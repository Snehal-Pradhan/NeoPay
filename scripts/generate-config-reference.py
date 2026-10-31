#!/usr/bin/env python3
"""Render docs/REFERENCE_CONFIGURATION.md from the config server YAML files."""
import pathlib
import yaml

ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG_DIR = ROOT / "config-service" / "src" / "main" / "resources" / "config"
OUT = ROOT / "docs" / "REFERENCE_CONFIGURATION.md"


def flatten(node, prefix=""):
    if isinstance(node, dict):
        for key, value in node.items():
            yield from flatten(value, f"{prefix}.{key}" if prefix else str(key))
    elif isinstance(node, list):
        yield prefix, ", ".join(str(item) for item in node)
    else:
        yield prefix, "" if node is None else str(node)


def profile_of(document):
    try:
        return document["spring"]["config"]["activate"]["on-profile"]
    except (KeyError, TypeError):
        return "default"


def render(path):
    documents = [doc for doc in yaml.safe_load_all(path.read_text()) if doc]
    lines = [f"## {path.stem}", ""]
    for document in documents:
        lines.append(f"### profile: `{profile_of(document)}`")
        lines.append("")
        lines.append("| Key | Value |")
        lines.append("|---|---|")
        for key, value in flatten(document):
            lines.append(f"| `{key}` | `{value}` |")
        lines.append("")
    return "\n".join(lines)


def main():
    parts = [
        "# Configuration reference",
        "",
        "Generated from `config-service/src/main/resources/config/*.yml` by",
        "`scripts/generate-config-reference.py`. Do not edit by hand.",
        "",
        "Values written as `${NAME:default}` resolve from the environment with `default`",
        "when the variable is absent. A blank value means the key is optional or supplied",
        "at runtime.",
        "",
    ]
    for path in sorted(CONFIG_DIR.glob("*.yml")) + sorted(CONFIG_DIR.glob("*.yaml")):
        parts.append(render(path))
    OUT.write_text("\n".join(parts).rstrip() + "\n")
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
