package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.model.Lemma;
import searchengine.model.Index;
import org.springframework.transaction.annotation.Transactional;
import java.util.function.Function;
import java.util.stream.Collectors;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.RecursiveAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PageCrawler extends RecursiveAction {
    private static final Logger logger = LoggerFactory.getLogger(PageCrawler.class);
    private final Site site;
    private final String url;
    private final Set<String> visitedUrls;
    private final PageRepository pageRepository;
    private final IndexingService indexingService;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;


    public PageCrawler(Site site, String url, Set<String> visitedUrls, PageRepository pageRepository,
                       IndexingService indexingService, LemmaRepository lemmaRepository,
                       IndexRepository indexRepository) {
        this.site = site;
        this.url = url;
        this.visitedUrls = visitedUrls;
        this.pageRepository = pageRepository;
        this.indexingService = indexingService;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }


    @Override
    protected void compute() {
        if (!checkAndLogStopCondition("Начало обработки")) return;

        synchronized (visitedUrls) {
            if (visitedUrls.contains(url)) {
                logger.debug("URL уже обработан: {}", url);
                return;
            }
            visitedUrls.add(url);
        }

        try {
            long delay = 500 + new Random().nextInt(4500);
            logger.debug("Задержка перед запросом: {} ms для URL: {}", delay, url);
            Thread.sleep(delay);

            if (!checkAndLogStopCondition("Перед запросом")) return;

            logger.info("Обработка URL: {}", url);
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .ignoreContentType(true)
                    .execute();

            handleResponse(response);

        } catch (IOException e) {
            handleError(e);
        } catch (InterruptedException e) {
            logger.warn("Индексация прервана для URL {}: поток остановлен.", url);
            Thread.currentThread().interrupt();
        }
    }

    private void handleResponse(Connection.Response response) throws IOException {
        String contentType = response.contentType();
        int statusCode = response.statusCode();
        String path = new URL(url).getPath();

        if (pageRepository.existsByPathAndSiteId(path, site.getId())) {
            logger.info("Страница {} уже существует. Пропускаем сохранение.", url);
            return;
        }

        Page page = new Page();
        page.setSite(site);
        page.setPath(path);
        page.setCode(statusCode);

        if (contentType != null && contentType.contains("text/html")) {
            Document document = response.parse();
            page.setContent(document.html());
            logger.info("HTML-страница добавлена: {}", url);
            processLinks(document);

            // Анализ текста и сохранение данных
            String text = document.body().text(); // Извлечение текста
            Map<String, Integer> lemmaCount = extractLemmas(text);
            saveLemmas(lemmaCount, site.getId());
            saveIndexData(page, lemmaCount);
        } else {
            page.setContent("Unhandled content type: " + contentType);
            logger.info("Контент с неизвестным типом добавлен: {}", url);
        }

        pageRepository.save(page);
    }


    private void processLinks(Document document) {
        Elements links = document.select("a[href]");
        List<PageCrawler> subtasks = new ArrayList<>();
        for (Element link : links) {
            if (!checkAndLogStopCondition("При обработке ссылок")) return;

            String childUrl = link.absUrl("href");

            // Проверяем, что ссылка принадлежит корневому сайту
            if (!childUrl.startsWith(site.getUrl())) {
                logger.debug("Ссылка {} находится за пределами корневого сайта. Пропускаем.", childUrl);
                continue;
            }

            // Обработка JavaScript ссылок
            if (childUrl.startsWith("javascript:")) {
                logger.info("Обнаружена JavaScript ссылка: {}", childUrl);
                saveJavaScriptLink(childUrl);
                continue;
            }

            // Обработка tel: ссылок
            if (childUrl.startsWith("tel:")) {
                logger.info("Обнаружена телефонная ссылка: {}", childUrl);
                savePhoneLink(childUrl);
                continue;
            }

            String childPath = null;
            try {
                childPath = new URL(childUrl).getPath();
            } catch (Exception e) {
                logger.warn("Ошибка извлечения пути из URL: {}", childUrl);
            }

            synchronized (visitedUrls) {
                if (childPath != null && !visitedUrls.contains(childPath)) {
                    visitedUrls.add(childPath);
                    subtasks.add(new PageCrawler(site, childUrl, visitedUrls, pageRepository,
                            indexingService, lemmaRepository, indexRepository));
                    logger.debug("Добавлена ссылка в обработку: {}", childUrl);
                } else {
                    logger.debug("Ссылка уже обработана: {}", childUrl);
                }
            }

        }
        invokeAll(subtasks);
    }

    private void savePhoneLink(String telUrl) {
        String phoneNumber = telUrl.substring(4); // Убираем "tel:"
        if (pageRepository.existsByPathAndSiteId(phoneNumber, site.getId())) {
            logger.info("Телефонный номер {} уже сохранён. Пропускаем.", phoneNumber);
            return;
        }

        Page page = new Page();
        page.setSite(site);
        page.setPath(phoneNumber);
        page.setCode(0); // Код 0 для телефонных ссылок
        page.setContent("Телефонный номер: " + phoneNumber);
        pageRepository.save(page);

        logger.info("Сохранён телефонный номер: {}", phoneNumber);
    }

    private void saveJavaScriptLink(String jsUrl) {
        if (pageRepository.existsByPathAndSiteId(jsUrl, site.getId())) {
            logger.info("JavaScript ссылка {} уже сохранена. Пропускаем.", jsUrl);
            return;
        }

        Page page = new Page();
        page.setSite(site);
        page.setPath(jsUrl); // Сохраняем полный jsUrl как path
        page.setCode(0); // Код 0 для JavaScript ссылок
        page.setContent("JavaScript ссылка: " + jsUrl);
        pageRepository.save(page);

        logger.info("Сохранена JavaScript ссылка: {}", jsUrl);
    }

    private void handleError(IOException e) {
        logger.warn("Ошибка обработки URL {}: {}", url, e.getMessage());
        Page page = new Page();
        page.setSite(site);
        page.setPath(url);
        page.setCode(0);
        page.setContent("Ошибка обработки: " + e.getMessage());
        pageRepository.save(page);
    }

    private boolean checkAndLogStopCondition(String stage) {
        if (!indexingService.isIndexingInProgress()) {
            logger.info("Индексация прервана на этапе {} для URL: {}", stage, url);
            return false;
        }
        return true;
    }

    public Map<String, Integer> extractLemmas(String text) {
        // Предположим, используется внешний лемматизатор
        Lemmatizer lemmatizer = new Lemmatizer();
        Map<String, Integer> lemmaCount = new HashMap<>();
        List<String> lemmas = lemmatizer.lemmatize(text);

        for (String lemma : lemmas) {
            lemmaCount.put(lemma, lemmaCount.getOrDefault(lemma, 0) + 1);
        }

        return lemmaCount;
    }

    @Transactional
    public void saveLemmas(Map<String, Integer> lemmaCount, Integer siteId) {
        if (lemmaCount == null || lemmaCount.isEmpty()) {
            return; // Если карта лемм пуста или null, ничего не делаем
        }

        // Загружаем существующие леммы одним запросом
        List<String> lemmasToFind = new ArrayList<>(lemmaCount.keySet());
        Map<String, Lemma> existingLemmas = lemmaRepository.findByLemmaInAndSiteId(lemmasToFind, siteId)
                .stream()
                .collect(Collectors.toMap(Lemma::getLemma, Function.identity()));

        List<Lemma> lemmasToUpdate = new ArrayList<>();
        List<Lemma> lemmasToInsert = new ArrayList<>();

        // Разделение на обновляемые и новые леммы
        for (Map.Entry<String, Integer> entry : lemmaCount.entrySet()) {
            String lemma = entry.getKey();
            Integer count = entry.getValue();

            if (existingLemmas.containsKey(lemma)) {
                Lemma existingLemma = existingLemmas.get(lemma);
                existingLemma.setFrequency(existingLemma.getFrequency() + count);
                lemmasToUpdate.add(existingLemma);
            } else {
                Lemma newLemma = new Lemma();
                newLemma.setLemma(lemma);
                newLemma.setSiteId(siteId);
                newLemma.setFrequency(count);
                lemmasToInsert.add(newLemma);
            }
        }

        // Сохранение новых лемм и обновление существующих
        if (!lemmasToInsert.isEmpty()) {
            lemmaRepository.saveAll(lemmasToInsert);
        }
        if (!lemmasToUpdate.isEmpty()) {
            lemmaRepository.saveAll(lemmasToUpdate);
        }
    }


    @Transactional
    public void saveIndexData(Page page, Map<String, Integer> lemmaCount) {
        // Убедитесь, что объект Page сохранён
        if (page.getId() == null) {
            page = pageRepository.saveAndFlush(page);
        }

        List<Index> indices = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : lemmaCount.entrySet()) {
            String lemma = entry.getKey();
            Integer count = entry.getValue();

            Lemma lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemma, page.getSite().getId());
            if (lemmaEntity != null) {
                Index index = new Index();
                index.setPage(page);
                index.setLemma(lemmaEntity);
                index.setRank(count.floatValue());
                indices.add(index);
            }
        }

        indexRepository.saveAll(indices);
    }


}