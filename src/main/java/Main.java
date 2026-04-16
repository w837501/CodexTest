import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final Path INDEX_PATH = Path.of("docs", "index.html");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private static final Pattern SHA_PATTERN = Pattern.compile("\"sha\"\\s*:\\s*\"([^\"]+)\"");

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", Main::handleHome);
        server.createContext("/health", Main::handleHealth);
        server.createContext("/upload", Main::handleUpload);
        server.setExecutor(null);
        server.start();

        System.out.println("Server started at http://localhost:8080");
    }

    private static void handleHome(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            sendText(exchange, 405, "只支援 GET");
            return;
        }

        byte[] response = Files.readAllBytes(INDEX_PATH);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        if ("HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, response.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            sendJson(exchange, 405, "{\"ok\":false,\"message\":\"只支援 GET\"}");
            return;
        }

        String body = "{\"ok\":true,\"message\":\"server-ready\"}";
        if ("HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        sendJson(exchange, 200, body);
    }

    private static void handleUpload(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"ok\":false,\"message\":\"只支援 POST\"}");
            return;
        }

        try {
            GitHubConfig config = GitHubConfig.fromEnvironment();
            MultipartUpload upload = parseMultipart(exchange);
            String targetPath = buildTargetPath(upload.targetPath(), upload.fileName());
            String sha = findExistingSha(config, targetPath);
            GitHubUploadResult result = uploadFileToGitHub(config, targetPath, upload.fileName(), upload.content(), sha);

            String message = jsonObject(Map.of(
                    "ok", "true",
                    "message", jsonString("檔案已成功上傳到 GitHub"),
                    "path", jsonString(targetPath),
                    "url", jsonString(result.htmlUrl())
            ));
            sendJson(exchange, 200, message);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, jsonObject(Map.of(
                    "ok", "false",
                    "message", jsonString(e.getMessage())
            )));
        } catch (IOException e) {
            throw e;
            } catch (Exception e) {
                sendJson(exchange, 500, jsonObject(Map.of(
                    "ok", "false",
                    "message", jsonString("上傳失敗：" + e.getMessage())
                )));
            }
    }

    private static MultipartUpload parseMultipart(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            throw new IllegalArgumentException("請使用 multipart/form-data 上傳檔案");
        }

        String boundary = extractBoundary(contentType);
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        String raw = new String(requestBody, StandardCharsets.ISO_8859_1);
        String delimiter = "--" + boundary;

        String fileName = null;
        byte[] fileContent = null;
        String targetPath = "";

        int position = 0;
        while (true) {
            int boundaryStart = raw.indexOf(delimiter, position);
            if (boundaryStart < 0) {
                break;
            }

            int partStart = boundaryStart + delimiter.length();
            if (raw.startsWith("--", partStart)) {
                break;
            }
            if (raw.startsWith("\r\n", partStart)) {
                partStart += 2;
            }

            int headerEnd = raw.indexOf("\r\n\r\n", partStart);
            if (headerEnd < 0) {
                break;
            }

            String headerBlock = raw.substring(partStart, headerEnd);
            int contentStart = headerEnd + 4;
            int nextBoundary = raw.indexOf("\r\n" + delimiter, contentStart);
            if (nextBoundary < 0) {
                nextBoundary = raw.indexOf(delimiter, contentStart);
            }
            if (nextBoundary < 0) {
                break;
            }

            String content = raw.substring(contentStart, nextBoundary);
            Map<String, String> disposition = parseDisposition(headerBlock);
            String fieldName = disposition.get("name");
            if ("targetPath".equals(fieldName)) {
                targetPath = content.trim();
            } else if ("file".equals(fieldName)) {
                fileName = disposition.get("filename");
                fileContent = content.getBytes(StandardCharsets.ISO_8859_1);
            }

            position = nextBoundary + 2;
        }

        if (fileName == null || fileName.isBlank() || fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("請選擇要上傳的檔案");
        }

        return new MultipartUpload(fileName, fileContent, targetPath);
    }

    private static Map<String, String> parseDisposition(String headers) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-disposition:")) {
                String[] parts = line.substring("Content-Disposition:".length()).split(";");
                for (String part : parts) {
                    String item = part.trim();
                    int equalsIndex = item.indexOf('=');
                    if (equalsIndex > 0) {
                        String key = item.substring(0, equalsIndex).trim();
                        String value = item.substring(equalsIndex + 1).trim().replaceAll("^\"|\"$", "");
                        values.put(key, value);
                    }
                }
            }
        }
        return values;
    }

    private static String extractBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                return trimmed.substring("boundary=".length()).replace("\"", "");
            }
        }
        throw new IllegalArgumentException("找不到 multipart boundary");
    }

    private static String buildTargetPath(String requestedPath, String fileName) {
        String cleanFileName = Path.of(fileName).getFileName().toString();
        String cleanPath = requestedPath == null ? "" : requestedPath.trim().replace('\\', '/');
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }
        if (cleanPath.contains("..")) {
            throw new IllegalArgumentException("上傳路徑不能包含 ..");
        }

        if (cleanPath.isBlank()) {
            return "uploads/" + cleanFileName;
        }
        if (cleanPath.endsWith("/")) {
            return cleanPath + cleanFileName;
        }
        return cleanPath;
    }

    private static String findExistingSha(GitHubConfig config, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(contentsUri(config, path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + config.token())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() >= 300) {
            throw new IllegalArgumentException("查詢 GitHub 檔案失敗，HTTP " + response.statusCode());
        }

        Matcher matcher = SHA_PATTERN.matcher(response.body());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static GitHubUploadResult uploadFileToGitHub(
            GitHubConfig config,
            String path,
            String fileName,
            byte[] fileContent,
            String sha
    ) throws IOException, InterruptedException {
        String payload = buildUploadJson(config, path, fileName, fileContent, sha);

        HttpRequest request = HttpRequest.newBuilder(contentsUri(config, path))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + config.token())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Content-Type", "application/json; charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 300) {
            throw new IllegalArgumentException("GitHub API 回傳 HTTP " + response.statusCode() + "：請檢查 token、repo 權限或 branch 設定");
        }

        return new GitHubUploadResult(buildHtmlUrl(config, path));
    }

    private static String buildUploadJson(GitHubConfig config, String path, String fileName, byte[] fileContent, String sha) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("message", jsonString("Upload " + fileName + " via web page"));
        values.put("content", jsonString(Base64.getEncoder().encodeToString(fileContent)));
        values.put("branch", jsonString(config.branch()));
        if (sha != null && !sha.isBlank()) {
            values.put("sha", jsonString(sha));
        }
        return jsonObject(values);
    }

    private static URI contentsUri(GitHubConfig config, String path) {
        String encodedPath = encodePath(path);
        return URI.create("https://api.github.com/repos/"
                + encodeSegment(config.owner())
                + "/"
                + encodeSegment(config.repo())
                + "/contents/"
                + encodedPath);
    }

    private static String buildHtmlUrl(GitHubConfig config, String path) {
        return "https://github.com/" + config.owner() + "/" + config.repo() + "/blob/" + config.branch() + "/" + path;
    }

    private static String encodePath(String path) {
        String[] segments = path.split("/");
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                encoded.append('/');
            }
            encoded.append(encodeSegment(segments[i]));
        }
        return encoded.toString();
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private static String jsonObject(Map<String, String> entries) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escapeJson(entry.getKey())).append('"').append(':').append(entry.getValue());
        }
        builder.append('}');
        return builder.toString();
    }

    private static String jsonString(String value) {
        return "\"" + escapeJson(value) + "\"";
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private record MultipartUpload(String fileName, byte[] content, String targetPath) {
    }

    private record GitHubUploadResult(String htmlUrl) {
    }

    private record GitHubConfig(String token, String owner, String repo, String branch) {
        private static GitHubConfig fromEnvironment() {
            String token = requireEnv("GITHUB_TOKEN");
            String owner = requireEnv("GITHUB_OWNER");
            String repo = requireEnv("GITHUB_REPO");
            String branch = System.getenv().getOrDefault("GITHUB_BRANCH", "master");
            return new GitHubConfig(token, owner, repo, branch);
        }

        private static String requireEnv(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("缺少環境變數：" + name);
            }
            return value;
        }
    }
}
