"""Tests preprocess — décodage image, PDF, downscale."""

from __future__ import annotations

import io

import numpy as np
import pytest
from PIL import Image

from app.preprocess import decode_to_rgb, downscale_if_needed, to_numpy_rgb


def _png_bytes(w: int = 100, h: int = 60) -> bytes:
    img = Image.new("RGB", (w, h), color=(255, 255, 255))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def test_decode_image_returns_rgb():
    data = _png_bytes(800, 600)
    img = decode_to_rgb(data, "scan.png")
    assert img.mode == "RGB"
    assert img.size == (800, 600)


def test_decode_image_converts_grayscale():
    gray = Image.new("L", (50, 50), color=128)
    buf = io.BytesIO()
    gray.save(buf, format="PNG")
    img = decode_to_rgb(buf.getvalue(), "g.png")
    assert img.mode == "RGB"


def test_decode_empty_raises():
    with pytest.raises(ValueError):
        decode_to_rgb(b"", "x.png")


def test_decode_invalid_raises():
    with pytest.raises(ValueError):
        decode_to_rgb(b"not-a-real-image", "x.png")


def test_downscale_no_op_when_smaller():
    img = Image.new("RGB", (800, 600))
    out, did = downscale_if_needed(img, 1600)
    assert did is False
    assert out.size == (800, 600)


def test_downscale_preserves_ratio():
    img = Image.new("RGB", (3200, 2400))
    out, did = downscale_if_needed(img, 1600)
    assert did is True
    assert out.size == (1600, 1200)


def test_downscale_disabled_when_max_zero():
    img = Image.new("RGB", (2000, 2000))
    out, did = downscale_if_needed(img, 0)
    assert did is False
    assert out.size == (2000, 2000)


def test_to_numpy_rgb_shape():
    img = Image.new("RGB", (10, 20), color=(0, 0, 0))
    arr = to_numpy_rgb(img)
    assert isinstance(arr, np.ndarray)
    assert arr.shape == (20, 10, 3)
    assert arr.dtype == np.uint8
