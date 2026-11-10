#!/usr/bin/env bash
# 운영 서버에서 다시 배포하거나 앞 버전으로 되돌린다(README 배포 절).
#
#   deploy/deploy.sh          main을 받아 배포한다
#   deploy/deploy.sh <커밋>   그 커밋으로 되돌린다
#
# 단계 하나라도 실패하면 거기서 멈추고, 새 버전을 띄우기 전에 멈췄는지 띄운 뒤에 멈췄는지 찍은 뒤 1로 끝난다.
# 사용법이 틀렸거나 되돌릴 커밋을 찾지 못했거나 root로 돌렸으면 2, 다른 배포가 돌고 있으면 3으로 아무것도 하지 않고 끝난다.
# 배포·되돌리기마다 결과를 ~/mwt-deploys.log에 한 줄씩 남긴다.

# 배포 도중 git pull·checkout이 이 파일을 바꾸거나 지워도 처음 시작한 내용으로 끝까지 돌도록 임시 사본으로 다시 실행한다.
# 그래서 이 파일을 고친 커밋은 그 커밋을 받은 다음 배포부터 적용된다
if [ "${MWT_DEPLOY_COPY:-}" != "$0" ]; then
  deploy_dir=$(cd "$(dirname "$0")" && pwd -P) || exit 1
  copy=$(mktemp "${TMPDIR:-/tmp}/mwt-deploy.XXXXXX") || exit 1
  cp "$0" "$copy" || { rm -f "$copy"; exit 1; }
  MWT_DEPLOY_COPY=$copy MWT_DEPLOY_DIR=$deploy_dir exec bash "$copy" "$@"
fi
trap 'rm -f "$MWT_DEPLOY_COPY"' EXIT
set -u

# 함께 붙여 넣은 다음 줄이나 SSH 입력을 docker compose exec·run이 입력으로 가져가지 않게 한다
exec </dev/null

usage() {
  echo "사용법: deploy/deploy.sh          main을 받아 배포합니다" >&2
  echo "        deploy/deploy.sh <커밋>   그 커밋으로 되돌립니다" >&2
  exit 2
}
[ $# -le 1 ] || usage
case "${1:-}" in -*) usage ;; esac

# root로 돌리면 잠금·배포 기록·백업이 /root 아래로 갈라져 서로 보지 못하고, 레포에 root 소유 파일이 생길 수 있다
if [ "$(id -u)" -eq 0 ]; then
  echo "root(sudo)로 돌리지 않습니다. 서버 계정으로 sudo 없이 돌립니다" >&2
  exit 2
fi

cd "$MWT_DEPLOY_DIR" || exit 1

