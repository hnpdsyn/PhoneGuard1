#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LoginGuard v1.1 打包脚本
- 用 python3 zipfile 打包整个工程为 ../LoginGuard.zip（覆盖旧版）
- 每条 ZipInfo 固定 date_time=(2026,9,13,0,0,0)（沙箱文件时间戳为 1970 会触发
  "ZIP does not support timestamps before 1980" 报错）
- 排除 app/build/、.gradle/、local.properties
- gradlew、gradlew.bat、build_apk.bat、scripts/*.sh 的 external_attr 设为 0o755<<16 保证可执行位
"""
import os
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))  # LoginGuard 工程根目录
OUT = os.path.join(os.path.dirname(ROOT), "LoginGuard.zip")

FIXED_DT = (2026, 9, 13, 0, 0, 0)

# 排除目录与文件
EXCLUDE_DIRS = {"build", ".gradle", ".idea"}
EXCLUDE_FILES = {"local.properties", "package_zip.py"}  # 打包脚本自身不入包（与兄弟工程保持一致）
# 可执行位文件
EXEC_FILES = {"gradlew", "gradlew.bat", "build_apk.bat"}
EXEC_EXT = {".sh"}


def main():
    count = 0
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as zf:
        for dirpath, dirnames, filenames in os.walk(ROOT):
            # 原地过滤排除目录（控制递归）
            dirnames[:] = [d for d in dirnames if d not in EXCLUDE_DIRS]

            # 目录条目（同样使用固定时间戳）
            for name in sorted(dirnames):
                full = os.path.join(dirpath, name)
                rel = os.path.relpath(full, ROOT).replace(os.sep, "/")
                info = zipfile.ZipInfo(rel + "/", FIXED_DT)
                info.external_attr = (0o755 << 16) | 0x10  # drwxr-xr-x + 目录标志
                zf.writestr(info, "")
                count += 1

            # 文件条目
            for name in sorted(filenames):
                rel = os.path.relpath(os.path.join(dirpath, name), ROOT).replace(os.sep, "/")
                if name in EXCLUDE_FILES:
                    continue
                info = zipfile.ZipInfo(rel, FIXED_DT)
                if name in EXEC_FILES or os.path.splitext(name)[1].lower() in EXEC_EXT:
                    info.external_attr = 0o755 << 16  # rwxr-xr-x
                else:
                    info.external_attr = 0o644 << 16  # rw-r--r--
                with open(os.path.join(dirpath, name), "rb") as f:
                    zf.writestr(info, f.read())
                count += 1

    print("packed:", OUT)
    print("entries:", count)
    print("size:", os.path.getsize(OUT), "bytes")


if __name__ == "__main__":
    main()
