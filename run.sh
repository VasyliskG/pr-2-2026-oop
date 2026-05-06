#!/usr/bin/env bash
# =====================================================================
# run.sh — швидкий запуск застосунку через Docker Compose
# Використання:
#   ./run.sh db        — лише БД
#   ./run.sh build     — перезібрати образ застосунку
#   ./run.sh logs      — перегляд логів застосунку
#   ./run.sh down      — зупинка контейнерів
#   ./run.sh clean     — повне очищення з видаленням даних
# =====================================================================

set -e

CMD="${1:-up}"
OS="$(uname -s)"

case "$CMD" in
    db)
        echo "→ Запуск БД"
        docker compose up -d db
        echo ""
        echo "✓ PostgreSQL: localhost:5432 (collab_user / collab_secret)"
        ;;

    build)
        echo "→ Перезбірка образу застосунку..."
        docker compose build app
        ;;

    logs)
        docker compose logs -f app
        ;;

    down)
        echo "→ Зупинка контейнерів (дані зберігаються)..."
        docker compose down
        ;;

    clean)
        echo "⚠ ВИДАЛЕННЯ ВСІХ ДАНИХ! Натисніть Enter для підтвердження..."
        read -r
        docker compose down -v
        echo "✓ Виконано."
        ;;

    *)
        echo "Використання: $0 {up|db|build|logs|down|clean|psql}"
        exit 1
        ;;
esac
