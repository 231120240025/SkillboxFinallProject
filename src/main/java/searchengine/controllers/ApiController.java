package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final ExecutorService executorService;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService, ExecutorService executorService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.executorService = executorService;
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
}
