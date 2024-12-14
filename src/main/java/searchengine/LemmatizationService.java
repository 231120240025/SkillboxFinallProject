package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Index;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.IndexRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.util.*;

@Service
public class LemmatizationService {

    private static final Logger logger = LoggerFactory.getLogger(LemmatizationService.class);
    private static final Set<String> EXCLUDED_PARTS_OF_SPEECH = Set.of("МЕЖД", "СОЮЗ", "ПРЕДЛ", "ЧАСТ");

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private SiteRepository siteRepository;

    public void processPage(String url, String siteName, String siteUrl) {
        try {
            // 1. Привязать страницу к сайту
            Site site = siteRepository.findByUrl(siteUrl);
            if (site == null) {
                site = new Site();
                site.setName(siteName);
                site.setUrl(siteUrl);
                siteRepository.save(site);
            }


            // 2. Проверить, существует ли уже запись для страницы
            if (pageRepository.findByPathAndSiteId(url, site.getId()).isPresent()) {
                logger.info("Страница с URL {} уже существует", url);
                return;
            }

            // 3. Получить HTML-код страницы
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; LemmatizationService/1.0)")
                    .timeout(10_000)
                    .get();
            String htmlContent = document.html();
            String plainText = document.text();

            // 4. Сохранить страницу в таблицу page
            Page page = new Page();
            page.setPath(url);
            page.setContent(htmlContent);
            page.setCode(200); // HTTP status code
            page.setSite(site);
            pageRepository.save(page);

            // 5. Преобразовать текст в набор лемм и их количеств
            HashMap<String, Integer> lemmasWithCount = extractLemmas(plainText);

            // 6. Обработать леммы пакетно
            List<Lemma> lemmasToSave = new ArrayList<>();
            List<Index> indexesToSave = new ArrayList<>();

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
                lemmasToSave.add(lemma);

                // Создать связь леммы и страницы в таблице index
                Index index = new Index();
                index.setLemma(lemma);
                index.setPage(page);
                index.setRank((float) count);
                indexesToSave.add(index);
            }

            lemmaRepository.saveAll(lemmasToSave);
            indexRepository.saveAll(indexesToSave);

        } catch (IOException e) {
            logger.error("Ошибка при обработке страницы с URL {}: {}", url, e.getMessage(), e);
        }
    }


    private HashMap<String, Integer> extractLemmas(String text) {
        HashMap<String, Integer> lemmaCount = new HashMap<>();

        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();

            // Разделяем текст на слова, убираем знаки препинания
            String[] words = text.toLowerCase().replaceAll("\\p{Punct}", "").split("\\s+");

            Arrays.stream(words)
                    .filter(word -> !word.isBlank())
                    .forEach(word -> {
                        try {
                            // Получаем информацию о частях речи и фильтруем
                            List<String> morphInfo = luceneMorph.getMorphInfo(word);
                            boolean isExcluded = morphInfo.stream()
                                    .anyMatch(info -> EXCLUDED_PARTS_OF_SPEECH.stream().anyMatch(info::contains));

                            if (isExcluded) return;

                            // Получаем первую нормальную форму слова
                            List<String> normalForms = luceneMorph.getNormalForms(word);
                            if (!normalForms.isEmpty()) {
                                String lemma = normalForms.get(0);
                                lemmaCount.put(lemma, lemmaCount.getOrDefault(lemma, 0) + 1);
                            }
                        } catch (Exception e) {
                            logger.warn("Ошибка при обработке слова {}: {}", word, e.getMessage());
                        }
                    });
        } catch (Exception e) {
            logger.error("Ошибка при инициализации LuceneMorphology: {}", e.getMessage(), e);
        }

        return lemmaCount;
    }

    public static String stripHtmlTags(String html) {
        return html.replaceAll("<[^>]*>", "").trim();
    }

    public static void main(String[] args) {
        String inputText = "Повторное появление леопарда в Осетии позволяет предположить, что леопард постоянно обитает в некоторых районах Северного Кавказа.";

        LemmatizationService service = new LemmatizationService();
        HashMap<String, Integer> lemmasWithCount = service.extractLemmas(inputText);

        lemmasWithCount.forEach((lemma, count) -> System.out.println(lemma + " — " + count));

        // Пример очистки HTML-кода
        String html = "<html><body><h1>Пример текста</h1><p>Это пример HTML-кода.</p></body></html>";
        String plainText = stripHtmlTags(html);
        System.out.println("Очищенный текст: " + plainText);
    }
}
