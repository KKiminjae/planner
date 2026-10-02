#!/usr/bin/env python3
"""Check local session login without printing passwords, cookies, or CSRF tokens."""
import argparse
import getpass
import http.cookiejar
import json
import pathlib
import ssl
import urllib.error
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', default='http://localhost:8080')
    base = parser.parse_args().base_url.rstrip('/')
    if not (base.startswith('https://') or base == 'http://localhost:8080'):
        raise ValueError('원격 서버 확인에는 HTTPS 주소를 사용하세요.')
    context = ssl.create_default_context()
    if pathlib.Path('/etc/ssl/cert.pem').is_file():
        context.load_verify_locations(cafile='/etc/ssl/cert.pem')
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),
        urllib.request.HTTPSHandler(context=context)
    )
    with opener.open(base + "/api/auth/csrf", timeout=15) as response:
        csrf = json.load(response)
    password = getpass.getpass("앱 로그인 비밀번호: ")
    request = urllib.request.Request(
        base + "/api/auth/login",
        data=urllib.parse.urlencode({"username": "owner", "password": password}).encode(),
        headers={csrf["headerName"]: csrf["token"],
                 "Content-Type": "application/x-www-form-urlencoded"},
    )
    with opener.open(request, timeout=15) as response:
        print("로그인: HTTP", response.status)
    with opener.open(base + "/api/auth/me", timeout=15) as response:
        account = json.load(response)
        print("세션 확인: HTTP", response.status, "사용자:", account["username"])
    with opener.open(base + "/api/auth/csrf", timeout=15) as response:
        csrf = json.load(response)
    request = urllib.request.Request(
        base + "/api/auth/logout", data=b"",
        headers={csrf["headerName"]: csrf["token"]},
    )
    with opener.open(request, timeout=15) as response:
        print("로그아웃: HTTP", response.status)


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as error:
        print("요청 실패: HTTP", error.code)
        raise SystemExit(1)
    except (urllib.error.URLError, TimeoutError):
        print("앱 연결 실패: localhost:8080에서 실행 중인지 확인하세요.")
        raise SystemExit(1)
    except (KeyboardInterrupt, EOFError):
        print("\n확인을 취소했습니다.")
        raise SystemExit(1)
    except ValueError as error:
        print(str(error))
        raise SystemExit(1)
