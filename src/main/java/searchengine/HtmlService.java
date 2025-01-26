package searchengine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.tartarus.snowball.ext.RussianStemmer;
import org.tartarus.snowball.ext.EnglishStemmer;
import searchengine.config.SitesList;
import searchengine.config.ConfigSite;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
public class HtmlService implements CommandLineRunner {

    private final SitesList sitesList;

    @Autowired
    public HtmlService(SitesList sitesList) {
        this.sitesList = sitesList;
    }

    public void fetchHtmlFromSites() {
        List<ConfigSite> sites = sitesList.getSites();

        if (sites == null || sites.isEmpty()) {
            System.err.println("Список сайтов пустой или не инициализирован.");
            return;
        }

        for (ConfigSite site : sites) {
            try {
                // Получаем HTML-код страницы
                Document document = Jsoup.connect(site.getUrl()).get();
                String textContent = document.text();
                System.out.println("Анализ текста для " + site.getUrl());

                // Разбираем текст на леммы и считаем их частоту
                Map<String, Integer> lemmaCounts = processText(textContent);

                // Выводим результат
                lemmaCounts.forEach((lemma, count) ->
                        System.out.println(lemma + ": " + count));

            } catch (IOException e) {
                System.err.println("Ошибка при получении HTML с сайта " + site.getUrl());
                e.printStackTrace();
            }
        }
    }

    private Map<String, Integer> processText(String text) {
        // Разбиваем текст на слова
        String[] words = text.toLowerCase()
                .replaceAll("[^a-zа-яё\\s]", "")
                .split("\\s+");

        // Инициализируем стеммеры для русского и английского языков
        RussianStemmer russianStemmer = new RussianStemmer();
        EnglishStemmer englishStemmer = new EnglishStemmer();

        Map<String, Integer> lemmaCounts = new HashMap<>();

        for (String word : words) {
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
