#!/usr/bin/env python3
"""Gera app/src/main/assets/brasil.bin: estados e municípios reais com contorno e dados.

Fonte: a base do Retrato do Brasil (dmwnezes.github.io/estatisticas), que já reúne
as malhas do IBGE (TopoJSON) e os indicadores de IBGE, PNUD, Ipea e TSE.

Uso: python3 tools/build_brasil.py CAMINHO/estatisticas
Requer: shapely
"""
import json
import math
import struct
import sys
from pathlib import Path

from shapely.geometry import MultiPolygon, Polygon
from shapely.ops import unary_union

# campos guardados, na ordem que o app lê (Brasil.kt: Stat)
STATS = ["pop", "pib", "pib_pc", "idh", "renda", "alfab", "agua", "esgoto", "homic", "eleitores", "area", "dens", "idade_med"]

CAPITAIS = {
    2800308, 1501402, 3106200, 1400100, 5300108, 5002704, 5103403, 4106902, 4205407, 2304400, 5208707, 2507507, 1600303, 2704302,
    1302603, 2408102, 1721000, 4314902, 1100205, 2611606, 1200401, 3304557, 2927408, 2111300, 3550308, 2211001, 3205309,
}

TOL_MUN = 0.0006   # ~65 m: o mapa da cidade é visto de perto
TOL_UF = 0.006
TOL_BR = 0.03


def decode_topo(path):
    """TopoJSON → lista de (codarea, geometria shapely)."""
    t = json.loads(Path(path).read_text())
    sx, sy = t["transform"]["scale"]
    tx, ty = t["transform"]["translate"]
    arcs = []
    for arc in t["arcs"]:
        x = y = 0
        pts = []
        for dx, dy in arc:
            x += dx
            y += dy
            pts.append((x * sx + tx, y * sy + ty))
        arcs.append(pts)

    def ring(idx):
        out = []
        for i in idx:
            a = arcs[i] if i >= 0 else arcs[~i][::-1]
            out.extend(a if not out else a[1:])
        return out

    res = []
    obj = next(iter(t["objects"].values()))
    for g in obj["geometries"]:
        code = g["properties"]["codarea"]
        if g["type"] == "Polygon":
            polys = [g["arcs"]]
        elif g["type"] == "MultiPolygon":
            polys = g["arcs"]
        else:
            continue
        shapes = []
        for p in polys:
            rings = [ring(r) for r in p]
            if len(rings[0]) >= 4:
                shapes.append(Polygon(rings[0], [r for r in rings[1:] if len(r) >= 4]))
        geom = MultiPolygon(shapes) if len(shapes) > 1 else shapes[0]
        res.append((code, geom.buffer(0)))
    return res


def outer_rings(geom, tol):
    """Só os anéis externos (sem buracos), simplificados; ilhas minúsculas somem."""
    g = geom.simplify(tol, preserve_topology=True)
    polys = list(g.geoms) if hasattr(g, "geoms") else [g]
    big = max(p.area for p in polys)
    rings = []
    for p in sorted(polys, key=lambda p: -p.area):
        if p.area < big * 0.002 and len(rings) > 0:
            continue
        coords = list(p.exterior.coords)[:-1]
        if len(coords) >= 3:
            rings.append(coords)
    return rings


def write_geom(out, rings):
    out.write(struct.pack(">H", len(rings)))
    for r in rings:
        out.write(struct.pack(">I", len(r)))
        out.write(b"".join(struct.pack(">ff", x, y) for x, y in r))


def utf(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def stats_of(campos, v):
    vals = []
    for k in STATS:
        x = v[campos.index(k)]
        vals.append(float("nan") if x is None else float(x))
    return struct.pack(">" + "f" * len(STATS), *vals)


def main():
    base = Path(sys.argv[1])
    geral = json.loads((base / "data/geral.json").read_text())
    mun = json.loads((base / "data/municipios.json").read_text())
    uf_geo = dict(decode_topo(base / "geo/uf.json"))

    mun_geo = {}
    for f in sorted((base / "geo/mun").glob("*.json")):
        for code, g in decode_topo(f):
            mun_geo[int(code)] = g

    out = bytearray()

    class W:
        def write(self, b):
            out.extend(b)

    w = W()
    w.write(b"PLQ1")
    w.write(struct.pack(">B", len(STATS)))

    ufs = sorted(k for k in uf_geo)
    w.write(struct.pack(">H", len(ufs)))
    for code in ufs:
        loc = geral["locais"][code]
        w.write(struct.pack(">B", int(code)))
        w.write(utf(loc["sigla"]))
        w.write(utf(loc["nome"]))
        w.write(stats_of(geral["campos"], loc["v"]))
        write_geom(w, outer_rings(uf_geo[code], TOL_UF))

    # contorno do Brasil (fundo da tela inicial)
    br = unary_union(list(uf_geo.values()))
    write_geom(w, outer_rings(br, TOL_BR))

    items = [(c, n, v) for c, n, v in zip(mun["ids"], mun["nomes"], mun["v"]) if c in mun_geo]
    w.write(struct.pack(">H", len(items)))
    npts = 0
    for code, nome, v in items:
        rings = outer_rings(mun_geo[code], TOL_MUN)
        npts += sum(len(r) for r in rings)
        w.write(struct.pack(">IBB", code, code // 100000, 1 if code in CAPITAIS else 0))
        w.write(utf(nome))
        w.write(stats_of(mun["campos"], v))
        write_geom(w, rings)

    dest = Path(__file__).resolve().parent.parent / "app/src/main/assets/brasil.bin"
    dest.write_bytes(bytes(out))
    print(f"{len(ufs)} estados, {len(items)} municípios, {npts} pontos, {len(out) / 1e6:.1f} MB → {dest}")
    missing = [n for c, n, _ in zip(mun["ids"], mun["nomes"], mun["v"]) if c not in mun_geo]
    if missing:
        print("sem contorno:", missing)


if __name__ == "__main__":
    main()
