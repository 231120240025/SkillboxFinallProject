package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

@Service
public class PageIndexingService {

    private static final Logger logger = LoggerFactory.getLogger(PageIndexingService.class);
    private final SitesList sitesList;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    private final Set<String> visitedUrls = new HashSet<>();
    private static final int MAX_DEPTH = 5;  // Максимальная глубина обхода

    @Autowired
    public PageIndexingService(SitesList sitesList, PageRepository pageRepository, SiteRepository siteRepository) {
        this.sitesList = sitesList;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
    }

    public void startIndexing(String url) {
        try {
            logger.info("Инициализация индексации для URL: {}", url);
            indexPage(url);
        } catch (Exception e) {
            logger.error("Индексация не удалась для URL: {}", url, e);
            // В случае ошибки, вызываем метод для обновления статуса на FAILED
            Site site = findOrCreateSite(url);  // Пытаемся найти или создать сайт
            if (site != null) {
                updateSiteStatusFailed(site, "Ошибка индексации: " + e.getMessage());
            }
        }
    }


    public boolean isUrlWithinConfiguredSites(String url) {
        return sitesList.getSites().stream().anyMatch(site -> url.startsWith(site.getUrl()));
    }

    public void indexPage(String url) throws Exception {
        logger.info("Начало индексации для URL: {}", url);

        // Определяем сайт для URL
        Site site = findOrCreateSite(url);
        if (site == null) {
            throw new Exception("Не удалось определить сайт для URL: " + url);
        }

        visitedUrls.clear();
        try {
            crawlPage(site, url, 1);  // Начинаем обход страниц
            updateSiteStatus(site);  // Обновляем статус на INDEXED после завершения обхода
        } catch (Exception e) {
            // Если возникла ошибка, изменяем статус на FAILED и записываем ошибку
            updateSiteStatusFailed(site, "Ошибка индексации: " + e.getMessage());
        }
    }



    private void crawlPage(Site site, String url, int depth) throws Exception {
        if (visitedUrls.contains(url) || depth > MAX_DEPTH) {
            return;
        }
        visitedUrls.add(url);
        logger.info("Обработка URL (глубина {}): {}", depth, url);

        try {
            // Вставляем случайную задержку перед каждым запросом
            sleepRandomly();

            // Обработка различных типов контента
            org.jsoup.nodes.Document document = org.jsoup.Jsoup.connect(url)
                    .timeout(5000)
                    .userAgent("Mozilla/5.0")
                    .execute()
                    .parse();

            String contentType = document.connection().response().contentType();
            if (contentType != null && contentType.startsWith("text/")) {
                savePage(site, url, document);
                // Обновление времени в процессе индексации
                updateSiteStatusTime(site);

                document.select("a[href]").forEach(link -> {
                    String href = link.absUrl("href");
                    if (href.startsWith(site.getUrl()) && !visitedUrls.contains(href)) {
                        try {
                            crawlPage(site, href, depth + 1); // Рекурсивный обход
                        } catch (Exception e) {
                            logger.error("Ошибка при обработке URL: {}", href, e);
                            updateSiteStatusFailed(site, "Ошибка индексации страницы: " + href);
                        }
                    }
                });
            } else if (contentType != null && (contentType.startsWith("image/") || contentType.equals("application/pdf"))) {
                saveFile(site, url, contentType);
            } else {
                logger.warn("Необрабатываемый контент для URL: {} Тип контента: {}", url, contentType);
            }
        } catch (IOException e) {
            logger.error("Ошибка при получении содержимого для URL: {}", url, e);
            updateSiteStatusFailed(site, "Ошибка получения содержимого: " + e.getMessage());
            throw new Exception("Ошибка получения содержимого страницы: " + e.getMessage(), e);
        }
    }


