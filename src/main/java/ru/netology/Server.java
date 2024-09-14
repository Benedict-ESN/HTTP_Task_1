package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {
    private final int port;
    private final ExecutorService threadPool;
    private volatile boolean isRunning = true;
    private ServerSocket serverSocket;

    private final Map<String, Map<String, Handler>> handlers = new HashMap<>();

    public Server(int port) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(64); // создаем пул на 64 потока
    }

    public void addHandler(String method, String path, Handler handler) {
        handlers.computeIfAbsent(method, k -> new HashMap<>()).put(path, handler);
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

            final var method = parts[0];
            final var uri = parts[1]; // Здесь содержится и путь, и Query String

            // Читаем заголовки
            String line;
            Map<String, String> headers = new HashMap<>();
            while (!(line = in.readLine()).isEmpty()) {
                var headerParts = line.split(": ");
                headers.put(headerParts[0], headerParts[1]);
            }

            // Чтение тела запроса
            StringBuilder body = new StringBuilder();
            while (in.ready()) {
                body.append((char) in.read());
            }

            // Создаем объект Request с разбором Query String
            Request request = new Request(method, uri, body.toString());

            // Поиск хендлера по пути
            Handler handler = findHandler(method, request.getPath());
            if (handler != null) {
                handler.handle(request, out);
            } else {
                handleStaticFile(request.getPath(), out);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleStaticFile(String path, BufferedOutputStream out) throws IOException {
        // Если путь пустой или "/", выведем список файлов в директории public
        if (path.equals("/") || path.isEmpty()) {
            var publicDir = Path.of(".", "public");
            if (Files.exists(publicDir) && Files.isDirectory(publicDir)) {
                StringBuilder content = new StringBuilder("<html><body><h1>Список файлов:</h1><ul>");
                Files.list(publicDir).forEach(file -> {
                    var fileName = file.getFileName().toString();
                    content.append("<li><a href=\"")
                            .append(fileName)
                            .append("\">")
                            .append(fileName)
                            .append("</a></li>");
                });
                content.append("</ul></body></html>");

                // Отправляем список файлов как HTML
                byte[] contentBytes = content.toString().getBytes();
                sendResponse(out, "200 OK", contentBytes.length, "text/html", contentBytes);
            } else {
                // Если директория public не найдена
                sendResponse(out, "404 Not Found", 0, "text/plain", null);
            }
        } else {
            // Обработка запроса на конкретный файл
            final var filePath = Path.of(".", "public", path);
            if (Files.exists(filePath)) {
                final var mimeType = Files.probeContentType(filePath);
                final var length = Files.size(filePath);
                sendResponse(out, "200 OK", length, mimeType, Files.readAllBytes(filePath));
            } else {
                // Если файл не найден
                sendResponse(out, "404 Not Found", 0, "text/plain", null);
            }
        }
    }


    private Handler findHandler(String method, String path) {
        Map<String, Handler> methodHandlers = handlers.get(method);
        if (methodHandlers != null) {
            return methodHandlers.get(path);
        }
        return null;
    }

    public void sendResponse(BufferedOutputStream out, String status, long length, String mimeType, byte[] content) throws IOException {
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

        // 5 секунд на завершение
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