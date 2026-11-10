#!/usr/bin/env bash
# GitHub Actions 배포 버튼(.github/workflows/deploy.yml)이 배포 버튼 키로 접속하면 이 스크립트만 돈다
# (서버 계정의 ~/.ssh/authorized_keys에서 그 키 줄 앞에 command="…" 로 묶는다. README "버튼으로 재배포").
# 접속한 쪽이 ssh 뒤에 적어 보낸 명령(SSH_ORIGINAL_COMMAND)은 실행하지 않고, 정해 둔 낱말 둘만 받는다:
#
#   deploy   main을 받아 재배포한다(deploy.sh). 종료 코드는 deploy.sh의 것 그대로다
#   status   배포가 아직 도는지, 배포 기록 마지막 5줄, 배포 출력 마지막 30줄을 찍는다
#
# 그 밖의 명령은 아무것도 하지 않고 2로 끝난다. 명령 없이 접속한 것(키를 시험하려고 그냥 ssh로 붙은 것 등)도
# 재배포로 받지 않고 거절한다.
set -u

case "${SSH_ORIGINAL_COMMAND:-}" in
  deploy)
    exec "$(dirname "$(readlink -f "$0")")/deploy.sh"
    ;;
  status)
    if flock -n "$HOME/.mwt-deploy.lock" true; then
      echo "배포(또는 백업 되살리기)가 돌고 있지 않습니다"
    else
      echo "배포(또는 백업 되살리기)가 아직 돌고 있습니다"
    fi
    echo "== 배포 기록(~/mwt-deploys.log) 마지막 5줄"
    tail -n 5 "$HOME/mwt-deploys.log" 2>/dev/null || echo "(배포 기록이 아직 없습니다)"
    echo "== 배포 출력(~/mwt-deploy-output.log) 마지막 30줄"
    tail -n 30 "$HOME/mwt-deploy-output.log" 2>/dev/null || echo "(배포 출력이 아직 없습니다)"
    ;;
  *)
    echo "받지 않는 명령입니다. 이 키로는 deploy(재배포)나 status(결과 보기)만 보냅니다. 아무것도 바꾸지 않았습니다" >&2
    exit 2
    ;;
esac
