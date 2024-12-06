package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.IndexingStatus;
import searchengine.model.Page;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@Service
public class IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    private volatile boolean indexingInProgress = false;
    private ExecutorService executorService;
    private ForkJoinPool forkJoinPool;

    public IndexingService(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    public synchronized boolean isIndexingInProgress() {
        return indexingInProgress;
    }

    public synchronized void startFullIndexing() {
        if (indexingInProgress) {
            logger.warn("Попытка запустить индексацию, которая уже выполняется.");
            throw new IllegalStateException("Индексация уже запущена.");
        }
        indexingInProgress = true;
        logger.info("Индексация начата.");

        try {
            performIndexing();
        } catch (Exception e) {
            logger.error("Ошибка во время индексации: ", e);
        } finally {
            indexingInProgress = false;
            logger.info("Индексация завершена.");
        }
    }

    public synchronized void stopIndexing() {
        if (!indexingInProgress) {
            logger.warn("Попытка остановить индексацию, которая не выполняется.");
            return;
        }
        logger.info("Остановка индексации по запросу пользователя.");
        indexingInProgress = false;
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (forkJoinPool != null) {
            forkJoinPool.shutdownNow();
        }
        updateSitesStatusToFailed("Индексация остановлена пользователем");
    }

    @Transactional
    private void deleteSiteData(String siteUrl) {
        searchengine.model.Site site = siteRepository.findByUrl(siteUrl);
        if (site != null) {
            logger.info("Удаление данных для сайта: {}", siteUrl);
            int pagesDeleted = pageRepository.deleteAllBySiteId(site.getId());
            siteRepository.delete(site);
            logger.info("Удалено {} записей из таблицы page для сайта {}.", pagesDeleted, siteUrl);
        } else {
            logger.info("Данные для сайта {} отсутствуют.", siteUrl);
        }
    }

    private void performIndexing() {
        List<Site> sites = sitesList.getSites();
        if (sites == null || sites.isEmpty()) {
            logger.warn("Список сайтов для индексации пуст.");
            return;
        }

        executorService = Executors.newFixedThreadPool(sites.size());
        try {
            for (Site site : sites) {
                executorService.submit(() -> {
                    logger.info("Индексация сайта: {} ({})", site.getName(), site.getUrl());
                    try {
                        deleteSiteData(site.getUrl());
                        searchengine.model.Site newSite = new searchengine.model.Site();
                        newSite.setName(site.getName());
                        newSite.setUrl(site.getUrl());
                        newSite.setStatus(IndexingStatus.INDEXING);
                        newSite.setStatusTime(LocalDateTime.now());
                        siteRepository.save(newSite);
                        crawlAndIndexPages(newSite, site.getUrl());
                        updateSiteStatusToIndexed(newSite);
                        logger.info("Сайт {} успешно проиндексирован.", site.getName());
                    } catch (Exception e) {
                        handleIndexingError(site.getUrl(), e);
                    }
                });
            }
        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(1, TimeUnit.HOURS)) {
                    executorService.shutdownNow();
                    logger.error("Превышено время ожидания завершения индексации.");
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                logger.error("Индексация была прервана: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    private void crawlAndIndexPages(searchengine.model.Site site, String startUrl) {
        forkJoinPool = new ForkJoinPool();
        try {
            forkJoinPool.invoke(new PageCrawler(site, startUrl, new HashSet<>()));
        } finally {
            forkJoinPool.shutdown();
        }
    }

    private class PageCrawler extends RecursiveAction {
        private final searchengine.model.Site site;
        private final String url;
        private final Set<String> visitedUrls;

        public PageCrawler(searchengine.model.Site site, String url, Set<String> visitedUrls) {
            this.site = site;
            this.url = url;
            this.visitedUrls = visitedUrls;
        }

        @Override
        protected void compute() {
            synchronized (visitedUrls) {
                if (visitedUrls.contains(url)) {
                    return;
                }
                visitedUrls.add(url);
            }

            try {
                // Добавляем задержку для избежания блокировки
                long delay = 500 + new Random().nextInt(4500); // Задержка от 0.5 до 5 секунд
                logger.info("Задержка перед запросом: {} ms для URL: {}", delay, url);
                Thread.sleep(delay);

                // Обращение с фейковым User-Agent и реферером
                String contentType = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                        .referrer("http://www.google.com")
                        .ignoreContentType(true)
                        .execute()
                        .contentType();
                int statusCode = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                        .referrer("http://www.google.com")
                        .ignoreContentType(true)
                        .execute()
                        .statusCode();

                if (contentType != null && contentType.startsWith("image/")) {
                    Page page = new Page();
                    page.setSite(site);
                    page.setPath(new URL(url).getPath());
                    page.setCode(statusCode);
                    page.setContent("Image content: " + contentType);
                    pageRepository.save(page);
                    logger.info("Изображение добавлено: {}", url);
                    return;
                }

                Document document = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                        .referrer("http://www.google.com")
                        .get();
                String content = document.html();

                Page page = new Page();
                page.setSite(site);
                page.setPath(new URL(url).getPath());
                page.setCode(statusCode);
                page.setContent(content);
                pageRepository.save(page);

                logger.info("Страница добавлена: {}", url);

                Elements links = document.select("a[href]");
                List<PageCrawler> subtasks = new ArrayList<>();
                for (Element link : links) {
                    String childUrl = link.absUrl("href");
                    if (childUrl.startsWith(site.getUrl())) {
                        subtasks.add(new PageCrawler(site, childUrl, visitedUrls));
                    }
                }
                invokeAll(subtasks);
            } catch (IOException | InterruptedException e) {
                logger.error("Ошибка при обработке URL {}: {}", url, e.getMessage());
            }
        }
    }

    private void updateSiteStatusToIndexed(searchengine.model.Site site) {
        site.setStatus(IndexingStatus.INDEXED);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
        logger.info("Сайт {} изменил статус на INDEXED.", site.getUrl());
    }

    private void handleIndexingError(String siteUrl, Exception e) {
        searchengine.model.Site site = siteRepository.findByUrl(siteUrl);
        if (site != null) {
            site.setStatus(IndexingStatus.FAILED);
            site.setLastError(e.getMessage());
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
            logger.error("Индексация сайта {} завершилась ошибкой: {}", site.getUrl(), e.getMessage());
        }
    }

    private void updateSitesStatusToFailed(String errorMessage) {
        List<searchengine.model.Site> sites = siteRepository.findAllByStatus(IndexingStatus.INDEXING);
        for (searchengine.model.Site site : sites) {
            site.setStatus(IndexingStatus.FAILED);
            site.setLastError(errorMessage);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
            logger.info("Сайт {} изменил статус на FAILED: {}", site.getUrl(), errorMessage);
        }
    }
}
