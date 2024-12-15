package searchengine.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class Lemmatizer {
    private static final String MYSTEM_PATH = "C:/tools/mystem/mystem.exe"; // Windows

    public List<String> lemmatize(String text) {
        List<String> lemmas = new ArrayList<>();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(MYSTEM_PATH, "-n", "--format=json");
            Process process = processBuilder.start();

            try (OutputStream os = process.getOutputStream()) {
                os.write(text.getBytes());
                os.flush();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String lemma = extractLemmaFromJson(line);
                    if (lemma != null) {
                        lemmas.add(lemma);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lemmas;
    }

    private String extractLemmaFromJson(String jsonLine) {
        // Простая обработка JSON (лучше использовать библиотеку вроде Jackson/Gson)
        if (jsonLine.contains("\"lex\":")) {
            int start = jsonLine.indexOf("\"lex\":") + 7;
            int end = jsonLine.indexOf("\"", start);
            return jsonLine.substring(start, end);
        }
        return null;
    }
}

