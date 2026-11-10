#!/usr/bin/env bash
# 운영 서버에서 재배포하거나 앞 버전으로 되돌린다(README 배포 절).
#
#   ~/be/deploy/deploy.sh          main을 받아 재배포한다
#   ~/be/deploy/deploy.sh <커밋>   그 커밋으로 되돌린다
#
# 단계 하나라도 실패하면 거기서 멈추고, 새 버전을 띄우기 전에 멈췄는지 띄운 뒤에 멈췄는지 찍은 뒤 1로 끝난다.
# 사용법이 틀렸거나 되돌릴 커밋을 받을 수 없거나 root로 돌렸으면 2, 다른 배포나 백업 되살리기가 돌고 있으면 3으로
# 아무것도 하지 않고 끝난다.
# 배포·되돌리기마다 결과를 ~/mwt-deploys.log에 한 줄씩 남긴다.

# 배포 도중 git pull·checkout이 이 파일을 바꾸거나 지워도 처음 시작한 내용으로 끝까지 돌도록 임시 사본으로 다시 실행한다.
# 그래서 이 파일을 고친 커밋은 그 커밋을 받은 다음 배포부터 적용된다
if [ "${MWT_DEPLOY_COPY:-}" != "$0" ]; then
  deploy_dir=$(cd "$(dirname "$(readlink -f "$0")")" && pwd -P) || exit 1
  copy=$(mktemp "${TMPDIR:-/tmp}/mwt-deploy.XXXXXX") || exit 1
  cp "$0" "$copy" || { rm -f "$copy"; exit 1; }
  MWT_DEPLOY_COPY=$copy MWT_DEPLOY_DIR=$deploy_dir exec bash "$copy" "$@"
fi

tee_pid=
# 끝날 때 tee가 마지막 줄까지 쓰기를 기다려, 프롬프트보다 먼저 찍히고 출력 파일에도 다 남게 한다
# shellcheck disable=SC2317,SC2329 # 아래 trap이 부른다(shellcheck 0.10 이하는 SC2317, 0.11은 SC2329로 알린다)
finish() {
  # 정리하는 도중 Ctrl-C로 끊겨 임시 사본과 감시가 남지 않게 한다
  trap '' INT TERM
  if [ -n "$tee_pid" ]; then
    # 잠금을 먼저 놓는다 — 출력을 물고 있는 자손이 남아 tee가 끝나지 않아도 다음 배포가 막히지 않게.
    # 그런 자손이 있어도 스크립트가 끝나도록 10초까지만 기다린다
    exec 9>&- >&- 2>&-
    # tee는 INT·TERM을 무시하게 띄웠고 감시도 그 무시를 물려받으므로, 둘 다 무시할 수 없는 KILL로 끝낸다
    (sleep 10; kill -KILL "$tee_pid" 2>/dev/null) &
    watchdog=$!
    wait "$tee_pid"
    kill -KILL "$watchdog" 2>/dev/null
  fi
  rm -f "$MWT_DEPLOY_COPY"
}
trap finish EXIT
set -u

# 함께 붙여 넣은 다음 줄이나 SSH 입력을 docker compose exec·run이 입력으로 가져가지 않게 한다
exec </dev/null

# SSH 연결이 끊겨도(HUP) 컨테이너를 바꿔 끼우던 docker compose가 같이 죽지 않도록 무시한다.
# 무시는 이 스크립트가 띄우는 명령에도 이어진다. 결과는 다시 접속해 배포 기록으로 본다
trap '' HUP

usage() {
  echo "사용법: ~/be/deploy/deploy.sh          main을 받아 재배포합니다" >&2
  echo "        ~/be/deploy/deploy.sh <커밋>   그 커밋으로 되돌립니다(커밋은 배포 기록의 12자리 해시)" >&2
  echo "아무것도 바꾸지 않았습니다" >&2
  exit 2
}
[ $# -le 1 ] || usage
case "${1:-}" in -*) usage ;; esac

