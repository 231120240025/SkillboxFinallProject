package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.config.ConfigSite;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.IndexingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PageIndexingService {

    private final SitesList sitesList;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    private static final Logger logger = LoggerFactory.getLogger(PageIndexingService.class);

    @Autowired
    public PageIndexingService(SitesList sitesList, PageRepository pageRepository, SiteRepository siteRepository) {
        this.sitesList = sitesList;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
    }

    public boolean isUrlWithinConfiguredSites(String url) {
        List<ConfigSite> sites = sitesList.getSites();
        return sites.stream().anyMatch(site -> url.startsWith(site.getUrl()));
    }

    public void indexPage(String url) throws Exception {
        logger.info("Starting indexing for URL: {}", url);

        // Определяем сайт, которому принадлежит URL
        Site associatedSite = findOrCreateSite(url);
        if (associatedSite == null) {
            throw new Exception("Не удалось определить сайт для URL: " + url);
        }

        // Индексация страницы
        Page page = pageRepository.findBySiteAndPath(associatedSite.getId(), url.replace(associatedSite.getUrl(), "/"))
                .orElse(new Page());

        page.setSite(associatedSite);
        page.setPath(url.replace(associatedSite.getUrl(), "/")); // Преобразуем полный URL в относительный путь
        page.setCode(200); // Код ответа HTTP (можно заменить на реальный)
        page.setContent(fetchPageContent(url)); // Получение реального содержимого страницы
        page.setContentType("text/html"); // Тип содержимого страницы

        // Сохраняем страницу в базе
        pageRepository.save(page);

        logger.info("Page indexed successfully: {}", page.getPath());

        // Обновляем статус сайта
        updateSiteStatus(associatedSite);
    }

    private Site findOrCreateSite(String url) {
        // Ищем соответствующий сайт по конфигурации
        return sitesList.getSites().stream()
                .filter(siteConfig -> url.startsWith(siteConfig.getUrl()))  // Проверяем, что URL начинается с конфигурированного URL сайта
                .map(siteConfig -> {
                    Site site = siteRepository.findByUrl(siteConfig.getUrl());  // Ищем сайт по URL
                    if (site == null) {  // Если сайт не найден, создаем новый
                        site = new Site();
                        site.setUrl(siteConfig.getUrl());
                        site.setName(siteConfig.getName());
                        site.setStatus(IndexingStatus.INDEXING);  // Устанавливаем статус INDEXING по умолчанию
                        site.setStatusTime(LocalDateTime.now());  // Устанавливаем текущее время
                        siteRepository.save(site);  // Сохраняем сайт в базе
                        logger.info("New site created: {}", site.getUrl());
                    }
                    return site;  // Возвращаем найденный или созданный сайт
                })
                .findFirst()
                .orElse(null);  // Если сайт не найден, возвращаем null
    }

    private void updateSiteStatus(Site site) {
        // Обновляем статус сайта после успешной индексации
        site.setStatus(IndexingStatus.INDEXED);  // Устанавливаем статус INDEXED
        site.setStatusTime(LocalDateTime.now());  // Устанавливаем текущее время
        siteRepository.save(site);  // Сохраняем изменения в базе
        logger.info("Site indexed successfully: {}", site.getUrl());
    }

    private String fetchPageContent(String url) throws Exception {
        // Получение содержимого страницы с использованием Jsoup
        try {
            org.jsoup.nodes.Document document = org.jsoup.Jsoup.connect(url).get();
            return document.outerHtml();
        } catch (Exception e) {
            logger.error("Error fetching content for URL: {}", url, e);
            throw new Exception("Ошибка при загрузке содержимого страницы: " + e.getMessage());
        }
    }
}