    private void updateSiteStatusTime(Site site) {
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);  // Обновляем время
        logger.info("Время обновлено для сайта: {}", site.getUrl());
    }


    private Site findOrCreateSite(String url) {
        return sitesList.getSites().stream()
                .filter(siteConfig -> url.startsWith(siteConfig.getUrl()))
                .map(siteConfig -> {
                    // Получаем Site из репозитория
                    Site existingSite = siteRepository.findByUrl(siteConfig.getUrl());
                    // Если сайт существует, возвращаем его, если нет, создаем новый
                    return existingSite != null ? existingSite : createSite(siteConfig);
                })
                .findFirst()
                .orElse(null);  // Если не нашли сайт, возвращаем null
    }




    private Site createSite(ConfigSite siteConfig) {
        Site site = new Site();
        site.setUrl(siteConfig.getUrl());
        site.setName(siteConfig.getName());
        site.setStatus(IndexingStatus.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
        return site;
    }

    private void savePage(Site site, String url, org.jsoup.nodes.Document document) {
        String relativePath = normalizeUrl(url).replace(site.getUrl(), "/");

        // Проверка на дублирование страницы
        if (pageRepository.findBySiteAndPath(site.getId(), relativePath).isPresent()) {
            logger.info("Страница уже существует в базе: {}", relativePath);
            return;
        }

        Page page = new Page();
        page.setSite(site);
        page.setPath(relativePath);
        page.setCode(200);
        page.setContentType("text/html");

        // Преобразуем HTML в строку и сохраняем
        page.setContent(document.outerHtml()); // Сохраняем как строку

        pageRepository.save(page);
        logger.info("Страница сохранена: {}", relativePath);

        // Обновляем время после сохранения страницы
        updateSiteStatusTime(site);
    }



    private String normalizeUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String path = uri.getPath();
            return uri.getScheme() + "://" + uri.getHost() + (path != null ? path : "");
        } catch (Exception e) {
            logger.error("Ошибка нормализации URL: {}", url, e);
            return url;
        }
    }

    private void updateSiteStatus(Site site) {
        site.setStatus(IndexingStatus.INDEXED);  // Устанавливаем статус в INDEXED
        site.setStatusTime(LocalDateTime.now());  // Обновляем время статуса
        siteRepository.save(site);  // Сохраняем изменения в репозитории
        logger.info("Сайт успешно проиндексирован: {}", site.getUrl());
    }


    private void saveFile(Site site, String url, String contentType) {
        String relativePath = url.replace(site.getUrl(), "/");

        // Проверка на дублирование файла
        Page existingPage = pageRepository.findBySiteAndPath(site.getId(), relativePath).orElse(null);
        if (existingPage != null) {
            logger.info("Файл уже существует: {}", relativePath);
            return;
        }

        try {
            byte[] fileContent = downloadFile(url); // Загружаем файл
            Page page = new Page();
            page.setSite(site);
            page.setPath(relativePath);
            page.setCode(200);  // Указываем статус 200, так как файл был успешно загружен
            page.setContentType(contentType);

            // Сохраняем файл в бинарном виде
            page.setContent(new String(fileContent, StandardCharsets.UTF_8)); // Преобразуем байты в строку и сохраняем

            pageRepository.save(page);
            logger.info("Файл сохранён: {}", relativePath);

            // Обновляем время после сохранения файла
            updateSiteStatusTime(site);
        } catch (IOException e) {
            logger.error("Ошибка при загрузке файла: {}", url, e);
            updateSiteStatusFailed(site, "Ошибка при загрузке файла: " + e.getMessage());
        }
    }

    private byte[] downloadFile(String url) throws IOException {
        java.net.URL fileUrl = new java.net.URL(url);
        java.net.URLConnection connection = fileUrl.openConnection();

        // Устанавливаем таймауты для соединения и чтения
        connection.setConnectTimeout(5000);  // Таймаут на подключение - 5 секунд
        connection.setReadTimeout(5000);     // Таймаут на чтение - 5 секунд

        try (java.io.InputStream inputStream = connection.getInputStream()) {
            return inputStream.readAllBytes();  // Считываем все байты из потока
        } catch (IOException e) {
            logger.error("Ошибка при скачивании файла: {}", url, e);
            throw new IOException("Ошибка при скачивании файла: " + e.getMessage(), e);  // Пробрасываем исключение
        }
    }



    public ResponseEntity<Map<String, Object>> createErrorResponse(Map<String, Object> response, String errorMessage, HttpStatus status) {
        response.put("result", false);
        response.put("error", errorMessage);
        return ResponseEntity.status(status).body(response);
    }

    private void sleepRandomly() {
        try {
            // Генерация случайной задержки от 500 миллисекунд (0,5 секунды) до 5000 миллисекунд (5 секунд)
            int delay = 500 + (int) (Math.random() * 4500); // Задержка от 500 до 5000 миллисекунд
            Thread.sleep(delay);
            logger.info("Задержка между запросами: {} миллисекунд", delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Восстановление состояния прерывания потока
            logger.error("Ошибка при ожидании перед запросом", e);
        }
    }

    private void updateSiteStatusFailed(Site site, String errorMessage) {
        site.setStatus(IndexingStatus.FAILED);  // Устанавливаем статус в FAILED
        site.setLastError(errorMessage);        // Сохраняем описание ошибки
        site.setStatusTime(LocalDateTime.now()); // Обновляем время
        siteRepository.save(site);  // Сохраняем изменения в репозитории
        logger.error("Ошибка индексации сайта {}: {}", site.getUrl(), errorMessage);
    }

}