# root로 돌리면 잠금·배포 기록·백업이 /root 아래로 갈라져 서로 보지 못하고, 레포에 root 소유 파일이 생길 수 있다
if [ "$(id -u)" -eq 0 ]; then
  echo "root(sudo)로는 돌릴 수 없습니다. 서버 계정으로 sudo 없이 다시 돌립니다. 아무것도 바꾸지 않았습니다" >&2
  exit 2
fi

cd "$MWT_DEPLOY_DIR" || exit 1

if [ $# -eq 1 ]; then
  kind=되돌리기
  # main 같은 브랜치 이름을 주면 받지 않은 로컬 브랜치로 "되돌리기"가 되므로 커밋 해시만 받는다
  case $1 in
    '' | *[!0-9a-fA-F]*) echo "'$1' 은 커밋 해시가 아닙니다. 배포 기록에 적힌 12자리 해시(예: a1b2c3d4e5f6)를 줍니다. 아무것도 바꾸지 않았습니다" >&2; exit 2 ;;
  esac
  hash=$(printf '%s' "$1" | tr 'A-F' 'a-f')
  # 짧아서 여러 커밋에 걸리면 git이 그렇다고 알리므로 git의 오류를 그대로 보인다
  # git은 같은 이름의 브랜치·태그를 해시보다 먼저 풀므로, git이 그 이름을 ref로 풀면 받지 않는다
  case $(git rev-parse --symbolic-full-name "$hash" 2>/dev/null) in
    refs/*)
      echo "$1 은 브랜치나 태그 이름이기도 해서 해시로 쓸 수 없습니다. 배포 기록의 12자리 해시를 그대로 줍니다. 아무것도 바꾸지 않았습니다" >&2
      exit 2 ;;
  esac
  target=$(git rev-parse --verify "$hash^{commit}") \
    || { echo "커밋 $1 을 찾지 못했습니다(위 git 오류를 봅니다). 배포 기록의 12자리 해시를 그대로 줍니다. 아무것도 바꾸지 않았습니다" >&2; exit 2; }
  git merge-base --is-ancestor "$target" main \
    || { echo "$1 은 main에 없는 커밋(머지되지 않은 커밋)이라 되돌리지 않았습니다. 배포 기록에 적힌 커밋을 줍니다. 아무것도 바꾸지 않았습니다" >&2; exit 2; }
  git cat-file -e "$target:deploy/compose.yaml" 2>/dev/null \
    || { echo "$1 커밋에는 운영 배포 설정(deploy/compose.yaml)이 없어 되돌릴 수 없습니다. 아무것도 바꾸지 않았습니다" >&2; exit 2; }
else
  kind=배포
  target=
fi

# 잠금은 이 스크립트가 끝나면(죽어도) 풀린다. README 백업 되살리기도 같은 잠금 파일을 쥔다
exec 9>>"$HOME/.mwt-deploy.lock" || exit 1
flock -n 9 || { echo "다른 배포나 백업 되살리기가 돌고 있습니다. 그 작업이 끝난 뒤 다시 돌립니다. 아무것도 바꾸지 않았습니다" >&2; exit 3; }

# 서버가 꺼지는 등으로 강제로 끝난 배포가 남긴, 받다 만 백업(회원 정보가 든다)을 지운다
rm -f "$HOME"/mwt-*.sql.part

# 출력을 ~/mwt-deploy-output.log에도 남긴다(마지막 배포 것만). SSH가 끊겨 터미널에 쓰지 못해도
# tee가 파일에는 계속 써서, 명령이 출력 실패로 멈추지 않는다. tee가 Ctrl-C에 같이 죽으면 멈췄다는 문구와 기록을
# 남기기 전에 이 스크립트가 출력 실패로 죽으므로, INT·TERM을 무시한 채 띄워 무시를 물려준다(프로세스 치환 안에서
# 무시하면 bash 5.3에서는 tee가 그대로 죽었다). 잠금(9번)은 물려받지 않게 닫는다.
# -p: 터미널 대신 파이프로 받던 쪽(pty 없는 ssh 등)이 사라져도 tee가 끝나지 않고 파일에 계속 쓴다
# 파일은 지우고 새로 만든다 — 앞 배포의 tee가 아직 쓰고 있어도 그 쓰기는 지운 파일로 가서 섞이지 않는다
trap '' INT TERM
rm -f "$HOME/mwt-deploy-output.log"
exec > >(exec tee -p "$HOME/mwt-deploy-output.log" 9>&-) 2>&1
tee_pid=${!:-}  # bash 4.4 아래는 프로세스 치환의 pid를 알 수 없어 기다리지 않는다

log=$HOME/mwt-deploys.log
backup=
backup_part=
launched=
note=

# 띄우기 전에 멈추면 서버의 코드를 시작할 때 있던 커밋으로 돌려놓는다. 코드를 떠 있는 버전과 다른 커밋에 두면
# 그 뒤 ~/be/deploy에서 치는 docker compose 명령이 다른 설정을 쓴다
start_branch=$(git symbolic-ref -q --short HEAD)
start_commit=$(git rev-parse HEAD)
# 배포 기록의 커밋 칸. 되돌리기는 되돌리려던 커밋, 재배포는 main을 받은 뒤부터 받은 커밋(받기 전에 멈추면 배포 전 커밋)
intended=${target:-$start_commit}
# 서버 이미지 이름(compose.yaml의 app image)도 빌드 전 이미지로 되돌린다. 그러지 않으면 빌드 뒤에 멈췄을 때
# README 되살리기의 docker compose up -d app 같은 명령이 배포하지 않은 새 이미지를 백업 없이 띄운다.
# 빌드 전 이미지에는 두 번째 이름을 붙여 둔다 — containerd 이미지 저장소는 이름이 다 떨어진 이미지를 ID로도 찾지 못한다
app_image=mwt-app
saved_app_image=

step() { echo "== $*"; }

# 칸 순서와 결과 문구는 README "배포 되돌리기"가 읽는 방법과 맞춰 둔다
record() {
  printf '%s\t%s\t%s\t%s\t%s\n' "$(TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S')" "$kind" "$(git rev-parse --short=12 "$intended")" "$1" "${backup:--}" >>"$log" \
    || echo "배포 기록($log)을 남기지 못했습니다" >&2
}

stop() {
  # 기록이 두 줄이 되지 않게, 멈추는 동안 들어온 Ctrl-C 등은 무시한다
  trap '' INT TERM
  # 이름을 바꾼 바로 뒤에 멈췄으면 끝까지 받은 백업이다
  if [ -n "$backup_part" ]; then
    if [ -e "${backup_part%.part}" ]; then backup=${backup_part%.part}; else rm -f "$backup_part"; fi
  fi
  if [ -n "$launched" ]; then
    record "띄운 뒤 멈춤$note"
    if [ "$kind" = 배포 ]; then
      echo "배포가 새 버전을 띄운 뒤에 멈췄습니다(배포 전 백업: $backup). 서버가 문제면 cd ~/be/deploy && docker compose logs app nginx 로 보고, 고칠 수 없으면 GitHub의 README '배포 되돌리기'로 갑니다"
    else
      echo "되돌리기가 앞 버전을 띄운 뒤에 멈췄습니다(되돌리기 전 백업: $backup). 앞 버전 서버가 뜨지 못했으면 GitHub의 README '배포 되돌리기' 2번 표를 봅니다(서버의 README는 앞 버전입니다)"
    fi
    # 새 버전을 띄우기 시작했으니 빌드 전 이미지에 붙여 둔 이름은 더 쓰지 않는다(남기면 다음 배포가 잘못 쓴다)
    if [ -n "$saved_app_image" ]; then docker rmi "$app_image:before-deploy" >/dev/null 2>&1; fi
    exit 1
  fi
  record "띄우기 전 멈춤$note"
  if [ -n "$saved_app_image" ] && docker image inspect "$app_image:before-deploy" >/dev/null 2>&1; then
    { docker tag "$app_image:before-deploy" "$app_image" && docker rmi "$app_image:before-deploy" >/dev/null; } \
      || echo "서버 이미지($app_image)를 빌드 전 이미지로 되돌리지 못했습니다. 재배포하기 전에는 ~/be/deploy 에서 docker compose up 을 치지 않습니다"
  fi
  back=$start_commit
  shown=$(git rev-parse --short=12 "$start_commit")
  if [ -n "$start_branch" ] && [ "$(git rev-parse "$start_branch")" = "$start_commit" ]; then back=$start_branch; shown=$start_branch; fi
  moved_back=
  if [ "$(git rev-parse HEAD)" != "$start_commit" ] || [ "$(git symbolic-ref -q --short HEAD)" != "$start_branch" ]; then
    if git checkout -q "$back"; then
      moved_back=" 서버의 코드도 시작할 때 있던 자리($shown)로 돌려놓았습니다."
    else
      moved_back=" 서버의 코드는 옮겨 간 커밋에 남았으니 cd ~/be && git checkout $back 로 돌려놓습니다."
    fi
  fi
  if [ "$kind" = 배포 ]; then
    echo "배포가 새 버전을 띄우기 전에 멈췄습니다. 운영은 앞 버전 그대로입니다.$moved_back 위 출력에서 멈춘 단계를 보고 고친 뒤 다시 돌립니다"
  else
    echo "되돌리기가 앞 버전을 띄우기 전에 멈췄습니다. 운영은 그대로입니다.$moved_back 위 출력에서 멈춘 단계를 보고 고친 뒤 다시 돌립니다"
  fi
  exit 1
}

# shellcheck disable=SC2317,SC2329 # 아래 trap이 부른다
on_signal() {
  trap '' INT TERM
  echo "중단 신호를 받았습니다" >&2
  note="(중단)"
  stop
}
trap on_signal INT TERM

if [ "$kind" = 배포 ]; then
  if [ "$start_branch" != main ]; then
    echo "서버의 코드가 main이 아니라 ${start_branch:-$(git rev-parse --short=12 HEAD)} 에 있습니다(배포를 되돌렸거나 앞 배포가 띄우기 전에 멈춘 뒤 등). main으로 돌아가 배포합니다"
    git checkout main || { echo "main으로 돌아가지 못했습니다(위 git 오류를 봅니다)"; stop; }
  fi
  step "1/7 main을 받습니다"
  # pull 뒤 뒤에서 도는 git gc·maintenance가 잠금(9번)을 물려받아 배포가 끝난 뒤에도 쥐고 있지 않게 닫아 준다
  git pull --ff-only 9>&- || { echo "main을 받지 못했습니다(위 git 오류를 봅니다)"; stop; }
  intended=$(git rev-parse HEAD)
else
  step "1/7 $1 커밋으로 옮깁니다"
  git checkout --detach "$target" || { echo "$1 커밋으로 옮기지 못했습니다(위 git 오류를 봅니다)"; stop; }
fi

step "2/7 Nginx·Certbot 이미지를 받고 Nginx 설정을 확인합니다"
docker compose pull nginx certbot || stop
docker compose run --rm --entrypoint /docker-entrypoint.sh nginx nginx -t || stop

# 베이스(JRE)까지 새로 받아 보안 수정이 들어가게 한다. 빌드하는 동안 앞 서버가 그대로 응답한다
step "3/7 서버 이미지를 빌드합니다"
# 강제로 끝난 앞 배포가 남긴 이름을 먼저 지운다. 표시를 이름 붙이기보다 먼저 해 두어, 그 사이 멈춰도 이 이름을 정리한다
docker rmi "$app_image:before-deploy" >/dev/null 2>&1
if docker image inspect "$app_image" >/dev/null 2>&1; then
  saved_app_image=1
  docker tag "$app_image" "$app_image:before-deploy" || stop
fi
# 출처 증명(provenance attestation)을 붙이지 않는다. 붙이면 내용이 같은 빌드도 이미지 ID가 빌드마다 바뀌는데, Compose는
# 내용이 같다며 서버 컨테이너를 그대로 둬서, 7단계가 아직 쓰이는 앞 이미지의 이름을 지우려다 실패한다(#59)
BUILDX_NO_DEFAULT_ATTESTATIONS=1 docker compose build --pull app || stop

# DB 테이블 변경(Flyway)은 서버가 뜰 때 저절로 적용되고, 중간에 실패하면 되돌릴 수 없어서 띄우기 바로 전에 받는다.
# 빌드 뒤에 받아야 빌드하는 몇 분 동안 들어온 가입·신청도 들어간다. 끝까지 받아진 것을 확인한 뒤에야 이름을 붙인다
step "4/7 DB를 백업합니다"
backup_part=$HOME/mwt-$(TZ=Asia/Seoul date +%Y%m%d-%H%M%S).sql.part
# shellcheck disable=SC2016 # $MYSQL_*는 mysql 컨테이너 안의 셸이 채운다
if ! { (umask 077; docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --no-tablespaces --single-transaction --add-drop-database --databases mwt -u"$MYSQL_USER"' >"$backup_part") \
  && tail -n 1 "$backup_part" | grep -q '^-- Dump completed' \
  && mv "$backup_part" "${backup_part%.part}"; }; then
  echo "DB를 백업하지 못했습니다"
  stop
fi
backup=${backup_part%.part}
backup_part=

# Nginx는 늘 새로 만든다. 설정(nginx/templates)은 컨테이너가 시작할 때만 채워지기 때문이다
step "5/7 새 버전을 띄웁니다"
launched=1
docker compose up -d --remove-orphans || stop
docker compose up -d --force-recreate nginx || stop

step "6/7 서버가 뜰 때까지 3분까지 기다립니다"
domain=$(docker compose exec -T nginx printenv DOMAIN) \
  || { echo "Nginx 컨테이너에서 서버 주소(DOMAIN)를 읽지 못했습니다. Nginx가 뜨지 못했을 수 있으니 cd ~/be/deploy && docker compose ps 와 docker compose logs nginx 로 봅니다"; stop; }
[ -n "$domain" ] || { echo "Nginx 컨테이너의 서버 주소(DOMAIN)가 비어 있습니다"; stop; }
curl -fs -o /dev/null --resolve "$domain:443:127.0.0.1" --max-time 10 --retry 60 --retry-delay 3 --retry-max-time 180 --retry-all-errors "https://$domain/api/docs" \
  || { echo "https://$domain/api/docs 가 3분 안에 응답하지 않았습니다"; stop; }

trap '' INT TERM
record 성공
# 이제 배포는 끝났으니 정리 도중 Ctrl-C로 끊어도 성공(0)으로 끝낸다
trap 'echo "정리를 그만둡니다. ${kind}는 끝났습니다" >&2; exit 0' INT TERM
if [ "$kind" = 배포 ]; then
  echo "배포했습니다: $(git rev-parse --short=12 HEAD) (배포 전 백업: $backup)"
  step "7/7 앞 이미지와 오래된 백업을 정리합니다"
else
  echo "배포했습니다: $(git rev-parse --short=12 HEAD) (앞 버전으로 되돌림, 되돌리기 전 백업: $backup)"
  # 되돌리기는 되살려야 할 백업이 지워지지 않게 백업을 정리하지 않는다
  step "7/7 앞 이미지를 정리합니다(백업은 지우지 않습니다)"
fi
# 빌드 전 이미지에 붙여 둔 이름을 뗀다. 이미지 내용이 같아 Compose가 서버 컨테이너를 그대로 두었으면(출처 증명을 붙여
# 빌드한 이미지에서 넘어오는 첫 배포, 손으로 docker compose up -d --build 한 뒤의 배포 등) 그 이미지를 아직 써서 떼지
# 못한다. 그 이름은 다음 배포가 3단계에서 옮기니 실패로 치지 않는다(#59)
[ -z "$saved_app_image" ] || docker rmi "$app_image:before-deploy" >/dev/null 2>&1
{ docker image prune -f && docker builder prune -f --filter until=168h; } \
  || echo "${kind}는 끝났고, 앞 이미지 정리만 실패했습니다. 디스크가 차면 위 출력을 봅니다"
if [ "$kind" = 배포 ]; then
  # shellcheck disable=SC2012 # 지울 파일 이름은 이 스크립트가 만드는 mwt-<시각>.sql 꼴뿐이다
  ls -1t "$HOME"/mwt-*.sql | tail -n +11 | while IFS= read -r f; do rm -- "$f" || exit 1; done \
    || echo "배포는 끝났고, 오래된 백업 정리만 실패했습니다. 디스크가 차면 위 출력을 봅니다"
fi
exit 0
