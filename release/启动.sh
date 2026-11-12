#!/usr/bin/env sh
# 与本文件同目录放置 SpringBook.jar 后执行：sh 启动.sh
cd "$(dirname "$0")" || exit 1
exec java -jar SpringBook.jar
