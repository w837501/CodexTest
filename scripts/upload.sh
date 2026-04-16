#!/usr/bin/env bash

set -euo pipefail

if [[ $# -eq 0 ]]; then
  echo "用法: ./scripts/upload.sh \"提交訊息\""
  exit 1
fi

message="$1"

git add .

if git diff --cached --quiet; then
  echo "沒有新的變更可上傳。"
  exit 0
fi

git commit -m "$message"
git push origin master

echo "已上傳到 GitHub。"
