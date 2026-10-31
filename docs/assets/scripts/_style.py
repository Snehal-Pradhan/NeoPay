"""Shared diagram style: small icons, tight spacing, thin grey edges."""
import os

from PIL import Image
from diagrams import Node

ICON_PX = 56
CACHE = os.path.join(os.path.dirname(__file__), ".icon-cache")

GRAPH_ATTR = {
    "fontname": "Helvetica",
    "fontsize": "12",
    "dpi": "170",
    "nodesep": "0.35",
    "ranksep": "0.55",
    "pad": "0.2",
    "splines": "polyline",
    "bgcolor": "white",
}

NODE_ATTR = {
    "fontname": "Helvetica",
    "fontsize": "9",
    "margin": "0.02",
}

EDGE_ATTR = {
    "fontname": "Helvetica",
    "fontsize": "8",
    "color": "gray40",
    "penwidth": "1.0",
    "arrowsize": "0.7",
}

_original_load_icon = Node._load_icon


def _small_icon(node):
    path = _original_load_icon(node)
    try:
        os.makedirs(CACHE, exist_ok=True)
        out = os.path.join(CACHE, f"{ICON_PX}-{os.path.basename(path)}")
        if not os.path.exists(out):
            image = Image.open(path).convert("RGBA")
            image.thumbnail((ICON_PX, ICON_PX), Image.LANCZOS)
            canvas = Image.new("RGBA", (ICON_PX, ICON_PX), (255, 255, 255, 0))
            canvas.paste(image, ((ICON_PX - image.width) // 2,
                                 (ICON_PX - image.height) // 2), image)
            canvas.save(out)
        return out
    except Exception:
        return path


Node._load_icon = _small_icon
Node._height = 1.45


def node(kind, label, height="1.6"):
    """Icon node with a short label rendered under a proportional icon."""
    return kind(label, height=height)
