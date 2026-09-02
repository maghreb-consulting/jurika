"""Configuration via variables d'environnement."""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    donut_model_dir: str
    max_file_mb: int
    log_level: str
    preload: bool

    @property
    def max_file_bytes(self) -> int:
        return self.max_file_mb * 1024 * 1024


def load_settings() -> Settings:
    return Settings(
        donut_model_dir=os.getenv("DONUT_MODEL_DIR", "/models/donut-jurika-final"),
        max_file_mb=int(os.getenv("MAX_FILE_MB", "15")),
        log_level=os.getenv("KIE_LOG_LEVEL", "INFO"),
        preload=os.getenv("KIE_PRELOAD", "true").lower() == "true",
    )


SUPPORTED_DOC_TYPES = {
    "cn",
    "cin_anc_recto",
    "cin_anc_verso",
    "cin_nouv_recto",
    "cin_nouv_verso",
}
