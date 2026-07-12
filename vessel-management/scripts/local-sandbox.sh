#!/usr/bin/env bash
# Sandbox local do vessel-management SEM Docker: baixa o DynamoDB Local
# standalone (jar da AWS, não o container), cria a tabela, builda e sobe a
# aplicação real contra ele. Útil neste ambiente (ou qualquer um sem Docker
# disponível) — em máquina com Docker, prefira Testcontainers (é o que os
# testes de contrato/integração já usam, ver README.md).
#
# Uso:
#   ./scripts/local-sandbox.sh start   # baixa (1a vez), cria tabela, builda e sobe a app
#   ./scripts/local-sandbox.sh stop    # derruba app + DynamoDB Local
#   ./scripts/local-sandbox.sh status  # mostra o que está rodando
#
# Portas: DynamoDB Local em :8000, aplicação em :8080 (sobrescreva com
# DDB_PORT / APP_PORT no ambiente antes de chamar o script).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SANDBOX_DIR="${SANDBOX_DIR:-/tmp/vessel-management-sandbox}"
DDB_DIR="$SANDBOX_DIR/dynamodb-local"
DDB_PORT="${DDB_PORT:-8000}"
APP_PORT="${APP_PORT:-8080}"
TABLE_NAME="${DYNAMODB_TABLE_NAME:-reserva-enseada-vessel-management-dev}"
DDB_PID_FILE="$SANDBOX_DIR/dynamodb-local.pid"
APP_PID_FILE="$SANDBOX_DIR/app.pid"

mkdir -p "$SANDBOX_DIR"

