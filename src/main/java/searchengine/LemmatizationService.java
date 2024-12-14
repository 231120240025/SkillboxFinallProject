package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Index;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.IndexRepository;

import java.io.IOException;
import java.util.*;

@Service
public class LemmatizationService {

    private static final Set<String> EXCLUDED_PARTS_OF_SPEECH = Set.of("МЕЖД", "СОЮЗ", "ПРЕДЛ", "ЧАСТ");

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private IndexRepository indexRepository;

    public void processPage(String url) {
        try {
            // 1. Получить HTML-код страницы
            Document document = Jsoup.connect(url).get();
            String htmlContent = document.html();
            String plainText = document.text();

            // 2. Сохранить страницу в таблицу page
            Page page = new Page();
            page.setPath(url);
            page.setContent(htmlContent);
            page.setCode(200); // HTTP status code
            pageRepository.save(page);

            // 3. Преобразовать текст в набор лемм и их количеств
            HashMap<String, Integer> lemmasWithCount = extractLemmas(plainText);

            // 4. Обработать леммы
            for (Map.Entry<String, Integer> entry : lemmasWithCount.entrySet()) {
                String lemmaText = entry.getKey();
                int count = entry.getValue();

                // Найти или создать лемму в таблице lemma
                Lemma lemma = lemmaRepository.findByLemma(lemmaText)
                        .orElseGet(() -> {
                            Lemma newLemma = new Lemma();
                            newLemma.setLemma(lemmaText);
                            newLemma.setFrequency(0);
                            return newLemma;
                        });

                // Увеличить frequency леммы
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.save(lemma);

                // Сохранить связь леммы и страницы в таблице index
                Index index = new Index();
                index.setLemma(lemma);
                index.setPage(page);
                index.setRank((float) count); // Преобразуем int в Float
                indexRepository.save(index);
            }

        } catch (IOException e) {
            System.err.println("Ошибка при обработке страницы: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private HashMap<String, Integer> extractLemmas(String text) {
        HashMap<String, Integer> lemmaCount = new HashMap<>();

        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();

            // Разделяем текст на слова, убираем знаки препинания
            String[] words = text.toLowerCase().replaceAll("\\p{Punct}", "").split("\\s+");

            for (String word : words) {
                if (word.isBlank()) {
                    continue;
                }

                // Получаем информацию о частях речи и фильтруем
                List<String> morphInfo = luceneMorph.getMorphInfo(word);
                boolean isExcluded = morphInfo.stream()
                        .anyMatch(info -> EXCLUDED_PARTS_OF_SPEECH.stream().anyMatch(info::contains));

                if (isExcluded) {
                    continue;
                }

                // Получаем первую нормальную форму слова
                List<String> normalForms = luceneMorph.getNormalForms(word);
                if (!normalForms.isEmpty()) {
                    String lemma = normalForms.get(0);
                    lemmaCount.put(lemma, lemmaCount.getOrDefault(lemma, 0) + 1);
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка при обработке текста: " + e.getMessage());
            e.printStackTrace();
        }

        return lemmaCount;
    }

    public static String stripHtmlTags(String html) {
        return html.replaceAll("<[^>]*>", "").trim();
    }

    public static void main(String[] args) {
        String inputText = "Повторное появление леопарда в Осетии позволяет предположить, что леопард постоянно обитает в некоторых районах Северного Кавказа.";

        HashMap<String, Integer> lemmasWithCount = new LemmatizationService().extractLemmas(inputText);

        lemmasWithCount.forEach((lemma, count) -> System.out.println(lemma + " — " + count));

        // Пример очистки HTML-кода
        String html = "<html><body><h1>Пример текста</h1><p>Это пример HTML-кода.</p></body></html>";
        String plainText = stripHtmlTags(html);
        System.out.println("Очищенный текст: " + plainText);
    }
}
