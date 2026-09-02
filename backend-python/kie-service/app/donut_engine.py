"""Wrapper Donut (VisionEncoderDecoderModel) — singleton CPU.

Important : on FORCE le backend torch AVANT l'import de transformers, sinon
transformers tente de charger TF/JAX en plus et alourdit le démarrage.
"""

from __future__ import annotations

import logging
import os

os.environ["USE_TF"] = "0"
os.environ["USE_JAX"] = "0"
os.environ["USE_TORCH"] = "1"

from typing import Any

from PIL import Image

from .clean import clean_fields

log = logging.getLogger("kie-service.donut")

# Prompt task et début du wrapper structuré utilisé à l'entraînement.
TASK = "<s_jurika>"


class DonutEngine:
    """Wrapper singleton. Charge processor + model une seule fois."""

    def __init__(self, model_dir: str) -> None:
        self.model_dir = model_dir
        self.processor: Any = None
        self.model: Any = None
        self.device: str = "cpu"
        self._loaded = False

    @property
    def loaded(self) -> bool:
        return self._loaded

    def load(self) -> None:
        if self._loaded:
            return
        # Imports lazy : permettent au service de boot même si transformers
        # n'est pas installé (utile pour les tests qui mockent infer).
        import torch  # noqa: WPS433
        from transformers import (  # noqa: WPS433
            DonutProcessor,
            VisionEncoderDecoderModel,
        )

        log.info("Chargement Donut depuis %s …", self.model_dir)
        self.processor = DonutProcessor.from_pretrained(self.model_dir)
        # Image processor : taille fixée à l'entraînement.
        self.processor.image_processor.size = {"height": 768, "width": 576}

        self.model = VisionEncoderDecoderModel.from_pretrained(self.model_dir)
        self.model.eval()
        self.model.to(self.device)
        # Garde une réf à torch pour infer().
        self._torch = torch
        self._loaded = True
        log.info("Donut chargé. device=%s", self.device)

    def infer(self, image: Image.Image, doc_type: str) -> dict[str, Any]:
        """Inférence Donut conditionnée par doc_type.

        Renvoie un dict de champs. En cas d'échec de parsing JSON Donut,
        retourne un dict vide.
        """
        if not self._loaded:
            self.load()

        torch = self._torch
        proc = self.processor
        model = self.model

        prompt = f"{TASK}<s_doc_type>{doc_type}</s_doc_type>"

        pixel_values = proc(image, return_tensors="pt").pixel_values.to(self.device)
        decoder_input_ids = proc.tokenizer(
            prompt, add_special_tokens=False, return_tensors="pt"
        ).input_ids.to(self.device)

        with torch.no_grad():
            outputs = model.generate(
                pixel_values,
                decoder_input_ids=decoder_input_ids,
                max_length=256,
                early_stopping=True,
                pad_token_id=proc.tokenizer.pad_token_id,
                eos_token_id=proc.tokenizer.eos_token_id,
                use_cache=True,
                num_beams=1,
                bad_words_ids=[[proc.tokenizer.unk_token_id]],
                return_dict_in_generate=True,
            )

        sequence = proc.batch_decode(outputs.sequences)[0]
        sequence = sequence.replace(proc.tokenizer.eos_token, "").replace(
            proc.tokenizer.pad_token, ""
        )
        try:
            parsed = proc.token2json(sequence)
        except Exception as exc:  # pragma: no cover — défensif
            log.warning("Donut token2json a échoué : %s", exc)
            return {"doc_type": doc_type}

        # 2026-06-15 — post-traitement déterministe (cf. app/clean.py) :
        # déballage "jurika", whitelist par doc_type, flatten des dicts
        # imbriqués parasites, strip <unk>/<...>, réinjection doc_type.
        # Doit s'appliquer AVANT validation (regex CIN/dates) et AVANT la
        # fusion MRZ orchestrée par main.py.
        return clean_fields(parsed, doc_type)
