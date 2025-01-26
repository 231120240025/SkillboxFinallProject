package searchengine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import searchengine.config.SitesList;
import searchengine.config.ConfigSite;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.IndexingStatus;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootApplication
public class WebPageService implements CommandLineRunner {

    @Autowired
    private SitesList sitesList;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private SiteRepository siteRepository;

    public void fetchHtmlFromSites() {
        List<ConfigSite> sites = sitesList.getSites();

        if (sites != null) {
            for (ConfigSite siteConfig : sites) {
                try {
                    // Получаем HTML контент с сайта
                    Document document = Jsoup.connect(siteConfig.getUrl()).get();
                    String htmlContent = document.html();

                    System.out.println("HTML контент получен с: " + siteConfig.getUrl());
                    // Выводим первые 500 символов HTML контента для удобства
                    System.out.println("HTML (первые 500 символов): " + (htmlContent.length() > 500 ? htmlContent.substring(0, 500) : htmlContent));

                    // Проверяем, существует ли сайт в базе данных
                    Site site = siteRepository.findByUrl(siteConfig.getUrl());

                    if (site == null) {
                        site = new Site();
                        site.setUrl(siteConfig.getUrl());
                        site.setName(siteConfig.getName());
                        site.setStatus(IndexingStatus.INDEXING); // Устанавливаем статус "Индексация"
                        site.setStatusTime(LocalDateTime.now());
                        siteRepository.save(site); // Сохраняем сайт в базе данных
                    }

                    // Создаем новый объект Page и сохраняем его в базу данных
                    Page page = new Page();
                    page.setSite(site);
                    page.setPath(siteConfig.getUrl());
                    page.setContent(htmlContent);
                    page.setCode(200); // Устанавливаем код ответа 200 (ОК)
                    page.setStatus(IndexingStatus.INDEXED); // Устанавливаем статус страницы "Проиндексировано"

                    pageRepository.save(page); // Сохраняем страницу в базе данных

                    System.out.println("Страница для " + siteConfig.getUrl() + " успешно сохранена.");

                } catch (IOException e) {
                    System.out.println("Ошибка при загрузке страницы: " + siteConfig.getUrl());
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void run(String... args) throws Exception {
        fetchHtmlFromSites();
    }

    public static void main(String[] args) {
        SpringApplication.run(WebPageService.class, args);
    }
}
