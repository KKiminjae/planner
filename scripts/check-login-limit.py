#!/usr/bin/env python3
"""Check public nginx login throttling without real credentials or data changes."""
import argparse
import http.cookiejar
import json
import pathlib
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', required=True)
    args = parser.parse_args()
    base = args.base_url.rstrip('/')
    if not base.startswith('https://'):
        raise ValueError('Public rate-limit verification requires HTTPS.')
    context = ssl.create_default_context()
    if pathlib.Path('/etc/ssl/cert.pem').is_file():
        context.load_verify_locations(cafile='/etc/ssl/cert.pem')
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),
        urllib.request.HTTPSHandler(context=context))

    def call(path, data=None, headers=None):
        request = urllib.request.Request(base + path, data=data, headers=headers or {})
        try:
            response = opener.open(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.code, response.headers, json.loads(response.read())

    status, headers, csrf = call('/api/auth/csrf', headers={
        'Forwarded': 'for=192.0.2.1;proto=http;host=invalid.example',
        'X-Forwarded-Proto': 'http', 'X-Forwarded-Host': 'invalid.example',
        'X-Forwarded-Port': '80', 'X-Forwarded-Prefix': '/fake'})
    assert status == 200, f'CSRF status: {status}'
    assert csrf['headerName'] == 'X-CSRF-TOKEN'
    assert 'Secure' in (headers.get('Set-Cookie') or ''), 'Secure session cookie missing'
    print('CSRF issuance and Secure cookie: passed with forged proxy headers')
    data = urllib.parse.urlencode({'username': 'limit-check-' + uuid.uuid4().hex[:8],
                                  'password': uuid.uuid4().hex}).encode()
    status, _, body = call('/api/auth/login', data, {
        'Content-Type': 'application/x-www-form-urlencoded'})
    assert status == 403 and body['code'] == 'REQUEST_FORBIDDEN', (status, body['code'])
    print('Missing CSRF rejected: HTTP 403')
    codes = []
    for index in range(6):
        status, response_headers, body = call('/api/auth/login', data, {
            'Content-Type': 'application/x-www-form-urlencoded',
            csrf['headerName']: csrf['token'],
            'X-Forwarded-For': f'192.0.2.{index + 1}',
            'X-Real-IP': f'192.0.2.{index + 1}',
            'Forwarded': f'for=192.0.2.{index + 1};proto=http'})
        codes.append(status)
        if status == 429:
            assert body['code'] == 'LOGIN_RATE_LIMITED'
            assert response_headers.get('Retry-After') == '12'
            assert response_headers.get('Cache-Control') == 'no-store'
        else:
            assert status == 401 and body['code'] == 'INVALID_CREDENTIALS', (status, body['code'])
    assert codes[:4] == [401] * 4 and codes[4:] == [429] * 2, codes
    print('Repeated attempts with changing forged IP headers:', codes)
    status, _, _ = call('/api/auth/csrf')
    assert status == 200
    status, _, body = call('/api/auth/me')
    assert status == 401 and body['code'] == 'AUTHENTICATION_REQUIRED'
    print('Other auth endpoints remain available during throttling')
    print('Waiting 13 seconds to check recovery...', flush=True)
    time.sleep(13)
    status, _, body = call('/api/auth/login', data, {
        'Content-Type': 'application/x-www-form-urlencoded', csrf['headerName']: csrf['token']})
    assert status == 401 and body['code'] == 'INVALID_CREDENTIALS'
    print('Login attempt allowed again: HTTP 401 (invalid test credentials)')
    print('Rate-limit verification passed. Real owner login must be checked separately.')


if __name__ == '__main__':
    main()
