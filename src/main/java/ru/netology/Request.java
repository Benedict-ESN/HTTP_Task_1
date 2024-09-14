package ru.netology;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Request {
    private final String method;
    private final String path;
    private final Map<String, String> headers = new HashMap<>();
    private final String body;
    private final Map<String, List<String>> queryParams = new HashMap<>();

    public Request(String method, String uri, String body) {
        this.method = method;
        // Разделяем путь и параметры
        URI fullUri = URI.create(uri);
        this.path = fullUri.getPath();
        this.body = body;

        // Парсинг Query String
        List<NameValuePair> params = URLEncodedUtils.parse(fullUri, StandardCharsets.UTF_8);
        for (NameValuePair param : params) {
            queryParams.computeIfAbsent(param.getName(), k -> new java.util.ArrayList<>()).add(param.getValue());
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    // Метод для получения всех параметров запроса
    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }

    // Метод для получения значения конкретного параметра
    public List<String> getQueryParam(String name) {
        return queryParams.getOrDefault(name, List.of());
    }
}