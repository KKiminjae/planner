#!/usr/bin/env python3
"""Read-only authenticated API probe: 24 requests, at most 3 in parallel."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import copy
from datetime import datetime
import getpass
import http.cookiejar
import json
import pathlib
import ssl
import statistics
import time
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', required=True)
    base = parser.parse_args().base_url.rstrip('/')
    if not base.startswith('https://'):
        raise ValueError('Remote API probe requires HTTPS')
    context = ssl.create_default_context()
    if pathlib.Path('/etc/ssl/cert.pem').is_file():
        context.load_verify_locations(cafile='/etc/ssl/cert.pem')
    jar = http.cookiejar.CookieJar()

    def opener(cookies):
        return urllib.request.build_opener(urllib.request.HTTPSHandler(context=context),
                                          urllib.request.HTTPCookieProcessor(cookies))

    client = opener(jar)
    with client.open(base + '/api/auth/csrf', timeout=20) as response:
        csrf = json.load(response)
    password = getpass.getpass('앱 로그인 비밀번호: ')
    request = urllib.request.Request(base + '/api/auth/login',
        data=urllib.parse.urlencode({'username': 'owner', 'password': password}).encode(),
        headers={csrf['headerName']: csrf['token'], 'Content-Type': 'application/x-www-form-urlencoded'})
    del password
    with client.open(request, timeout=20) as response:
        assert response.status == 200
    del request
    now = datetime.now(ZoneInfo('Asia/Seoul'))
    paths = ['/api/categories', '/api/records?date=' + now.date().isoformat(),
             f'/api/calendar/integrated?year={now.year}&month={now.month}'] * 8
    print('읽기 확인 시작(KST):', now.isoformat(), flush=True)
    print('조회만 수행합니다: 총 24회, 최대 동시 3회.', flush=True)
    try:
        def read(path):
            cookies = http.cookiejar.CookieJar()
            for cookie in jar:
                cookies.set_cookie(copy.copy(cookie))
            started = time.monotonic()
            with opener(cookies).open(base + path, timeout=30) as response:
                assert response.status == 200
                assert isinstance(json.load(response), list)
            return time.monotonic() - started
        with ThreadPoolExecutor(max_workers=3) as pool:
            elapsed = list(pool.map(read, paths))
        print('조회 HTTP 200: 24/24')
        print(f'평균 {statistics.mean(elapsed):.3f}초 / 최대 {max(elapsed):.3f}초')
    finally:
        with client.open(base + '/api/auth/csrf', timeout=20) as response:
            csrf = json.load(response)
        with client.open(urllib.request.Request(base + '/api/auth/logout', data=b'',
                headers={csrf['headerName']: csrf['token']}), timeout=20) as response:
            assert response.status == 204
    print('읽기 확인 완료. 데이터는 생성·수정·삭제하지 않았습니다.')


if __name__ == '__main__':
    main()
