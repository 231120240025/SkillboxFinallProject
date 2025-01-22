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

@RestController
@RequestMapping("/api")
public class ApiController {

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
        if (url == null || url.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("result", false);
            errorResponse.put("error", "URL страницы не указан");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        if (!pageIndexingService.isUrlWithinConfiguredSites(url)) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("result", false);
            errorResponse.put("error", "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            pageIndexingService.indexPage(url);
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("result", true);
            return ResponseEntity.ok(successResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("result", false);
            errorResponse.put("error", "Ошибка при индексации страницы: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

}
