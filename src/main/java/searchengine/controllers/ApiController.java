package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam ;
import searchengine.services.PageIndexingService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api")
public class ApiController {
    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);
    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final ExecutorService executorService;
    private final PageIndexingService pageIndexingService;  // Исправленное имя переменной

    public ApiController(StatisticsService statisticsService, PageIndexingService pageIndexingService, IndexingService indexingService, ExecutorService executorService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.executorService = executorService;
        this.pageIndexingService = pageIndexingService;  // Конструктор правильно инициализирует переменную
    }



    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        if (indexingService.isIndexingInProgress()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("result", false);
            errorResponse.put("error", "Индексация уже запущена");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Запуск асинхронной индексации
        executorService.submit(indexingService::startFullIndexing);

        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("result", true);
        return ResponseEntity.ok(successResponse);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        if (!indexingService.isIndexingInProgress()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("result", false);
            errorResponse.put("error", "Индексация не запущена");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        indexingService.stopIndexing();

        Map<String, Object> successResponse = new HashMap<>();
        successResponse.put("result", true);
        return ResponseEntity.ok(successResponse);
    }

    @PostMapping(value = "/indexPage", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam String url) {
        Map<String, Object> response = new HashMap<>();

        // Проверка на пустой или null URL
        if (url == null || url.isEmpty()) {
            response.put("result", false);
            response.put("error", "URL страницы не указан");
            logger.warn("URL is empty or null.");
            return ResponseEntity.badRequest().body(response);
        }

        // Проверка, принадлежит ли URL указанным в конфигурации сайтам
        if (!pageIndexingService.isUrlWithinConfiguredSites(url)) {
            response.put("result", false);
            response.put("error", "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            logger.warn("URL is not within the configured sites: {}", url);
            return ResponseEntity.badRequest().body(response);
        }

        // Попытка индексации страницы
        try {
            pageIndexingService.indexPage(url);
            response.put("result", true);
            logger.info("Successfully indexed page: {}", url);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Ошибка при индексации страницы: " + e.getMessage());
            logger.error("Error indexing page: {}", url, e);
            return ResponseEntity.status(500).body(response);
        }
    }

}
