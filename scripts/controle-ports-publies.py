#!/usr/bin/env python3
"""Lot L0 (E16e) : controle des ports publies dans les fichiers compose.

Seuls les services appeles DIRECTEMENT par le navigateur restent publies sur
toutes les interfaces : passerelle, front, realtime (WebSocket,
VITE_REALTIME_URL), Collabora. Tout autre port publie doit etre lie a
127.0.0.1 (le test de fumee tourne sur la machine). Sortie non nulle sinon.

Usage : python3 scripts/controle-ports-publies.py [fichier.yml ...]
"""
import sys
from pathlib import Path

import yaml

LISTE_BLANCHE = {"gateway-service", "frontend", "realtime-service", "collabora"}
FICHIERS = ["docker-compose.yml", "docker-compose.services.yml", "docker-compose.local.yml"]


def ports_courts(ports):
    for p in ports:
        if isinstance(p, dict):
            yield p.get("host_ip"), str(p)
        else:
            yield (str(p).split(":")[0] if str(p).count(":") >= 2 else None), str(p)


def main(chemins):
    racine = Path(__file__).resolve().parent.parent / "infrastructure"
    chemins = [Path(c) for c in chemins] or [racine / f for f in FICHIERS]
    ecarts, vus = [], 0
    for f in chemins:
        services = (yaml.safe_load(f.read_text(encoding="utf-8")) or {}).get("services") or {}
        for nom, svc in services.items():
            for hote, brut in ports_courts((svc or {}).get("ports") or []):
                vus += 1
                if nom in LISTE_BLANCHE:
                    continue
                if hote != "127.0.0.1":
                    ecarts.append(f"{f.name}: {nom} publie {brut} hors 127.0.0.1")
    if vus == 0:
        print("aucun port lu : controle inoperant")
        return 2
    for e in ecarts:
        print(e)
    print(f"{vus} ports lus, {len(ecarts)} ecart(s)")
    return 1 if ecarts else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
