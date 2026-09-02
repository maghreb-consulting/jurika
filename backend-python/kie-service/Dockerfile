# JURIKA — kie-service Dockerfile (CPU only, CNDP-compliant).
#
# Build : docker build -t jurika/kie-service:dev -f backend-python/kie-service/Dockerfile backend-python/kie-service
# Run   : docker run --rm -p 8088:8088 \
#           -e DONUT_MODEL_DIR=/models/donut-jurika-final \
#           -v /chemin/host/donut-jurika-final:/models/donut-jurika-final:ro \
#           jurika/kie-service:dev
#
# IMPORTANT : les poids du modèle NE SONT PAS embarqués dans l'image.
# Ils sont montés en volume read-only au runtime.

FROM python:3.11-slim AS runtime

# Dépendances système minimales pour Pillow + PyMuPDF.
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
        libglib2.0-0 \
        libgl1 \
        curl \
 && rm -rf /var/lib/apt/lists/*

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    USE_TF=0 \
    USE_JAX=0 \
    USE_TORCH=1 \
    DONUT_MODEL_DIR=/models/donut-jurika-final \
    MAX_FILE_MB=15 \
    KIE_LOG_LEVEL=INFO \
    KIE_PRELOAD=true

WORKDIR /app

COPY requirements.txt /app/requirements.txt

# torch CPU wheel — on évite le wheel CUDA qui pèse plusieurs Go.
RUN pip install --upgrade pip \
 && pip install --no-cache-dir \
        --extra-index-url https://download.pytorch.org/whl/cpu \
        -r requirements.txt

COPY app /app/app

# User non-root.
RUN useradd --create-home --uid 1000 kie \
 && chown -R kie:kie /app
USER kie

EXPOSE 8088

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=120s \
  CMD curl -fsS http://localhost:8088/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8088", "--workers", "1"]
