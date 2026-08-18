FROM python:3.12-slim

WORKDIR /opt/engine
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY engine_server.py .

ENV ENGINE_PORT=9100
EXPOSE 9100

# To use the real vector64 binary instead of the dev engine, mount it and set:
#   ENGINE_CMD=/opt/engine/bin/ChessEngine-validator
CMD ["python", "engine_server.py"]
