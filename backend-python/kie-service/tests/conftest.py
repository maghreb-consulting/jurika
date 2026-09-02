"""Configuration commune des tests."""

from __future__ import annotations

import os
import sys
from pathlib import Path

# On désactive le préchargement Donut au boot pour les tests.
os.environ.setdefault("KIE_PRELOAD", "false")
os.environ.setdefault("DONUT_MODEL_DIR", "/nonexistent/donut-test")

# Ajoute le répertoire parent (kie-service/) au PYTHONPATH pour que
# `import app` fonctionne sans installer le package.
_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))
