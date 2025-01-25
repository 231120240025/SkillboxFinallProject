package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.config.ConfigSite;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import java.io.IOException;

@Service
public class PageIndexingService {

    private final SitesList sitesList;

    @Autowired
    public PageIndexingService(SitesList sitesList) {
        this.sitesList = sitesList;
    }

    public boolean isUrlWithinConfiguredSites(String url) {
        List<ConfigSite> sites = sitesList.getSites();
        for (ConfigSite site : sites) {
            System.out.println("Проверяем сайт: " + site.getUrl());
            if (url.startsWith(site.getUrl())) {
                return true;
            }
        }
        return false;
    }

    public void indexPage(String url) throws Exception {
        try {
            // Загружаем страницу с помощью Jsoup
            Document doc = Jsoup.connect(url).get();

            // Пример индексации: извлекаем все ссылки с страницы
            doc.select("a[href]").forEach(link -> {
                String href = link.attr("href");
                System.out.println("Найденная ссылка: " + href);
                // Реализуйте логику сохранения или индексации этой ссылки
            });

            // Пример индексации: извлекаем мета-данные страницы
            String title = doc.title();
            System.out.println("Заголовок страницы: " + title);

            // Реализуйте сохранение контента или других данных страницы в базе данных или индексе

        } catch (IOException e) {
            throw new Exception("Ошибка загрузки страницы: " + e.getMessage());
        } catch (Exception e) {
            throw new Exception("Ошибка индексации страницы: " + e.getMessage());
        }
    }

    // Добавляем метод для создания ошибки
    public ResponseEntity<Map<String, Object>> createErrorResponse(
            Map<String, Object> response,
            String errorMessage,
            HttpStatus status) {

        response.put("result", false);
        response.put("error", errorMessage);
        return new ResponseEntity<>(response, status);
    }
}
