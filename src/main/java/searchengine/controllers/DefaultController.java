package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import searchengine.services.IndexingService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class DefaultController {

    private final IndexingService indexingService;
    private final ExecutorService executorService;

    @Autowired
    public DefaultController(IndexingService indexingService) {
        this.indexingService = indexingService;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @RequestMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/api/startIndexing")
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

    @GetMapping("/api/stopIndexing")
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
