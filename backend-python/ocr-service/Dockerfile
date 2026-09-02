# JURIKA — ocr-service Dockerfile (CPU only, CNDP-compliant).
#
# Build:  docker build -t jurika/ocr-service:dev -f backend-python/ocr-service/Dockerfile backend-python/ocr-service
# Run:    docker run --rm -p 8089:8089 jurika/ocr-service:dev

FROM python:3.12-slim AS runtime

# - libgl1 + libglib2 sont nécessaires pour OpenCV (cv2).
# - poppler-utils n'est PAS nécessaire (on utilise pypdfium2 pur Python).
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
        libgl1 \
        libglib2.0-0 \
        curl \
 && rm -rf /var/lib/apt/lists/*

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    OCR_ENGINE=doctr \
    OCR_DEFAULT_LANG=latin \
    OCR_MAX_WIDTH=1600 \
    OCR_PRELOAD=true \
    OCR_LOG_LEVEL=INFO

WORKDIR /app

COPY requirements.txt /app/requirements.txt
RUN pip install --upgrade pip \
 && pip install --no-cache-dir -r requirements.txt

COPY app /app/app

EXPOSE 8089

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=120s \
  CMD curl -fsS http://localhost:8089/health || exit 1

# Workers=1 : on évite de tripler le footprint mémoire des modèles OCR sur CPU host.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8089", "--workers", "1"]
