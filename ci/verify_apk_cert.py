# -*- coding: utf-8 -*-
"""檢查 APK 的簽章憑證是不是專案固定的那一把（ci/expected-cert-sha256.txt）。
不一致就結束代碼 1：用臨時金鑰簽的 APK 會讓手機上已經裝的測試版蓋不上去，資料會因為重裝而遺失。
用法：python3 ci/verify_apk_cert.py <apk>"""
import hashlib
import io
import os
import struct
import sys


def u32(b, o):
    return struct.unpack_from('<I', b, o)[0]


def u64(b, o):
    return struct.unpack_from('<Q', b, o)[0]


def cert_sha256(path):
    """讀 APK 簽章區塊（v3 優先，其次 v2）裡第一張憑證的 SHA-256"""
    data = open(path, 'rb').read()
    eocd = data.rfind(b'PK\x05\x06')
    cd_off = u32(data, eocd + 16)
    if data[cd_off - 16:cd_off] != b'APK Sig Block 42':
        raise ValueError('APK 沒有簽章區塊（沒有簽章？）')
    size = u64(data, cd_off - 24)
    p = cd_off - size - 8 + 8
    end = cd_off - 24
    blocks = {}
    while p < end:
        ln = u64(data, p)
        blocks[u32(data, p + 8)] = data[p + 12:p + 8 + ln]
        p += 8 + ln
    for block_id in (0xf05368c0, 0x7109871a):
        if block_id in blocks:
            v = blocks[block_id]
            o = 8                                    # 跳過 signers 長度、第一個 signer 的長度
            sd = v[o + 4:o + 4 + u32(v, o)]          # signed data
            o2 = 4 + u32(sd, 0)                      # 跳過 digests
            o3 = o2 + 4                              # certificates 序列
            cert = sd[o3 + 4:o3 + 4 + u32(sd, o3)]
            return hashlib.sha256(cert).hexdigest()
    raise ValueError('找不到 v2／v3 簽章')


def main():
    # Windows 終端機預設的編碼印不出中文；Actions 上是 UTF-8，不受影響
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass
    apk = sys.argv[1]
    here = os.path.dirname(os.path.abspath(__file__))
    want = open(os.path.join(here, 'expected-cert-sha256.txt'), encoding='utf-8').read().strip().lower()
    got = cert_sha256(apk)
    print(f'APK 簽章 SHA-256：{got}')
    print(f'專案固定金鑰　　：{want}')
    if got != want:
        print('::error::APK 的簽章不是專案固定的那一把（可能沒有拿到 KEYSTORE 相關 Secrets，用了臨時金鑰）。'
              '這種 APK 不能覆蓋安裝，請不要給使用者。')
        sys.exit(1)
    print('簽章一致')


if __name__ == '__main__':
    main()
