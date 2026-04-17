# Java Hello World

這是一個使用 Gradle 結構的最小 Java 網頁專案，另外也附了一個可部署到 GitHub Pages 的靜態首頁。

## 結構

```text
build.gradle
settings.gradle
src/main/java/Main.java
docs/index.html
```

## 執行

如果你的環境已安裝 Gradle：

```bash
gradle run
```

看到這行代表伺服器已啟動：

```text
Server started at http://localhost:8080
```

接著打開瀏覽器進入：

```text
http://localhost:8080
```

頁面會顯示 `Hello World` 和一段簡單文字。

## GitHub Pages

這個專案也包含一個靜態首頁：

```text
docs/index.html
```

在 GitHub repo 中啟用 Pages 後：

1. 打開 repository 的 `Settings`
2. 進入 `Pages`
3. `Build and deployment` 的 `Source` 選 `Deploy from a branch`
4. Branch 選 `master`
5. Folder 選 `/docs`

啟用後，網址通常會是：

```text
https://w837501.github.io/CodexTest/
```
