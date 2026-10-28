FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt && useradd --uid 10001 --create-home tester
COPY server.py runtime.py store.py ./
COPY web ./web
USER tester
CMD ["uvicorn", "server:create_app", "--factory", "--host", "0.0.0.0", "--port", "18767", "--workers", "1", "--ws-max-size", "65536", "--limit-concurrency", "32", "--no-access-log"]
