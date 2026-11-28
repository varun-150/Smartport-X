#!/usr/bin/env bash
# =============================================================================
# SmartPortX — RabbitMQ topology declaration (M1.3)
#
# Declares the full broker topology from COMMUNICATION-ARCHITECTURE §3.2–§3.4:
#   - 6 exchanges  (4 topic: domain-events/notifications/audit-events/integration-events,
#                   2 fanout: system-broadcasts/cache-invalidation)
#   - 24 primary queues + per-queue `.retry` (TTL 60 000 ms) + `.dlq`
#   - primary bindings per §3.3 topology
#   - DEV-ONLY additions to make §3.4 DXL routing actually deliver (recorded D-425):
#       * primary queue also bound by its literal queue-name key so the retry DLX
#         (routing-key = <queue-name>) returns to the primary
#       * each `.dlq` bound by its literal `<queue-name>.dlq` key on domain-events
#         so messages dead-lettered with that key reach it
#
# Idempotent: `rabbitmqadmin declare` is a no-op when the object already exists
# with identical arguments. Requires rabbitmqadmin (present in rabbitmq:3-management).
#
# Usage (defaults match docker-compose dev):
#   ./scripts/rabbit-topology.sh
#   RABBITMQ_ADMIN_URL=http://localhost:15672 ./scripts/rabbit-topology.sh
# =============================================================================
set -euo pipefail

: "${RABBITMQ_HOST:=localhost}"
: "${RABBITMQ_PORT:=15672}"
: "${RABBITMQ_USERNAME:=guest}"
: "${RABBITMQ_PASSWORD:=guest}"
DOMAIN_EX="smartportx.domain-events"

declare -a COMMANDS=()
rq="rabbitmqadmin --host=$RABBITMQ_HOST --port=$RABBITMQ_PORT -u $RABBITMQ_USERNAME -p $RABBITMQ_PASSWORD"

declare_exchange() {
  COMMANDS+=("$rq declare exchange name=$1 type=$2 durable=true")
}
declare_queue() {
  local name="$1" args="$2"
  COMMANDS+=("$rq declare queue name=$name durable=true arguments=$args")
}
declare_binding() {
  COMMANDS+=("$rq declare binding source=$1 destination_type=queue destination=$2 routing_key=$3")
}

# -- Exchanges -----------------------------------------------------------------
for ex in smartportx.domain-events smartportx.notifications \
          smartportx.audit-events smartportx.integration-events; do
  declare_exchange "$ex" topic
done
for ex in smartportx.system-broadcasts smartportx.cache-invalidation; do
  declare_exchange "$ex" fanout
done

# -- Queues: PRIMARY | EXCHANGE | BINDING-KEY ----------------------------------
# (COMMUNICATION-ARCHITECTURE §3.3 complete topology — 24 primary queues)
readarray -t QUEUE_SPECS <<'LINES'
yard.container-moved|smartportx.domain-events|container.#
gate.container-moved|smartportx.domain-events|container.#
vessel.container-loaded|smartportx.domain-events|container.#
integration.container-moved|smartportx.domain-events|container.#
notification.container-moved|smartportx.domain-events|container.#
container.yard-updated|smartportx.domain-events|yard.#
gate.yard-updated|smartportx.domain-events|yard.#
vessel.yard-updated|smartportx.domain-events|yard.#
notification.yard-status-changed|smartportx.domain-events|yard.#
container.gate-entry|smartportx.domain-events|gate.#
yard.gate-entry|smartportx.domain-events|gate.#
vessel.gate-entry|smartportx.domain-events|gate.#
notification.gate-entry-processed|smartportx.domain-events|gate.#
container.vessel-arrival|smartportx.domain-events|vessel.#
yard.vessel-arrival|smartportx.domain-events|vessel.#
notification.vessel-status-changed|smartportx.domain-events|vessel.#
gate.appointment-created|smartportx.domain-events|appointment.#
yard.appointment-created|smartportx.domain-events|appointment.#
notification.appointment-updated|smartportx.domain-events|appointment.#
container.customs-cleared|smartportx.domain-events|customs.#
notification.customs-status-changed|smartportx.domain-events|customs.#
notification.push-events|smartportx.notifications|notification.#
notification.digest-events|smartportx.notifications|notification.#
integration.audit-trail|smartportx.audit-events|audit.#
LINES

for spec in "${QUEUE_SPECS[@]}"; do
  IFS='|' read -r QNAME QEX QKEY <<<"$spec"

  declare_queue "$QNAME" "{\"x-dead-letter-exchange\":\"$DOMAIN_EX\",\"x-dead-letter-routing-key\":\"$QNAME.dlq\"}"
  declare_queue "$QNAME.retry" "{\"x-dead-letter-exchange\":\"$DOMAIN_EX\",\"x-dead-letter-routing-key\":\"$QNAME\",\"x-message-ttl\":60000}"
  declare_queue "$QNAME.dlq" '{}'

  declare_binding "$QEX" "$QNAME" "$QKEY"
  # D-425: literal-key binding so retry-DLX (routing key = queue name) returns to primary
  declare_binding "$DOMAIN_EX" "$QNAME" "$QNAME"
  # D-425: DLQ binding so dead-lettered messages reach it
  declare_binding "$DOMAIN_EX" "$QNAME.dlq" "$QNAME.dlq"
done

# -- Execute -------------------------------------------------------------------
for cmd in "${COMMANDS[@]}"; do
  echo ">> $cmd"
  eval "$cmd"
done

echo "[rabbit-topology] complete (6 exchanges, ${#QUEUE_SPECS[@]} primary queues + retry/dlq each)"