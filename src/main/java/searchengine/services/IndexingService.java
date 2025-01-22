package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import searchengine.config.SitesList;
import searchengine.model.IndexingStatus;
import searchengine.model.Page;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
public class IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;


    private volatile boolean indexingInProgress = false;
    private ExecutorService executorService;
    private ForkJoinPool forkJoinPool;

    public IndexingService(SitesList sitesList, SiteRepository siteRepository,  PageRepository pageRepository ) {
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

        executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {
                performIndexing();
            } catch (Exception e) {
                logger.error("Ошибка во время индексации: ", e);
            } finally {
                indexingInProgress = false;
                logger.info("Индексация завершена.");
            }
        });
        executorService.shutdown();
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

    public void indexPage(String url) {
        logger.info("Индексация отдельной страницы: {}", url);

        // Проверяем, находится ли URL в рамках указанных сайтов
        if (!isUrlValid(url)) {
            logger.error("URL {} находится за пределами разрешенных сайтов.", url);
            throw new IllegalArgumentException("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        try {
            searchengine.model.Site site = getSiteByUrl(url);
            if (site == null) {
                logger.error("Сайт для URL {} не найден.", url);
                return;
            }

            String content = fetchPageContent(url);
            savePageIfUnique(url, content, site);

        } catch (Exception e) {
            logger.error("Ошибка индексации страницы {}: {}", url, e.getMessage());
        }
    }

    public boolean isUrlValid(String url) {
        return sitesList.getSites().stream()
                .anyMatch(site -> url.startsWith(site.getUrl()));
    }

    public synchronized boolean isIndexingComplete() {
        return !indexingInProgress;
    }

    private searchengine.model.Site getSiteByUrl(String url) {
        return sitesList.getSites().stream()
                .filter(site -> url.startsWith(site.getUrl()))
                .map(site -> siteRepository.findByUrl(site.getUrl()))
                .findFirst()
                .orElse(null);
    }

    private String fetchPageContent(String url) {
        // Здесь может быть код для загрузки содержимого страницы через HTTP-запрос
        logger.info("Загрузка содержимого страницы: {}", url);
        return "Пример содержимого страницы."; // Заглушка
    }

    private void performIndexing() {
        List<searchengine.config.Site> sites = sitesList.getSites();
        if (sites == null || sites.isEmpty()) {
            logger.warn("Список сайтов для индексации пуст.");
            return;
        }

        executorService = Executors.newFixedThreadPool(sites.size());
        try {
            for (searchengine.config.Site site : sites) {
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
                        if (indexingInProgress) {
                            updateSiteStatusToIndexed(newSite);
                        } else {
                            logger.warn("Индексация была прервана. Статус сайта {} не обновлен на INDEXED.", site.getName());
                        }
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
            forkJoinPool.invoke(new PageCrawler(site, startUrl, new HashSet<>(), pageRepository, this));
        } finally {
            forkJoinPool.shutdown();
        }
    }

    private void deleteSiteData(String siteUrl) {
        searchengine.model.Site site = siteRepository.findByUrl(siteUrl);
        if (site != null) {
            int pagesDeleted = pageRepository.deleteAllBySiteId(site.getId());
            siteRepository.delete(site);
            logger.info("Удалено {} записей из таблицы page для сайта {}.", pagesDeleted, siteUrl);
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
            logger.error("Ошибка при индексации сайта {}: {}", site.getUrl(), e.getMessage());
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

    private void savePageIfUnique(String url, String content, searchengine.model.Site site) {
        Optional<Page> existingPage = pageRepository.findByPathAndSiteId(url, site.getId());
        if (existingPage.isEmpty()) {
            Page page = new Page();
            page.setPath(url);
            page.setContent(content);
            page.setSite(site);
            Page savedPage = pageRepository.save(page);
            logger.info("Сохранена уникальная страница: {}", url);


        } else {
            logger.info("Страница {} уже существует. Пропускаем сохранение.", url);
        }
    }

    public Set<String> getIndexedSites() {
        // Получаем список сайтов из базы данных, которые были проиндексированы
        List<searchengine.model.Site> indexedSites = siteRepository.findAllByStatus(IndexingStatus.INDEXED);

        // Возвращаем только URL сайтов в виде Set
        Set<String> indexedSiteUrls = new HashSet<>();
        for (searchengine.model.Site site : indexedSites) {
            indexedSiteUrls.add(site.getUrl());
        }

        return indexedSiteUrls;
    }
}
