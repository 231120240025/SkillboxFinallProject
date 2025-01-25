package searchengine.services;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.config.ConfigSite;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;

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
            // Логика индексации страницы (пока просто выводим URL)
            System.out.println("Индексация страницы: " + url);
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
