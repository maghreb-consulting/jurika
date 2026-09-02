"""Schémas Pydantic d'entrée/sortie."""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class ExtractResponse(BaseModel):
    doc_type: str = Field(..., description="Type de document fourni par l'appelant.")
    fields: dict[str, Any] = Field(default_factory=dict, description="Champs extraits.")
    source: str = Field(..., description="kie | merged (kie+mrz)")
    warnings: list[str] = Field(default_factory=list, description="Anomalies non bloquantes détectées en post-traitement.")


class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
