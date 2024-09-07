package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {
    private final int port;
    private final List<String> validPaths;
    private final ExecutorService threadPool;
    private volatile boolean isRunning = true;
    private ServerSocket serverSocket;
    public Server(int port) {
        this.port = port;
        this.validPaths = List.of(
                "/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html",
                "/forms.html", "/classic.html", "/events.html", "/events.js"
        );
        this.threadPool = Executors.newFixedThreadPool(64); // создаем пул на 64 потока
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);  // Инициализируем серверный сокет
            System.out.println("Сервер запущен на порту: " + port);

            // Отдельный поток для прослушивания ввода команд из консоли
            new Thread(this::handleConsoleInput).start();

            while (isRunning) {
                try {
                    // Ожидание нового подключения
                    final var clientSocket = serverSocket.accept();
                    // Передача задачи в пул потоков для обработки
                    threadPool.submit(() -> handleConnection(clientSocket));
                } catch (IOException e) {
                    if (isRunning) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            if (isRunning) {
                e.printStackTrace();
            }
        }
    }


    private void handleConnection(Socket clientSocket) {
        try (
                final var in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                final var out = new BufferedOutputStream(clientSocket.getOutputStream())
        ) {
            // Читаем первую строку запроса
            final var requestLine = in.readLine();
            if (requestLine == null) return;

            final var parts = requestLine.split(" ");
            if (parts.length != 3) {
                // если неверный формат запроса, закрываем соединение
                return;
            }

            final var path = parts[1]; // Получаем запрашиваемый путь
            if (!validPaths.contains(path)) {
                sendResponse(out, "404 Not Found", 0, "text/plain", null);
                return;
            }

            final var filePath = Path.of(".", "public", path);
            final var mimeType = Files.probeContentType(filePath);

            // Специальная обработка для /classic.html
            if (path.equals("/classic.html")) {
                final var template = Files.readString(filePath);
                final var content = template.replace("{time}", LocalDateTime.now().toString()).getBytes();
                sendResponse(out, "200 OK", content.length, mimeType, content);
            } else {
                final var length = Files.size(filePath);
                sendResponse(out, "200 OK", length, mimeType, Files.readAllBytes(filePath));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendResponse(BufferedOutputStream out, String status, long length, String mimeType, byte[] content) throws IOException {
        var response = "HTTP/1.1 " + status + "\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        out.write(response.getBytes());
        if (content != null) {
            out.write(content);
        }
        out.flush();
    }

    public void stop() {
        System.out.println("Остановка сервера...");
        isRunning = false;
        threadPool.shutdown(); // Останавливаем пул потоков

        // Закрываем серверный сокет
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Даем пулу потоков 5 секунд на завершение
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("Некоторые потоки не завершились, принудительная остановка...");
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("Ошибка при остановке потоков: " + e.getMessage());
            threadPool.shutdownNow();
        }

        System.out.println("Сервер успешно остановлен.");
    }

    // Обработка команд из консоли
    private void handleConsoleInput() {
        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            String command;
            while ((command = consoleReader.readLine()) != null) {
                if (command.equalsIgnoreCase("\\exit")) {
                    stop(); // Завершаем работу сервера
                    System.out.println("Команда \\exit введена. Завершение работы сервера.");
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
