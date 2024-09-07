package ru.netology;

public class Main {
    public static void main(String[] args) {
        Server server = new Server(9999);  // Инициализация сервера

        // Добавляем хендлеры
        server.addHandler("GET", "/messages", (request, out) -> {
            String content = "Messages handler called with GET method";
            server.sendResponse(out, "200 OK", content.length(), "text/plain", content.getBytes());
        });

        server.addHandler("POST", "/messages", (request, out) -> {
            String content = "Messages handler called with POST method";
            server.sendResponse(out, "200 OK", content.length(), "text/plain", content.getBytes());
        });

        server.start();  // Запуск сервера
    }
}