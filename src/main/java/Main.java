import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", Main::handleHome);
        server.setExecutor(null);
        server.start();

        System.out.println("Server started at http://localhost:8080");
    }

    private static void handleHome(HttpExchange exchange) throws IOException {
        String html = """
                <!DOCTYPE html>
                <html lang="zh-Hant">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Hello</title>
                </head>
                <body>
                    <h1>Hello World</h1>
                    <p>這是你的第一個 Java 網頁。</p>
                </body>
                </html>
                """;
        byte[] response = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }
}
