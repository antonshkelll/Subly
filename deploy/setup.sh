#!/usr/bin/env bash
# =====================================================================
#  setup.sh — установка и обновление сервера Subly на VPS (Ubuntu/Debian)
#
#  Первая установка:          sudo bash deploy/setup.sh
#  Обновить до новой версии:  sudo bash deploy/setup.sh --update
#  Выпустить SSL позже:       sudo bash deploy/setup.sh --ssl
#  Состояние и журнал:        sudo bash deploy/setup.sh --status | --logs
#  Резервная копия базы:      sudo bash deploy/setup.sh --backup
#  Поменять ключи/админов:    sudo bash deploy/setup.sh --reconfigure
#
#  Что делает: ставит Docker (если нет), кладёт всё в /opt/subly,
#  поднимает PostgreSQL и сервер в контейнерах, добавляет сайт в nginx
#  и выпускает бесплатный сертификат Let's Encrypt.
#  Другие сайты в nginx, ботов и порт 8080 не трогает: Subly слушает 127.0.0.1:8090.
# =====================================================================
set -Eeuo pipefail
umask 022

DOMAIN="${SUBLY_DOMAIN:-subly.antonshkel.ru}"
DIR="/opt/subly"
REPO="antonshkelll/Subly"
JAR_URL="https://github.com/${REPO}/releases/latest/download/subly-server.jar"
NGX_SITE="/etc/nginx/sites-available/subly.conf"
NGX_LINK="/etc/nginx/sites-enabled/subly.conf"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -t 1 ]]; then B=$'\e[1m'; G=$'\e[32m'; Y=$'\e[33m'; R=$'\e[31m'; C=$'\e[36m'; N=$'\e[0m'; else B=; G=; Y=; R=; C=; N=; fi
step() { printf '\n%s▸ %s%s\n' "$B$C" "$*" "$N"; }
ok()   { printf '  %s✓%s %s\n' "$G" "$N" "$*"; }
warn() { printf '  %s!%s %s\n' "$Y" "$N" "$*"; }
die()  { printf '\n%s✗ %s%s\n' "$R$B" "$*" "$N" >&2; exit 1; }
trap 'printf "\n%s✗ Ошибка в строке %s: %s%s\nДругие сервисы на сервере не тронуты.\n" "$R" "$LINENO" "$BASH_COMMAND" "$N" >&2' ERR

[[ $EUID -eq 0 ]] || die "Запустите через sudo: sudo bash deploy/setup.sh"

compose() { (cd "$DIR" && docker compose "$@"); }

ask() {  # ask "Вопрос" "значение по умолчанию" → ответ
  local q="$1" def="${2:-}" a
  if [[ -n "$def" ]]; then read -r -p "  $q [$def]: " a; echo "${a:-$def}"; else read -r -p "  $q: " a; echo "$a"; fi
}

install_docker() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then ok "Docker уже установлен"; return; fi
  step "Ставлю Docker"
  curl -fsSL https://get.docker.com | sh
  systemctl enable --now docker
  ok "Docker установлен"
}

install_nginx() {
  if ! command -v nginx >/dev/null 2>&1; then
    step "Ставлю nginx"
    apt-get update -qq && apt-get install -y -qq nginx
  fi
  command -v certbot >/dev/null 2>&1 || apt-get install -y -qq certbot python3-certbot-nginx
  ok "nginx и certbot на месте"
}

download_jar() {
  step "Скачиваю сервер (последний релиз)"
  curl -fL --retry 3 -o "$DIR/subly-server.jar.new" "$JAR_URL" || die "Не удалось скачать $JAR_URL. Есть ли релиз на GitHub?"
  [[ -f "$DIR/subly-server.jar" ]] && cp "$DIR/subly-server.jar" "$DIR/subly-server.jar.prev"
  mv "$DIR/subly-server.jar.new" "$DIR/subly-server.jar"
  ok "$(du -h "$DIR/subly-server.jar" | cut -f1) — $DIR/subly-server.jar"
}

