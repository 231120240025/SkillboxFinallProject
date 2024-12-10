package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.RecursiveAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PageCrawler extends RecursiveAction {
    private static final Logger logger = LoggerFactory.getLogger(PageCrawler.class);
    private final Site site;
    private final String url;
    private final Set<String> visitedUrls;
    private final PageRepository pageRepository;
    private final IndexingService indexingService;

    public PageCrawler(Site site, String url, Set<String> visitedUrls, PageRepository pageRepository, IndexingService indexingService) {
        this.site = site;
        this.url = url;
        this.visitedUrls = visitedUrls;
        this.pageRepository = pageRepository;
        this.indexingService = indexingService;
    }

    @Override
    protected void compute() {
        if (!checkAndLogStopCondition("Начало обработки")) return;

        synchronized (visitedUrls) {
            if (visitedUrls.contains(url)) {
                logger.debug("URL уже обработан: {}", url);
                return;
            }
            visitedUrls.add(url);
        }

        try {
            long delay = 500 + new Random().nextInt(4500);
            logger.debug("Задержка перед запросом: {} ms для URL: {}", delay, url);
            Thread.sleep(delay);

            if (!checkAndLogStopCondition("Перед запросом")) return;

            logger.info("Обработка URL: {}", url);
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .ignoreContentType(true)
                    .execute();

            handleResponse(response);

        } catch (IOException e) {
            handleError(e);
        } catch (InterruptedException e) {
            logger.warn("Индексация прервана для URL {}: поток остановлен.", url);
            Thread.currentThread().interrupt();
        }
    }

    private void handleResponse(Connection.Response response) throws IOException {
        String contentType = response.contentType();
        int statusCode = response.statusCode();
        Page page = new Page();
        page.setSite(site);
        page.setPath(new URL(url).getPath());
        page.setCode(statusCode);

        if (contentType != null && contentType.startsWith("image/")) {
            page.setContent("Image content: " + contentType);
            logger.info("Изображение добавлено: {}", url);
        } else if (contentType != null && contentType.contains("text/html")) {
            Document document = response.parse();
            page.setContent(document.html());
            logger.info("HTML-страница добавлена: {}", url);
            processLinks(document);
        } else {
            page.setContent("Unhandled content type: " + contentType);
            logger.info("Контент с неизвестным типом добавлен: {}", url);
        }

        pageRepository.save(page);
    }

    private void processLinks(Document document) {
        Elements links = document.select("a[href]");
        List<PageCrawler> subtasks = new ArrayList<>();
        for (Element link : links) {
            if (!checkAndLogStopCondition("При обработке ссылок")) return;

            String childUrl = link.absUrl("href");
            synchronized (visitedUrls) {
                if (!visitedUrls.contains(childUrl)) {
                    subtasks.add(new PageCrawler(site, childUrl, visitedUrls, pageRepository, indexingService));
                    logger.debug("Добавлена ссылка в обработку: {}", childUrl);
                }
            }
        }
        invokeAll(subtasks);
    }

    private void handleError(IOException e) {
        logger.warn("Ошибка обработки URL {}: {}", url, e.getMessage());
        Page page = new Page();
        page.setSite(site);
        page.setPath(url);
        page.setCode(0);
        page.setContent("Ошибка обработки: " + e.getMessage());
        pageRepository.save(page);
    }

    private boolean checkAndLogStopCondition(String stage) {
        if (!indexingService.isIndexingInProgress()) {
            logger.info("Индексация прервана на этапе {} для URL: {}", stage, url);
            return false;
        }
        return true;
    }
}
