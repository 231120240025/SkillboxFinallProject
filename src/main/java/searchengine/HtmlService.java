package searchengine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.tartarus.snowball.ext.RussianStemmer;
import org.tartarus.snowball.ext.EnglishStemmer;
import searchengine.model.*;
import searchengine.repository.PageRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.IndexRepository;
import searchengine.repository.SiteRepository;
import searchengine.config.SitesList;
import searchengine.config.ConfigSite;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
public class HtmlService implements CommandLineRunner {

    private final SitesList sitesList;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;

    @Autowired
    public HtmlService(SitesList sitesList, PageRepository pageRepository, SiteRepository siteRepository, LemmaRepository lemmaRepository, IndexRepository indexRepository) {
        this.sitesList = sitesList;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.siteRepository = siteRepository;
    }

    public void fetchHtmlFromSites() {
        List<ConfigSite> sites = sitesList.getSites();

        if (sites == null || sites.isEmpty()) {
            System.err.println("Список сайтов пустой или не инициализирован.");
            return;
        }

        for (ConfigSite siteConfig : sites) {
            try {
                // Сообщение о начале обработки сайта
                System.out.println("Начинаем обработку сайта: " + siteConfig.getUrl());

                // Получаем HTML-код страницы
                Document document = Jsoup.connect(siteConfig.getUrl()).get();
                String textContent = document.text();
                System.out.println("Анализ текста для " + siteConfig.getUrl());

                // Выводим сам HTML-контент
                System.out.println("HTML-контент страницы:");
                System.out.println(textContent);

                // Найдем или создадим сайт
                Site site = findOrCreateSite(siteConfig.getUrl(), siteConfig.getName());

                // Разбираем текст на леммы и считаем их частоту
                Map<String, Integer> lemmaCounts = processText(textContent);

                // Выводим леммы и их частоты
                System.out.println("Леммы и их частота:");
                lemmaCounts.forEach((lemma, count) -> System.out.println(lemma + ": " + count));

                // Создаем страницу и сохраняем её
                Page page = new Page();
                page.setSite(site);
                page.setPath(siteConfig.getUrl());  // Используем URL в качестве пути
                page.setCode(200);  // Статус код, для примера, можно установить 200 (ОК)
                page.setContent(textContent);
                page.setStatus(IndexingStatus.INDEXED);
                pageRepository.save(page);

                // Обрабатываем леммы и сохраняем их
                for (Map.Entry<String, Integer> entry : lemmaCounts.entrySet()) {
                    String lemmaText = entry.getKey();
                    Integer frequency = entry.getValue();

                    // Получаем или создаем лемму для этого сайта
                    Lemma lemma = lemmaRepository.findByLemma(lemmaText);
                    if (lemma == null) {
                        lemma = new Lemma();
                        lemma.setLemma(lemmaText);
                        lemma.setFrequency(1);
                        lemma.setSite(site);  // Лемма привязывается к сайту
                        lemmaRepository.save(lemma);
                    } else {
                        lemma.setFrequency(lemma.getFrequency() + 1);  // Увеличиваем частоту
                        lemmaRepository.save(lemma);
                    }

                    // Сохраняем связь леммы с страницей в таблице index
                    Index index = new Index();
                    index.setPage(page);
                    index.setLemma(lemma);
                    index.setRank(frequency.floatValue());  // Преобразуем Integer в Float
                    // Частота леммы на странице
                    indexRepository.save(index);
                }

                // Сообщение о завершении обработки сайта
                System.out.println("Завершена обработка сайта: " + siteConfig.getUrl());

            } catch (IOException e) {
                System.err.println("Ошибка при получении HTML с сайта " + siteConfig.getUrl());
                e.printStackTrace();
            }
        }
    }

    private Site findOrCreateSite(String url, String name) {
        // Проверяем, существует ли сайт с таким URL
        Site site = siteRepository.findByUrl(url);
        if (site == null) {
            site = new Site();
            site.setUrl(url);
            site.setName(name);
            site.setStatus(IndexingStatus.INDEXING);  // Ставим статус индексации
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
        return site;
    }

    private Map<String, Integer> processText(String text) {
        // Приводим текст к нижнему регистру и удаляем все, кроме букв, цифр и пробелов
        String cleanedText = text.toLowerCase().replaceAll("[^a-zа-яё0-9\\s]", "");

        // Разбиваем текст на слова
        String[] words = cleanedText.split("\\s+");

        // Инициализируем стеммеры для русского и английского языков
        RussianStemmer russianStemmer = new RussianStemmer();
        EnglishStemmer englishStemmer = new EnglishStemmer();

        // Создаем мапу для хранения лемм и их частоты
        Map<String, Integer> lemmaCounts = new HashMap<>();

        // Перечень исключаемых слов и паттернов
        Set<String> excludedWords = new HashSet<>(Arrays.asList(
                "usb", "vivo", "tecno", "infinix", "purpl", "вднх", "каталог", "серебрян", "iphone", "android",
                "pdf", "en", "ec", "денис", "серг", "миха", "государствен", "академик", "вышел", "многослойн", "профессиональн"
        ));

        // Паттерн для слов, состоящих только из цифр
        String numberPattern = "^[0-9]+$";

        // Обрабатываем каждое слово
        for (String word : words) {
            if (word.isEmpty()) continue; // Пропускаем пустые строки

            // Пропускаем слова, состоящие из аббревиатур, чисел или из списка исключений
            if (word.matches(numberPattern) || excludedWords.contains(word)) {
                continue;
            }

            String lemma;

            // Пытаемся определить язык и применить соответствующий стеммер
            if (word.matches("[а-яё]+")) { // Русские слова
                russianStemmer.setCurrent(word);
                russianStemmer.stem();
                lemma = russianStemmer.getCurrent();
            } else if (word.matches("[a-z]+")) { // Английские слова
                englishStemmer.setCurrent(word);
                englishStemmer.stem();
                lemma = englishStemmer.getCurrent();
            } else {
                continue; // Пропускаем слова, которые не подходят под шаблон
            }

            // Увеличиваем счетчик для леммы
            lemmaCounts.put(lemma, lemmaCounts.getOrDefault(lemma, 0) + 1);
        }

        // Сортируем леммы по частоте и возвращаем отсортированную мапу
        return lemmaCounts.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }



    @Override
    public void run(String... args) throws Exception {
        // Когда приложение запущено, вызываем метод для получения HTML
        fetchHtmlFromSites();
    }

    public static void main(String[] args) {
        SpringApplication.run(HtmlService.class, args);  // Запуск приложения Spring Boot
    }
}