configure() {
  step "Настройки"
  local env="$DIR/.env" admin shop key
  # Пароли генерируются один раз и не меняются при повторном запуске
  local jwt db
  jwt=$(grep -s '^JWT_SECRET=' "$env" | cut -d= -f2- || true); [[ -n "$jwt" ]] || jwt=$(openssl rand -hex 32)
  db=$(grep -s '^DATABASE_PASSWORD=' "$env" | cut -d= -f2- || true); [[ -n "$db" ]] || db=$(openssl rand -hex 16)
  admin=$(ask "Почта администратора (для входа в /admin)" "$(grep -s '^ADMIN_EMAILS=' "$env" | cut -d= -f2- || true)")
  echo "  ЮKassa можно подключить позже (--reconfigure). Для теста — ключи тестового магазина."
  shop=$(ask "ЮKassa shopId (Enter — пропустить)" "$(grep -s '^YOOKASSA_SHOP_ID=' "$env" | cut -d= -f2- || true)")
  key=""
  if [[ -n "$shop" ]]; then
    read -r -s -p "  ЮKassa секретный ключ (не отображается): " key; echo
    [[ -n "$key" ]] || key=$(grep -s '^YOOKASSA_SECRET_KEY=' "$env" | cut -d= -f2- || true)
  fi
  cat > "$env" <<EOF
PUBLIC_URL=https://${DOMAIN}
JWT_SECRET=${jwt}
DATABASE_PASSWORD=${db}
ADMIN_EMAILS=${admin}
YOOKASSA_SHOP_ID=${shop}
YOOKASSA_SECRET_KEY=${key}
EOF
  chmod 600 "$env"
  ok "Сохранено в $env (права 600)"
}

setup_nginx() {
  step "nginx: сайт ${DOMAIN}"
  if [[ -f "$NGX_SITE" ]] && grep -q "ssl_certificate" "$NGX_SITE"; then
    ok "Конфиг уже есть и с SSL — не трогаю"
  else
    sed "s/subly\.antonshkel\.ru/${DOMAIN}/g" "$HERE/nginx-subly.conf" > "$NGX_SITE"
    ln -sf "$NGX_SITE" "$NGX_LINK"
    nginx -t && systemctl reload nginx
    ok "Конфиг: $NGX_SITE"
  fi
}

issue_ssl() {
  step "SSL-сертификат Let's Encrypt"
  local ip dns
  ip=$(curl -fsS4 https://ifconfig.me 2>/dev/null || true)
  dns=$(getent ahostsv4 "$DOMAIN" | awk '{print $1; exit}' || true)
  if [[ -z "$dns" || ( -n "$ip" && "$dns" != "$ip" ) ]]; then
    warn "Домен $DOMAIN пока не указывает на этот сервер (DNS: ${dns:-нет}, сервер: ${ip:-?})."
    warn "Добавьте A-запись и через 15–60 минут запустите: sudo bash deploy/setup.sh --ssl"
    return
  fi
  certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos --register-unsafely-without-email --redirect
  ok "HTTPS включён: https://${DOMAIN}"
}

wait_healthy() {
  step "Запускаю"
  for _ in $(seq 1 40); do
    if curl -fsS http://127.0.0.1:8090/health >/dev/null 2>&1; then ok "Сервер отвечает: http://127.0.0.1:8090/health"; return; fi
    sleep 2
  done
  compose logs --tail 40 app || true
  die "Сервер не ответил за 80 секунд — журнал выше."
}

case "${1:-install}" in
  install|"")
    command -v openssl >/dev/null || apt-get install -y -qq openssl
    install_docker
    install_nginx
    mkdir -p "$DIR"
    cp "$HERE/docker-compose.yml" "$DIR/docker-compose.yml"
    [[ -f "$DIR/.env" ]] && grep -q '^JWT_SECRET=.' "$DIR/.env" && ok "Настройки уже есть ($DIR/.env)" || configure
    download_jar
    compose up -d
    wait_healthy
    setup_nginx
    issue_ssl
    printf '\n%sГотово.%s\n  Сайт:     https://%s\n  Админка:  https://%s/admin  (зарегистрируйтесь в приложении на почту администратора)\n  Вебхук ЮKassa: https://%s/api/billing/yookassa/webhook\n' "$G$B" "$N" "$DOMAIN" "$DOMAIN" "$DOMAIN"
    ;;
  --update)
    cp "$HERE/docker-compose.yml" "$DIR/docker-compose.yml"
    download_jar
    compose up -d
    compose restart app
    wait_healthy
    ok "Обновлено. Откатиться: cp $DIR/subly-server.jar.prev $DIR/subly-server.jar && sudo bash deploy/setup.sh --restart"
    ;;
  --restart) compose restart app; wait_healthy ;;
  --ssl) issue_ssl ;;
  --reconfigure) configure; compose up -d; compose restart app; wait_healthy ;;
  --status) compose ps; curl -fsS http://127.0.0.1:8090/health && echo ;;
  --logs) compose logs --tail 100 -f app ;;
  --backup)
    mkdir -p "$DIR/backups"
    f="$DIR/backups/subly-$(date +%F-%H%M).sql.gz"
    compose exec -T db pg_dump -U subly subly | gzip > "$f"
    ok "Копия базы: $f"
    ;;
  *) die "Неизвестная команда: $1" ;;
esac
