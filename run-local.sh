#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")"

required_vars=(
  GITHUB_TOKEN
  GITHUB_OWNER
  GITHUB_REPO
)

missing=()
for var_name in "${required_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    missing+=("$var_name")
  fi
done

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "缺少必要環境變數：${missing[*]}"
  echo "請先在終端機設定，例如："
  echo 'export GITHUB_TOKEN="你的 GitHub Token"'
  echo 'export GITHUB_OWNER="w837501"'
  echo 'export GITHUB_REPO="CodexTest"'
  echo 'export GITHUB_BRANCH="master"'
  exit 1
fi

export GITHUB_BRANCH="${GITHUB_BRANCH:-master}"

echo "啟動本機測試站..."
echo "Repository: ${GITHUB_OWNER}/${GITHUB_REPO}"
echo "Branch: ${GITHUB_BRANCH}"
echo "網址: http://localhost:8080"

gradle run
