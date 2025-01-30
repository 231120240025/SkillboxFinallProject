package searchengine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HtmlFetcher {

    public static String getHtml(String urlString) {
        StringBuilder result = new StringBuilder();
        try {
            // Создание объекта URL
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Установка метода запроса GET
            connection.setRequestMethod("GET");

            // Получение ответа
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                result.append(inputLine).append("\n");
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result.toString();
    }

    public static void printHtmlPreview(String url, String html) {
        System.out.println("HTML-код страницы: " + url);
        // Выводим только первые 500 символов для preview
        System.out.println(html.substring(0, Math.min(500, html.length())));
        System.out.println("...");
        System.out.println("=====================================\n");
    }

    public static void main(String[] args) {
        // Указание нужных URL
        String url1 = "https://www.playback.ru";
        String url2 = "https://www.ipfran.ru";

        // Получение HTML-кода страниц
        String html1 = getHtml(url1);
        String html2 = getHtml(url2);

        // Печать первых 500 символов HTML-кода
        printHtmlPreview(url1, html1);
        printHtmlPreview(url2, html2);
    }
}
