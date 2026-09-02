"""Abstraction au-dessus des moteurs OCR (docTR + PaddleOCR).

L'API publique se résume à :

```
engine = create_engine(name='doctr')   # ou 'paddle'
engine.warmup()                        # charge les modèles, idempotent
result = engine.run(np_image_rgb, lang='latin')
# -> OcrResult(text, lines=[OcrLine(text, confidence, bbox)], confidence, ms)
```

Les deux moteurs renvoient le même contrat OcrResult. Le caller n'a pas à connaître la
distinction. Si le moteur demandé n'est pas installé / refuse de charger, le service
tente le fallback vers l'autre moteur AU BOOT (cf. ``app/main.py``), pas pendant
l'inférence.
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field
from typing import List, Optional, Sequence

import numpy as np

log = logging.getLogger(__name__)


@dataclass
class OcrLine:
    text: str
    confidence: float
    bbox: Optional[List[float]] = None  # [x1,y1,x2,y2] en pixels

    def to_dict(self) -> dict:
        return {
            "text": self.text,
            "confidence": round(self.confidence, 4),
            "bbox": self.bbox,
        }


@dataclass
class OcrResult:
    text: str
    lines: List[OcrLine] = field(default_factory=list)
    confidence: float = 0.0  # moyenne pondérée par longueur de texte
    ms: int = 0

    def to_dict(self) -> dict:
        return {
            "text": self.text,
            "lines": [line.to_dict() for line in self.lines],
            "confidence": round(self.confidence, 4),
            "ms": self.ms,
        }


# ---------------------------------------------------------------------------
# Interface
# ---------------------------------------------------------------------------


class OcrEngine:
    name: str = "abstract"

    def warmup(self) -> None:
        """Charge les modèles. Idempotent."""
        raise NotImplementedError

    def run(self, image_rgb: np.ndarray, lang: str = "latin") -> OcrResult:
        raise NotImplementedError


# ---------------------------------------------------------------------------
# docTR (Mindee) — moteur par défaut
# ---------------------------------------------------------------------------


class DocTrEngine(OcrEngine):
    """OCR via python-doctr backend torch CPU.

    docTR fonctionne en deux étapes (détection + reconnaissance) tout comme PaddleOCR.
    Le modèle ``ocr_predictor`` les enchaîne. On garde la version par défaut (db_resnet50
    + crnn_vgg16_bn) — bonne précision FR/latin avec un footprint ~200 MB.
    """

    name = "doctr"

    def __init__(self) -> None:
        self._predictor = None

    def warmup(self) -> None:
        if self._predictor is not None:
            return
        # Imports paresseux : le binaire torch coûte plusieurs secondes au démarrage.
        from doctr.models import ocr_predictor  # type: ignore

        log.info("docTR: chargement des modèles (db_resnet50 + crnn_vgg16_bn)")
        t0 = time.time()
        self._predictor = ocr_predictor(
            det_arch="db_resnet50",
            reco_arch="crnn_vgg16_bn",
            pretrained=True,
            assume_straight_pages=True,
        )
        log.info("docTR: modèles chargés en %.2fs", time.time() - t0)

    def run(self, image_rgb: np.ndarray, lang: str = "latin") -> OcrResult:
        if self._predictor is None:
            self.warmup()
        assert self._predictor is not None  # for mypy

        t0 = time.time()
        result = self._predictor([image_rgb])
        elapsed_ms = int((time.time() - t0) * 1000)

        lines: List[OcrLine] = []
        full_text_parts: List[str] = []
        # docTR retourne Document(pages=[Page(blocks=[Block(lines=[Line(words)])])])
        export = result.export()
        for page in export.get("pages", []):
            h, w = page.get("dimensions", [0, 0])
            for block in page.get("blocks", []):
                for ln in block.get("lines", []):
                    words = ln.get("words", [])
                    if not words:
                        continue
                    text = " ".join(w.get("value", "") for w in words if w.get("value"))
                    if not text.strip():
                        continue
                    confs = [float(w.get("confidence", 0.0)) for w in words]
                    line_conf = sum(confs) / len(confs) if confs else 0.0
                    geom = ln.get("geometry") or [[0, 0], [0, 0]]
                    # geometry = [[xmin,ymin],[xmax,ymax]] en coords normalisées 0..1
                    bbox = None
                    if w and h and geom and len(geom) == 2:
                        bbox = [
                            float(geom[0][0]) * w,
                            float(geom[0][1]) * h,
                            float(geom[1][0]) * w,
                            float(geom[1][1]) * h,
                        ]
                    lines.append(OcrLine(text=text, confidence=line_conf, bbox=bbox))
                    full_text_parts.append(text)
                # ligne vide entre blocks pour préserver la structure
                full_text_parts.append("")

        text = "\n".join(s for s in full_text_parts if s is not None).strip()
        confidence = _weighted_confidence(lines)
        return OcrResult(text=text, lines=lines, confidence=confidence, ms=elapsed_ms)


# ---------------------------------------------------------------------------
# PaddleOCR — moteur optionnel
# ---------------------------------------------------------------------------


class PaddleEngine(OcrEngine):
    """OCR via PaddleOCR (PP-OCRv4 mobile CPU).

    Modèles téléchargés au 1er run sous ~/.paddleocr/ — ne JAMAIS supposer connectivité
    réseau au runtime, prévoir un warmup au boot.
    """

    name = "paddle"

    def __init__(self, use_mkldnn: bool = True) -> None:
        self._engine = None
        self._use_mkldnn = use_mkldnn

    def warmup(self) -> None:
        if self._engine is not None:
            return
        from paddleocr import PaddleOCR  # type: ignore

        log.info("PaddleOCR: chargement PP-OCRv4 mobile CPU (mkldnn=%s)", self._use_mkldnn)
        t0 = time.time()
        # use_angle_cls=False : suppose CIN droites — gain ~20% latence.
        # rec_batch_num borné pour CPU.
        self._engine = PaddleOCR(
            use_angle_cls=False,
            lang="latin",  # même alphabet que fr/en — couvre nom/prenom CIN
            use_gpu=False,
            enable_mkldnn=self._use_mkldnn,
            show_log=False,
            rec_batch_num=4,
            det_model_dir=None,  # auto-DL
            rec_model_dir=None,  # auto-DL
        )
        log.info("PaddleOCR: modèles chargés en %.2fs", time.time() - t0)

    def run(self, image_rgb: np.ndarray, lang: str = "latin") -> OcrResult:
        if self._engine is None:
            self.warmup()
        assert self._engine is not None

        t0 = time.time()
        # PaddleOCR attend BGR ndarray (compatible OpenCV).
        bgr = image_rgb[..., ::-1]
        raw = self._engine.ocr(bgr, cls=False)
        elapsed_ms = int((time.time() - t0) * 1000)

        lines: List[OcrLine] = []
        text_parts: List[str] = []
        # raw = [[ [box, (text, conf)], [box, (text, conf)], ... ]]  (1 page)
        if raw and raw[0]:
            for entry in raw[0]:
                if not entry or len(entry) < 2:
                    continue
                box, rec = entry[0], entry[1]
                if not rec or len(rec) < 2:
                    continue
                t, c = rec[0], float(rec[1])
                if not t:
                    continue
                bbox = None
                if box and len(box) == 4:
                    xs = [float(p[0]) for p in box]
                    ys = [float(p[1]) for p in box]
                    bbox = [min(xs), min(ys), max(xs), max(ys)]
                lines.append(OcrLine(text=t, confidence=c, bbox=bbox))
                text_parts.append(t)

        text = "\n".join(text_parts)
        confidence = _weighted_confidence(lines)
        return OcrResult(text=text, lines=lines, confidence=confidence, ms=elapsed_ms)


# ---------------------------------------------------------------------------
# Factory + helpers
# ---------------------------------------------------------------------------


def create_engine(name: str) -> OcrEngine:
    n = (name or "").strip().lower()
    if n in ("doctr", "doctr-torch", "mindee"):
        return DocTrEngine()
    if n in ("paddle", "paddleocr", "pp"):
        return PaddleEngine()
    raise ValueError(f"OCR engine inconnu: {name!r} — attendu 'doctr' ou 'paddle'")


def _weighted_confidence(lines: Sequence[OcrLine]) -> float:
    """Moyenne des confidences pondérée par longueur de texte.

    Quelques caractères très confiants ne doivent pas dominer une longue ligne incertaine.
    """
    if not lines:
        return 0.0
    total_weight = 0.0
    total = 0.0
    for ln in lines:
        w = max(1, len(ln.text.strip()))
        total += float(ln.confidence) * w
        total_weight += w
    return total / total_weight if total_weight else 0.0
