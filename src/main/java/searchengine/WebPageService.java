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
public class WebPageService implements CommandLineRunner {

    @Autowired
    private SitesList sitesList;

    // Метод для получения HTML-кода всех сайтов из конфигурации
    public void fetchHtmlFromSites() {
        List<ConfigSite> sites = sitesList.getSites();

        if (sites != null) {
            for (ConfigSite site : sites) {
                try {
                    // Получаем HTML-код страницы с указанного URL
                    Document document = Jsoup.connect(site.getUrl()).get();
                    System.out.println("HTML код страницы для: " + site.getUrl());
                    System.out.println(document.html()); // выводим HTML-код страницы

                } catch (IOException e) {
                    System.out.println("Ошибка при загрузке страницы: " + site.getUrl());
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void run(String... args) throws Exception {
        // Вызываем метод сразу после запуска приложения
        fetchHtmlFromSites();
    }

    // Метод main для запуска приложения
    public static void main(String[] args) {
        SpringApplication.run(WebPageService.class, args);
    }
}
