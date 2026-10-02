#!/usr/bin/env bash
set -euo pipefail
umask 077
login_entry=$(htpasswd -nBC 12 owner)
login_hash=${login_entry#owner:}
if [[ ! "$login_hash" =~ ^\$2[aby]\$12\$ ]]; then
  echo '로그인 해시 생성 실패' >&2
  exit 1
fi
printf 'APP_LOGIN_USERNAME=owner\nAPP_LOGIN_PASSWORD_HASH=%s\n' "$login_hash" > auth.env
chmod 600 auth.env
unset login_entry login_hash
echo '서버 로그인 설정 저장 완료. 비밀번호·해시는 출력하지 않습니다.'
