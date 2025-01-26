package searchengine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import searchengine.config.SitesList;
import searchengine.config.ConfigSite;

import java.io.IOException;
import java.util.List;

@SpringBootApplication
public class HtmlService implements CommandLineRunner {

    private final SitesList sitesList;

    @Autowired
    public HtmlService(SitesList sitesList) {
        this.sitesList = sitesList;
    }

    public void fetchHtmlFromSites() {
        List<ConfigSite> sites = sitesList.getSites();

        if (sites == null || sites.isEmpty()) {
            System.err.println("Список сайтов пустой или не инициализирован.");
            return;
        }

        for (ConfigSite site : sites) {
            try {
                // Получаем HTML-код страницы
                Document document = Jsoup.connect(site.getUrl()).get();
                String htmlContent = document.html();
                System.out.println("HTML для " + site.getUrl() + ": " + htmlContent);
            } catch (IOException e) {
                System.err.println("Ошибка при получении HTML с сайта " + site.getUrl());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run(String... args) throws Exception {
        // Когда приложение запущено, вызываем метод для получения HTML
        fetchHtmlFromSites();
    }

    public static void main(String[] args) {
        SpringApplication.run(HtmlService.class, args);  // Запуск приложения Spring Boot
    }
}
