FROM python:3.12-slim
WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# Non-root, and the model directory is writable so the fitted model can be
# persisted across restarts.
RUN useradd --create-home --shell /bin/bash obms \
    && mkdir -p /app/models \
    && chown -R obms:obms /app
USER obms

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