port_in_use() {
  local port="$1"
  (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null && { exec 3<&- 3>&-; return 0; }
  return 1
}

# Espera até a porta responder ou estoura o timeout — evita seguir em frente
# (ex.: criar tabela, subir a app) com um processo que na verdade falhou ao
# subir (ex.: porta já ocupada por outro processo não rastreado por este script).
wait_for_port() {
  local port="$1" label="$2" timeout="${3:-20}" waited=0
  while ! port_in_use "$port"; do
    sleep 1
    waited=$((waited + 1))
    if [ "$waited" -ge "$timeout" ]; then
      echo "ERRO: $label não respondeu em :$port após ${timeout}s. Veja o log em $SANDBOX_DIR." >&2
      exit 1
    fi
  done
}

download_dynamodb_local() {
  if [ -f "$DDB_DIR/DynamoDBLocal.jar" ]; then
    return
  fi
  echo "Baixando DynamoDB Local (uma vez só, fica em $DDB_DIR)..."
  mkdir -p "$DDB_DIR"
  curl -sS -o "$SANDBOX_DIR/ddb.tar.gz" \
    "https://s3.us-west-2.amazonaws.com/dynamodb-local/dynamodb_local_latest.tar.gz"
  tar xzf "$SANDBOX_DIR/ddb.tar.gz" -C "$DDB_DIR"
}

start_dynamodb_local() {
  if [ -f "$DDB_PID_FILE" ] && kill -0 "$(cat "$DDB_PID_FILE")" 2>/dev/null; then
    echo "DynamoDB Local já rodando (PID $(cat "$DDB_PID_FILE"))."
    return
  fi
  if port_in_use "$DDB_PORT"; then
    echo "ERRO: porta $DDB_PORT já está em uso por um processo não rastreado por este script." >&2
    echo "Pare esse processo ou rode com DDB_PORT=<outra porta> $0 start" >&2
    exit 1
  fi
  download_dynamodb_local
  echo "Subindo DynamoDB Local em :$DDB_PORT (in-memory)..."
  # `exec` dentro do subshell garante que o PID capturado por $! seja o do
  # processo java de fato (sem isso, "cd && nohup ... &" backgrounda um
  # subshell intermediário e $! aponta pro PID errado).
  (cd "$DDB_DIR" && exec nohup java -Djava.library.path=./DynamoDBLocal_lib \
    -jar DynamoDBLocal.jar -inMemory -port "$DDB_PORT" \
    > "$SANDBOX_DIR/dynamodb-local.log" 2>&1) &
  echo $! > "$DDB_PID_FILE"
  wait_for_port "$DDB_PORT" "DynamoDB Local" 20
}

create_table() {
  echo "Criando tabela $TABLE_NAME (PK/SK + GSI1, mesmo schema de infra/dynamodb.tf)..."
  python3 - "$DDB_PORT" "$TABLE_NAME" <<'PYEOF'
import json, sys, urllib.request

port, table_name = sys.argv[1], sys.argv[2]

def call(action, payload):
    req = urllib.request.Request(
        f"http://localhost:{port}",
        data=json.dumps(payload).encode(),
        headers={
            "Content-Type": "application/x-amz-json-1.0",
            "X-Amz-Target": f"DynamoDB_20120810.{action}",
            "Authorization": "AWS4-HMAC-SHA256 Credential=local/20260101/sa-east-1/dynamodb/aws4_request, "
                              "SignedHeaders=host, Signature=" + "0" * 64,
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()

status, body = call("DescribeTable", {"TableName": table_name})
if status == 200:
    print(f"Tabela {table_name} já existe, seguindo.")
    sys.exit(0)

status, body = call("CreateTable", {
    "TableName": table_name,
    "BillingMode": "PAY_PER_REQUEST",
    "KeySchema": [
        {"AttributeName": "PK", "KeyType": "HASH"},
        {"AttributeName": "SK", "KeyType": "RANGE"},
    ],
    "AttributeDefinitions": [
        {"AttributeName": "PK", "AttributeType": "S"},
        {"AttributeName": "SK", "AttributeType": "S"},
        {"AttributeName": "GSI1PK", "AttributeType": "S"},
        {"AttributeName": "GSI1SK", "AttributeType": "S"},
    ],
    "GlobalSecondaryIndexes": [{
        "IndexName": "GSI1",
        "KeySchema": [
            {"AttributeName": "GSI1PK", "KeyType": "HASH"},
            {"AttributeName": "GSI1SK", "KeyType": "RANGE"},
        ],
        "Projection": {"ProjectionType": "ALL"},
    }],
})
print(status, body[:200])
PYEOF
}

build_and_start_app() {
  if [ -f "$APP_PID_FILE" ] && kill -0 "$(cat "$APP_PID_FILE")" 2>/dev/null; then
    echo "Aplicação já rodando (PID $(cat "$APP_PID_FILE"))."
    return
  fi
  if port_in_use "$APP_PORT"; then
    echo "ERRO: porta $APP_PORT já está em uso por um processo não rastreado por este script." >&2
    echo "Pare esse processo ou rode com APP_PORT=<outra porta> $0 start" >&2
    exit 1
  fi

  echo "Empacotando (mvn package -DskipTests)..."
  (cd "$MODULE_DIR" && mvn -q package -DskipTests)

  echo "Subindo a aplicação em :$APP_PORT contra o DynamoDB Local..."
  (cd "$MODULE_DIR" && \
    DYNAMODB_ENDPOINT_OVERRIDE="http://localhost:$DDB_PORT" \
    AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local AWS_REGION=sa-east-1 \
    DYNAMODB_TABLE_NAME="$TABLE_NAME" \
    exec nohup java -jar target/vessel-management.jar --server.port="$APP_PORT" \
    > "$SANDBOX_DIR/app.log" 2>&1) &
  echo $! > "$APP_PID_FILE"
  wait_for_port "$APP_PORT" "Aplicação" 60
}

stop_all() {
  for pid_file in "$APP_PID_FILE" "$DDB_PID_FILE"; do
    if [ -f "$pid_file" ]; then
      pid="$(cat "$pid_file")"
      if kill -0 "$pid" 2>/dev/null; then
        kill "$pid"
        echo "Parado PID $pid ($pid_file)."
      fi
      rm -f "$pid_file"
    fi
  done
}

status() {
  for name_pidfile in "DynamoDB Local:$DDB_PID_FILE" "Aplicação:$APP_PID_FILE"; do
    name="${name_pidfile%%:*}"
    pid_file="${name_pidfile#*:}"
    if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
      echo "$name: rodando (PID $(cat "$pid_file"))"
    else
      echo "$name: parado"
    fi
  done
}

case "${1:-start}" in
  start)
    start_dynamodb_local
    create_table
    build_and_start_app
    cat <<EOF

Sandbox no ar:
  DynamoDB Local: http://localhost:$DDB_PORT
  Aplicação:      http://localhost:$APP_PORT
  Logs:           $SANDBOX_DIR/app.log , $SANDBOX_DIR/dynamodb-local.log

Exemplo:
  curl -X POST http://localhost:$APP_PORT/vessels \\
    -H "Content-Type: application/json" \\
    -d '{"ownerId":"owner-1","nomeLegal":"Sereia do Mar","nomeFantasia":"Passeios Sereia","numeroRegistroCapitania":"CP-1","cpfCnpjProprietario":"111","capacidadeMaxima":20,"portoSaida":"Porto A"}'

OpenAPI:  http://localhost:$APP_PORT/v3/api-docs
Swagger:  http://localhost:$APP_PORT/swagger-ui.html

Pare tudo com: $0 stop
EOF
    ;;
  stop)
    stop_all
    ;;
  status)
    status
    ;;
  *)
    echo "Uso: $0 {start|stop|status}" >&2
    exit 1
    ;;
esac