if [ $# -eq 1 ]; then
  kind=되돌리기
  target=$(git rev-parse --verify --quiet "$1^{commit}") || { echo "커밋을 찾지 못했습니다: $1" >&2; exit 2; }
else
  kind=배포
fi

# 배포가 두 번 겹쳐 돌지 않게 잠근다. 잠금은 이 스크립트가 끝나면(죽어도) 풀린다
exec 9>"$HOME/.mwt-deploy.lock" || exit 1
flock -n 9 || { echo "다른 배포가 돌고 있습니다. 그 배포가 끝난 뒤 다시 돌립니다" >&2; exit 3; }

log=$HOME/mwt-deploys.log
B=     # 이번에 받아 끝까지 확인한 백업 파일
part=  # 받는 중인 백업 파일(끝까지 받아진 것을 확인하면 B로 이름을 바꾼다)
U=     # 새 버전을 띄우기 시작했으면 1
note=  # 기록의 결과 칸에 덧붙일 말

step() { echo "== $*"; }

# 시각, 배포/되돌리기, 커밋, 결과, 배포 전 백업을 한 줄로 남긴다(README "배포 되돌리기")
record() {
  printf '%s\t%s\t%s\t%s\t%s\n' "$(TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S')" "$kind" "$(git rev-parse --short HEAD)" "$1" "${B:--}" >>"$log" \
    || echo "배포 기록($log)을 남기지 못했습니다" >&2
}

# 단계가 실패했을 때: 어디서 멈췄는지 알리고 기록한 뒤 1로 끝낸다
stop() {
  if [ -n "$part" ]; then rm -f "$part"; fi
  if [ -n "$U" ]; then
    record "띄운 뒤 멈춤$note"
    if [ "$kind" = 배포 ]; then
      echo "배포가 새 버전을 띄운 뒤에 멈췄습니다(배포 전 백업: $B). 서버가 문제면 docker compose logs app nginx 를 보고, 고칠 수 없으면 GitHub의 README '배포 되돌리기'로 갑니다"
    else
      echo "되돌리기가 앞 버전을 띄운 뒤에 멈췄습니다(되돌리기 전 백업: $B). 앞 버전 서버가 뜨지 못했으면 GitHub의 README '배포 되돌리기' 2번 표를 봅니다(서버의 README는 앞 버전입니다)"
    fi
  else
    record "띄우기 전 멈춤$note"
    if [ "$kind" = 배포 ]; then
      echo "배포가 새 버전을 띄우기 전에 멈췄습니다. 운영은 앞 버전 그대로이니, 위 출력에서 멈춘 단계를 보고 고친 뒤 다시 돌립니다"
    else
      echo "되돌리기가 앞 버전을 띄우기 전에 멈췄습니다. 운영은 그대로입니다. 위 출력에서 멈춘 단계를 보고 고친 뒤 다시 돌립니다(되돌리기를 그만둘 때는 따로 할 일이 없습니다 — 다음 배포가 main으로 돌아갑니다)"
    fi
  fi
  exit 1
}

# Ctrl-C 등으로 끊겨도 기록을 남긴다. 받는 중이던 백업 파일은 지운다
trap 'trap - INT TERM HUP; echo "중단 신호를 받았습니다" >&2; note="(중단)"; stop' INT TERM HUP

if [ "$kind" = 배포 ]; then
  branch=$(git symbolic-ref -q --short HEAD)
  if [ "$branch" != main ]; then
    echo "서버의 코드가 main이 아니라 ${branch:-커밋 $(git rev-parse --short HEAD)}에 있습니다(배포를 되돌린 뒤 등). main으로 돌아가 배포합니다"
    git checkout main || { echo "main으로 돌아가지 못했습니다(위 git 오류를 봅니다)"; stop; }
  fi
  step "main을 받습니다"
  # pull 뒤 뒤에서 도는 git gc·maintenance가 잠금(9번)을 물려받아 배포가 끝난 뒤에도 쥐고 있지 않게 닫아 준다
  git pull --ff-only 9>&- || { echo "main을 받지 못했습니다(위 git 오류를 봅니다)"; stop; }
else
  step "$1 커밋으로 옮깁니다"
  git checkout --detach "$target" || { echo "$1 커밋으로 옮기지 못했습니다(위 git 오류를 봅니다)"; stop; }
fi

step "Nginx·Certbot 이미지를 받고 Nginx 설정을 확인합니다"
docker compose pull nginx certbot || stop
docker compose run --rm --entrypoint /docker-entrypoint.sh nginx nginx -t || stop

# 베이스(JRE)까지 새로 받아 보안 수정이 들어가게 한다. 빌드하는 동안 앞 서버가 그대로 응답한다
step "서버 이미지를 빌드합니다"
docker compose build --pull app || stop

# DB 테이블 변경(Flyway)은 서버가 뜰 때 저절로 적용되고, 중간에 실패하면 되돌릴 수 없어서 띄우기 바로 전에 받는다.
# 빌드 뒤에 받아야 빌드하는 몇 분 동안 들어온 가입·신청도 들어간다
step "DB를 백업합니다"
part=$HOME/mwt-$(TZ=Asia/Seoul date +%Y%m%d-%H%M%S).sql.part
# shellcheck disable=SC2016 # $MYSQL_*는 mysql 컨테이너 안의 셸이 채운다
if ! { (umask 077; docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --no-tablespaces --single-transaction --add-drop-database --databases mwt -u"$MYSQL_USER"' >"$part") \
  && tail -n 1 "$part" | grep -q '^-- Dump completed' \
  && mv "$part" "${part%.part}"; }; then
  echo "DB를 백업하지 못했습니다"
  stop
fi
B=${part%.part}
part=

# Nginx는 늘 새로 만든다. 설정(nginx/templates)은 컨테이너가 시작할 때만 채워지기 때문이다
step "새 버전을 띄웁니다"
U=1
docker compose up -d --remove-orphans || stop
docker compose up -d --force-recreate nginx || stop

# 바깥 DNS를 거치지 않고 이 서버의 Nginx로 바로 묻는다
step "서버가 뜰 때까지 3분까지 기다립니다"
D=$(docker compose exec -T nginx printenv DOMAIN) || { echo "Nginx 컨테이너에서 서버 주소(DOMAIN)를 읽지 못했습니다"; stop; }
[ -n "$D" ] || { echo "Nginx 컨테이너의 서버 주소(DOMAIN)가 비어 있습니다"; stop; }
curl -fs -o /dev/null --resolve "$D:443:127.0.0.1" --max-time 10 --retry 60 --retry-delay 3 --retry-max-time 180 --retry-all-errors "https://$D/api/docs" \
  || { echo "https://$D/api/docs 가 3분 안에 응답하지 않았습니다"; stop; }

trap - INT TERM HUP
record 성공
if [ "$kind" = 배포 ]; then
  echo "배포했습니다: $(git rev-parse --short HEAD) (배포 전 백업: $B)"
else
  echo "배포했습니다: $(git rev-parse --short HEAD) (앞 버전으로 되돌림, 되돌리기 전 백업: $B)"
fi

# 앞 배포의 이미지와 일주일 넘게 안 쓴 빌드 캐시를 지운다. 배포는 백업을 최근 10개만 남기고,
# 되돌리기는 되살려야 할 백업이 지워지지 않게 백업을 그대로 둔다
step "앞 이미지와 오래된 백업을 정리합니다"
if [ "$kind" = 배포 ]; then
  # shellcheck disable=SC2012 # 지울 파일 이름은 이 스크립트가 만드는 mwt-<시각>.sql 꼴뿐이다
  { docker image prune -f && docker builder prune -f --filter until=168h \
    && ls -1t "$HOME"/mwt-*.sql | tail -n +11 | while IFS= read -r f; do rm -- "$f" || exit 1; done; } \
    || echo "배포는 끝났고, 앞 이미지·오래된 백업 정리만 실패했습니다. 디스크가 차면 위 출력을 봅니다"
else
  { docker image prune -f && docker builder prune -f --filter until=168h; } \
    || echo "되돌리기는 끝났고, 앞 이미지 정리만 실패했습니다"
fi
exit 0
