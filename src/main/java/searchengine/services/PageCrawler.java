package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.RecursiveAction;

public class PageCrawler extends RecursiveAction {

    private static final Logger logger = LoggerFactory.getLogger(PageCrawler.class);

    private final Site site;
    private final String url;
    private final Set<String> visitedUrls;
    private final PageRepository pageRepository;

    public PageCrawler(Site site, String url, Set<String> visitedUrls, PageRepository pageRepository) {
        this.site = site;
        this.url = url;
        this.visitedUrls = visitedUrls;
        this.pageRepository = pageRepository;
    }

    @Override
    protected void compute() {
        synchronized (visitedUrls) {
            if (visitedUrls.contains(url)) {
                logger.debug("URL уже посещен: {}", url);
                return;
            }
            visitedUrls.add(url);
        }

        try {
            Document document = Jsoup.connect(url).get();
            String content = document.html();

            Page page = new Page();
            page.setSite(site);
            page.setPath(new URL(url).getPath());
            page.setCode(200); // Статус успешного запроса
            page.setContent(content);
            pageRepository.save(page);

            logger.info("Добавлена страница: {}", url);

            Elements links = document.select("a[href]");
            List<PageCrawler> subtasks = new ArrayList<>();
            for (Element link : links) {
                String childUrl = link.absUrl("href");
                if (childUrl.startsWith(site.getUrl())) {
                    logger.debug("Найден дочерний URL: {}", childUrl);
                    subtasks.add(new PageCrawler(site, childUrl, visitedUrls, pageRepository));
                }
            }
            invokeAll(subtasks);
        } catch (IOException e) {
            logger.error("Ошибка при обработке URL {}: {}", url, e.getMessage(), e);
        }
    }
}
