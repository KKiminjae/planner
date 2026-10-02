#!/usr/bin/env bash
set -euo pipefail
umask 077
if [[ -e .env ]]; then
  echo '기존 .env를 유지합니다.'
  exit 0
fi
# Refuse to overwrite a file that appears between the check and creation.
set -o noclobber
{
  printf 'MYSQL_PORT=3306\nMYSQL_DATABASE=photo_calendar\nMYSQL_USER=photo_calendar\n'
  printf 'MYSQL_PASSWORD=%s\n' "$(openssl rand -hex 24)"
  printf 'MYSQL_ROOT_PASSWORD=%s\n' "$(openssl rand -hex 24)"
} > .env
echo 'DB 환경 파일 생성 완료. 비밀번호는 출력하지 않습니다.'
