package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
import java.io.IOException;
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
            Document document = Jsoup.connect(url).get();  // Скачиваем HTML контент страницы
            String title = document.title();  // Извлекаем заголовок страницы
            String text = document.body().text();  // Извлекаем текст из тела страницы
            Elements links = document.select("a[href]");  // Извлекаем все ссылки на странице

            // Логируем информацию о странице
            logger.info("Заголовок страницы: {}", title);
            logger.info("Текст страницы: {}", text);

            // Обрабатываем найденные ссылки (например, добавляем их для индексации)
            for (Element link : links) {
                String linkUrl = link.absUrl("href");  // Получаем абсолютный URL
                logger.info("Найденная ссылка: {}", linkUrl);
                // Можно добавить дополнительную логику для обработки найденных ссылок
            }

            logger.info("Успешно проиндексирована страница: {}", url);
        } catch (IOException e) {
            logger.error("Ошибка при скачивании или парсинге страницы: {}", url, e);
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
