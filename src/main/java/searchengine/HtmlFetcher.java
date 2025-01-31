package searchengine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import searchengine.config.SitesList;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

@SpringBootApplication
public class HtmlFetcher implements CommandLineRunner {
    private final SitesList sitesList;

    @Autowired
    public HtmlFetcher(SitesList sitesList) {
        this.sitesList = sitesList;
    }

    public void fetchAll() {
        if (sitesList.getSites() == null || sitesList.getSites().isEmpty()) {
            System.out.println("\n❌ Список сайтов пуст. Проверь конфигурацию! ❌\n");
            return;
        }

        System.out.println("\n🔍 Начинаем загрузку сайтов...\n");

        sitesList.getSites().forEach(site -> {
            System.out.println("📡 Загружаю сайт: " + site.getUrl());
            String html = fetchHtml(site.getUrl());

            if (html.startsWith("Ошибка загрузки")) {
                System.out.println("❌ Ошибка: " + html + "\n");
            } else {
                // Ограничиваем количество символов, чтобы вывести HTML в консоль более компактно
                String truncatedHtml = truncateHtml(html, 1000);  // выводим только первые 1000 символов
                System.out.println("✅ Успешно загружено: " + site.getUrl() + "\n");
                System.out.println("🔎 HTML (первые 1000 символов):\n" + truncatedHtml + "\n");
            }
        });

        System.out.println("🎉 Все сайты обработаны!\n");
    }

    private String fetchHtml(String siteUrl) {
        try {
            URL url = new URL(siteUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            Scanner scanner = new Scanner(connection.getInputStream());
            StringBuilder html = new StringBuilder();

            while (scanner.hasNext()) {
                html.append(scanner.nextLine()).append("\n");
            }

            scanner.close();
            return html.toString();
        } catch (IOException e) {
            return "Ошибка загрузки: " + e.getMessage();
        }
    }

    private String truncateHtml(String html, int maxLength) {
        if (html.length() > maxLength) {
            return html.substring(0, maxLength) + "\n...\n(HTML обрезан)";
        }
        return html;
    }

    @Override
    public void run(String... args) {
        fetchAll();
    }

    public static void main(String[] args) {
        SpringApplication.run(HtmlFetcher.class, args);
    }
}
