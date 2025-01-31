package searchengine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.config.SitesList;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.IndexingStatus;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.time.LocalDateTime;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.Scanner;
import java.util.stream.Collectors;

@SpringBootApplication
public class HtmlFetcher implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(HtmlFetcher.class);

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    @Autowired
    public HtmlFetcher(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    public void fetchAll() {
        if (sitesList.getSites() == null || sitesList.getSites().isEmpty()) {
            logger.error("❌ Список сайтов пуст. Проверь конфигурацию!");
            return;
        }

        logger.info("🔍 Начинаем загрузку сайтов...");

        // Параллельная обработка сайтов
        List<CompletableFuture<Void>> futures = sitesList.getSites().stream()
                .map(site -> CompletableFuture.runAsync(() -> processSite(site)))
                .collect(Collectors.toList());

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("❌ Ошибка при загрузке сайтов: ", e);
        }

        logger.info("🎉 Все сайты обработаны!");
    }

    private void processSite(searchengine.config.ConfigSite siteConfig) {
        logger.info("🔄 Начинаю обработку сайта: {}", siteConfig.getUrl());

        String html = fetchHtml(siteConfig.getUrl());

        if (html.startsWith("Ошибка загрузки")) {
            logger.error("❌ Ошибка загрузки сайта {}: {}", siteConfig.getUrl(), html);
            return;
        }

        // Логируем результат загрузки HTML
        logger.info("✅ HTML успешно загружен для сайта: {}", siteConfig.getUrl());

        Site savedSite = saveSiteIfNeeded(siteConfig);

        if (savedSite != null) {
            savePage(savedSite, html);
            logger.info("✅ Сайт успешно обработан: {}", siteConfig.getUrl());
        } else {
            logger.warn("⚠️ Не удалось сохранить сайт: {}", siteConfig.getUrl());
        }
    }



    private String fetchHtml(String siteUrl) {
        logger.debug("🕵️‍♂️ Загружаю HTML для сайта: {}", siteUrl);
        try {
            URL url = new URL(siteUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            try (Scanner scanner = new Scanner(connection.getInputStream())) {
                StringBuilder html = new StringBuilder();
                while (scanner.hasNext()) {
                    html.append(scanner.nextLine()).append("\n");
                }

                // Логируем полный HTML-контент (или его часть для более удобного вывода)
                String htmlSnippet = truncateHtml(html.toString(), 500); // Ограничиваем вывод 500 символами для удобства
                logger.debug("✅ HTML загружен для сайта: {}\n{}", siteUrl, htmlSnippet);

                return html.toString();
            }
        } catch (IOException e) {
            logger.error("❌ Ошибка загрузки сайта {}: {}", siteUrl, e.getMessage());
            return "Ошибка загрузки: " + e.getMessage();
        }
    }

    private String truncateHtml(String html, int maxLength) {
        if (html.length() > maxLength) {
            return html.substring(0, maxLength) + "\n...\n(HTML обрезан)";
        }
        return html;
    }




    private Site saveSiteIfNeeded(searchengine.config.ConfigSite siteConfig) {
        // Ищем сайт по URL
        Site existingSite = siteRepository.findByUrl(siteConfig.getUrl());

        if (existingSite != null) {
            logger.debug("🔄 Сайт уже существует в базе данных: {}", siteConfig.getUrl());
            return existingSite;
        } else {
            logger.info("🚀 Создаю новый сайт для URL: {}", siteConfig.getUrl());
            Site newSite = new Site();
            newSite.setUrl(siteConfig.getUrl());
            newSite.setName(siteConfig.getName());
            newSite.setStatus(IndexingStatus.INDEXING); // Устанавливаем статус INDEXING по умолчанию
            newSite.setStatusTime(LocalDateTime.now()); // Устанавливаем текущее время
            return siteRepository.save(newSite); // Сохраняем сайт и возвращаем его
        }
    }

    private void savePage(Site site, String html) {
        logger.debug("💾 Сохраняю страницу для сайта: {}", site.getUrl());
        Page page = new Page();
        page.setSite(site);
        page.setPath("");  // Укажите путь к странице, если нужно
        page.setContent(html);
        page.setCode(200);  // Статус код 200 (ОК)
        pageRepository.save(page);

        // Log saved content without truncation
        logger.debug("✅ Страница успешно сохранена для сайта: {}\nСодержимое страницы:\n{}", site.getUrl(), html);
    }

    @Override
    public void run(String... args) {
        logger.info("🚀 Приложение запущено!");
        fetchAll();
    }


    public static void main(String[] args) {
        SpringApplication.run(HtmlFetcher.class, args);
    }
}
