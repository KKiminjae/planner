#!/usr/bin/env python3
"""Check the running local photo API using disposable data and in-memory secrets."""
import argparse
import datetime
import getpass
import http.cookiejar
import json
import pathlib
import ssl
import struct
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib


def test_png():
    def chunk(kind, data):
        return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data))
    return (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('!IIBBBBB', 1, 1, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(b'\x00\x40\x80\xc0')) + chunk(b'IEND', b''))


def download_context():
    context = ssl.create_default_context()
    # python.org macOS builds may not have their optional CA bundle installed.
    # Keep TLS verification enabled and add the macOS CA bundle when available.
    system_bundle = pathlib.Path('/etc/ssl/cert.pem')
    if system_bundle.is_file():
        context.load_verify_locations(cafile=str(system_bundle))
    return context


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', default='http://localhost:8080')
    base = parser.parse_args().base_url.rstrip('/')
    if not (base.startswith('https://') or base == 'http://localhost:8080'):
        raise ValueError('원격 서버 확인에는 HTTPS 주소를 사용하세요.')
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),
        urllib.request.HTTPSHandler(context=download_context()))
    csrf = None

    def call(path, method='GET', data=None, content_type='application/json'):
        headers = {}
        if method != 'GET':
            headers[csrf['headerName']] = csrf['token']
            headers['Content-Type'] = content_type
        if isinstance(data, dict):
            data = json.dumps(data).encode()
        request = urllib.request.Request(base + path, data=data, headers=headers, method=method)
        with opener.open(request, timeout=90) as response:
            body = response.read()
            return response.status, json.loads(body) if body else None

    _, csrf = call('/api/auth/csrf')
    password = getpass.getpass('앱 로그인 비밀번호: ')
    call('/api/auth/login', 'POST', urllib.parse.urlencode({'username': 'owner', 'password': password}).encode(),
         'application/x-www-form-urlencoded')
    del password
    _, csrf = call('/api/auth/csrf')
    category_id = record_id = None
    try:
        png = test_png()
        boundary = 'PhotoCheck' + uuid.uuid4().hex
        multipart = (('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="check.png"\r\n'
                      'Content-Type: image/png\r\n\r\n').encode() + png + ('\r\n--' + boundary + '--\r\n').encode())
        status, photo = call('/api/photos', 'POST', multipart, 'multipart/form-data; boundary=' + boundary)
        print('사진 업로드: HTTP', status)
        _, category = call('/api/categories', 'POST', {
            'name': '사진 연결 확인 ' + uuid.uuid4().hex[:8], 'color': '#4080C0', 'isPrivate': True})
        category_id = category['id']
        now = datetime.datetime.now()
        status, record = call('/api/records', 'POST', {
            'categoryId': category_id, 'recordDate': now.date().isoformat(),
            'recordTime': now.strftime('%H:%M:%S'), 'memo': '자동 생성된 사진 API 확인 기록', 'imageKey': photo['imageKey']})
        record_id = record['id']
        print('사진을 기록에 연결: HTTP', status)
        status, signed = call('/api/records/' + str(record_id) + '/photo-url')
        print('사진 URL 발급: HTTP', status)
        # Use a separate client so application cookies are never sent to S3.
        with urllib.request.urlopen(signed['imageUrl'], timeout=60, context=download_context()) as response:
            if response.read() != png:
                raise ValueError('사진 내용 불일치')
            print('S3 이미지 다운로드 및 내용 일치: HTTP', response.status)
    finally:
        cleanup_failed = False
        if record_id is not None:
            try:
                status, _ = call('/api/records/' + str(record_id), 'DELETE')
                print('테스트 기록 삭제: HTTP', status)
            except Exception:
                cleanup_failed = True
                print('테스트 기록 정리 실패. 기록 ID:', record_id)
        if category_id is not None and not cleanup_failed:
            try:
                status, _ = call('/api/categories/' + str(category_id), 'DELETE')
                print('테스트 카테고리 삭제: HTTP', status)
            except Exception:
                cleanup_failed = True
                print('테스트 카테고리 정리 실패. 카테고리 ID:', category_id)
        call('/api/auth/logout', 'POST', b'')
        if cleanup_failed:
            raise RuntimeError('테스트 데이터 정리 실패')
    print('확인 완료. 테스트 사진은 연결 해제 후 24시간 유예를 거쳐 자동 정리 대상이 됩니다.')


if __name__ == '__main__':
    try:
        main()
    except urllib.error.HTTPError as error:
        print('요청 실패: HTTP', error.code)
        raise SystemExit(1)
    except urllib.error.URLError as error:
        if isinstance(error.reason, ssl.SSLCertVerificationError):
            print('HTTPS 인증서 검증 실패. Python 또는 시스템 CA 인증서 설정을 확인하세요.')
        else:
            print('연결 실패. 오류 종류:', type(error.reason).__name__)
        raise SystemExit(1)
    except TimeoutError:
        print('연결 시간 초과. 앱 상태와 네트워크를 확인하세요.')
        raise SystemExit(1)
    except (ValueError, RuntimeError) as error:
        print(str(error))
        raise SystemExit(1)
    except (KeyboardInterrupt, EOFError):
        print('\n확인을 취소했습니다.')
        raise SystemExit(1)
