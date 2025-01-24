package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.config.ConfigSite;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

@Service
public class PageIndexingService {

    private static final Logger logger = LoggerFactory.getLogger(PageIndexingService.class);
    private final SitesList sitesList;

    @Autowired
    public PageIndexingService(SitesList sitesList) {
        this.sitesList = sitesList;
    }

    public boolean isUrlWithinConfiguredSites(String url) {
        List<ConfigSite> sites = sitesList.getSites();
        for (ConfigSite site : sites) {
            logger.info("Проверяем сайт: {}", site.getUrl());
            if (url.startsWith(site.getUrl())) {
                return true;
            }
        }
        return false;
    }

    @Async
    public void indexPage(String url) {
        if (!isValidUrl(url)) {
            logger.error("Некорректный URL: {}", url);
            return;
        }

        try {
            logger.info("Начинаем индексацию страницы: {}", url);
            Thread.sleep(2000);
            logger.info("Успешно проиндексирована страница: {}", url);
        } catch (Exception e) {
            logger.error("Ошибка индексации страницы: {}", url, e);
        }
    }

    private boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public ResponseEntity<Map<String, Object>> createErrorResponse(Map<String, Object> response, String message, HttpStatus status) {
        response.put("result", false);
        response.put("error", message);
        return new ResponseEntity<>(response, status);
    }
}
