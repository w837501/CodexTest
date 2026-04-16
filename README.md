# GitHub Pages 檔案上傳頁

這個專案現在改成適合 `GitHub Pages + Cloudflare Worker` 的架構。

你把前端部署到 GitHub Pages，使用者在頁面上選檔案後，Cloudflare Worker 會幫你安全地呼叫 GitHub API，把檔案送進指定 repository。

## 架構

1. `docs/index.html` 是 GitHub Pages 前端
2. `cloudflare-worker/src/index.js` 是上傳 API
3. Worker 會用 GitHub Contents API 把檔案寫進 repository
4. 如果目標檔案已存在，Worker 會自動覆蓋更新

## 為什麼要加 Worker

GitHub Pages 是靜態頁面，不能安全地保存 `GITHUB_TOKEN`。  
所以正確做法是：

1. 前端頁面放在 GitHub Pages
2. GitHub Token 放在 Cloudflare Worker secret
3. 上傳時由 Worker 代你寫入 GitHub

## 部署前準備

你需要先建立一個 GitHub Personal Access Token，至少要有 repository contents 寫入權限。

## 部署 Cloudflare Worker

進入 Worker 目錄：

```bash
cd "/Users/leo/Documents/New project/cloudflare-worker"
```

設定 GitHub Token secret：

```bash
wrangler secret put GITHUB_TOKEN
```

如果你的 repo / branch 不是預設值，修改 [cloudflare-worker/wrangler.toml](/Users/leo/Documents/New%20project/cloudflare-worker/wrangler.toml:1)：

```toml
GITHUB_OWNER = "w837501"
GITHUB_REPO = "CodexTest"
GITHUB_BRANCH = "master"
```

部署 Worker：

```bash
wrangler deploy
```

部署後你會拿到一個網址，像這樣：

```text
https://github-upload-worker.<subdomain>.workers.dev
```

## GitHub Pages 如何使用

1. 打開你的 GitHub Pages 網址
2. 在 `Worker API 網址` 輸入剛剛部署好的 Worker URL
3. 選擇檔案
4. 輸入 GitHub 儲存路徑
5. 按下 `上傳到 GitHub`

頁面會把 Worker 網址存在瀏覽器，下次不用重填。

### 路徑範例

```text
uploads/report.pdf
```

如果路徑留空，系統會自動放到：

```text
uploads/原始檔名
```

## 檢查 Worker 是否正常

你可以直接打：

```bash
curl https://你的-worker-url/health
```

正常會回：

```json
{"ok":true,"message":"worker-ready"}
```

## 注意

1. 不要把 `GITHUB_TOKEN` 放進 `docs/index.html`
2. 真正有寫入權限的是 Worker，不是 GitHub Pages
3. 如果看到 `Failed to fetch`，通常是 Worker 網址錯了、Worker 還沒部署，或瀏覽器被 CORS 擋住
4. 目前 repo 內原本的 Java 範例還保留著，但 GitHub Pages 上傳功能不再依賴它
