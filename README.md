# GitHub 檔案上傳頁

這個專案現在是一個簡單的 Java 上傳網站。

你可以打開網頁、選檔案、按下上傳，後端會直接呼叫 GitHub API，把檔案送進指定 repository。

## 這個專案做了什麼

1. 提供一個上傳頁面
2. 接收使用者上傳的檔案
3. 用 GitHub Contents API 把檔案寫進 repository
4. 如果目標檔案已存在，會自動覆蓋更新

## 先設定 GitHub Token

你需要建立一個 GitHub Personal Access Token，至少要有對該 repository 的內容寫入權限。

如果是一般私人或公開 repo，最實用的是：

1. 到 GitHub `Settings`
2. 進入 `Developer settings`
3. 進入 `Personal access tokens`
4. 建立 token
5. 給它 repository contents 的寫入權限

## 啟動方式

在 macOS 或 Linux 終端機中：

```bash
cd "/Users/leo/Documents/New project"
export GITHUB_TOKEN="你的 GitHub Token"
export GITHUB_OWNER="w837501"
export GITHUB_REPO="CodexTest"
export GITHUB_BRANCH="master"
gradle run
```

如果你沒有設定 `GITHUB_BRANCH`，系統會預設使用 `master`。

啟動後打開：

```text
http://localhost:8080
```

## 頁面如何使用

1. 選擇要上傳的檔案
2. 輸入 GitHub 儲存路徑
3. 按下「上傳到 GitHub」

### 路徑範例

如果你輸入：

```text
uploads/report.pdf
```

檔案就會上傳到 repository 的：

```text
uploads/report.pdf
```

如果你留空，系統會自動放到：

```text
uploads/原始檔名
```

## 另一台電腦如何下載

另一台電腦只要抓這個 repo 最新版本即可：

```bash
git clone git@github.com:w837501/CodexTest.git
cd CodexTest
git pull origin master
```

如果只是要下載某個上傳後的檔案，也可以直接在 GitHub 網頁上打開檔案後下載。

## 注意

1. 不要把 `GITHUB_TOKEN` 寫死在前端頁面裡，否則會外洩
2. 這個上傳頁面必須透過 Java 伺服器執行，不能只靠純 GitHub Pages 完成安全上傳
3. GitHub Pages 適合展示靜態頁面，但不適合直接安全地持有 GitHub 寫入權限
