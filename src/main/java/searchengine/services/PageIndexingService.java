package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.config.ConfigSite;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.IndexingStatus;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PageIndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    @Autowired
    public PageIndexingService(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    // Проверка, входит ли URL в список настроенных сайтов
    public boolean isUrlWithinConfiguredSites(String url) {
        List<ConfigSite> sites = sitesList.getSites();
        for (ConfigSite site : sites) {
            if (url.startsWith(site.getUrl())) {
                return true;
            }
        }
        return false;
    }

    // Полный обход сайта
    public void indexSite(String baseUrl) throws Exception {
        if (!isUrlWithinConfiguredSites(baseUrl)) {
            throw new Exception("URL не принадлежит к списку настроенных сайтов: " + baseUrl);
        }

        Site site = siteRepository.findByUrl(baseUrl);
        if (site == null) {
            site = new Site();
            site.setUrl(baseUrl);
            site.setName(baseUrl); // Можно заменить на реальное имя сайта
            site.setStatus(IndexingStatus.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }

        Set<String> visitedUrls = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(baseUrl);

        while (!queue.isEmpty()) {
            String currentUrl = queue.poll();
            if (visitedUrls.contains(currentUrl)) {
                continue;
            }
            visitedUrls.add(currentUrl);

            try {
                Document document = Jsoup.connect(currentUrl).get();
                savePage(document, currentUrl, site);

                // Извлечение всех ссылок со страницы
                Elements links = document.select("a[href]");
                for (Element link : links) {
                    String absUrl = link.attr("abs:href");
                    if (absUrl.startsWith(baseUrl) && !visitedUrls.contains(absUrl)) {
                        queue.add(absUrl);
                    }
                }
            } catch (IOException e) {
                System.err.println("Ошибка загрузки страницы: " + currentUrl + " - " + e.getMessage());
            }
        }

        site.setStatus(IndexingStatus.INDEXED);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);

        System.out.println("Индексация сайта завершена: " + baseUrl);
    }

    // Сохранение страницы в базу данных
    private void savePage(Document document, String url, Site site) {
        String content = document.html();
        String path = getPathFromUrl(url);
        int statusCode = 200; // Здесь можно получить реальный HTTP-статус

        Page page = new Page();
        page.setSite(site);
        page.setPath(path);
        page.setCode(statusCode);
        page.setContent(content);
        page.setContentType("text/html");
        page.setStatus(IndexingStatus.INDEXED);

        pageRepository.save(page);
        System.out.println("Сохранена страница: " + url);
    }

    // Метод для создания ошибки
    public ResponseEntity<Map<String, Object>> createErrorResponse(
            Map<String, Object> response,
            String errorMessage,
            HttpStatus status) {

        response.put("result", false);
        response.put("error", errorMessage);
        return new ResponseEntity<>(response, status);
    }

    // Метод для получения базового URL
    private String getBaseUrl(String url) {
        try {
            return new java.net.URL(url).getProtocol() + "://" + new java.net.URL(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    // Метод для извлечения пути из URL
    private String getPathFromUrl(String url) {
        try {
            return new java.net.URL(url).getPath();
        } catch (Exception e) {
            return "/";
        }
    }
}
