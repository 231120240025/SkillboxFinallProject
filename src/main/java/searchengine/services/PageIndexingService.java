package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.config.ConfigSite;  // ConfigSite from the config package
import searchengine.model.Site;  // Site from the model package
import searchengine.model.IndexingStatus;
import searchengine.repository.SiteRepository;
import searchengine.repository.PageRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PageIndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    @Autowired
    public PageIndexingService(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    // Convert ConfigSite to Site
    private Site convertConfigSiteToSite(ConfigSite configSite) {
        Site site = new Site();
        site.setUrl(configSite.getUrl());
        site.setName(configSite.getName());
        site.setStatus(IndexingStatus.INDEXING);  // Set the default status as INDEXING
        site.setStatusTime(LocalDateTime.now());  // Set the current time for statusTime
        return site;
    }

    public boolean isUrlWithinConfiguredSites(String url) {
        List<Site> sites = sitesList.getSites().stream()
                .map(this::convertConfigSiteToSite)  // Convert each ConfigSite to Site
                .collect(Collectors.toList());  // Collect the result as List<Site>

        for (Site site : sites) {
            System.out.println("Проверяем сайт: " + site.getUrl());
            if (url.startsWith(site.getUrl())) {
                return true;
            }
        }
        return false;
    }

    public void indexPage(String url) throws Exception {
        try {
            // Логика индексации страницы (пока просто выводим URL)
            System.out.println("Индексация страницы: " + url);
        } catch (Exception e) {
            throw new Exception("Ошибка индексации страницы: " + e.getMessage());
        }
    }

    // Метод для удаления данных по сайту
    public void deleteSiteData(String siteUrl) throws Exception {
        try {
            // Удаляем все страницы для данного сайта
            pageRepository.deleteBySiteUrl(siteUrl);
            // Удаляем сам сайт
            siteRepository.deleteByUrl(siteUrl);
            System.out.println("Данные для сайта " + siteUrl + " удалены");
        } catch (Exception e) {
            throw new Exception("Ошибка при удалении данных для сайта " + siteUrl + ": " + e.getMessage());
        }
    }

    // Метод для удаления данных по всем сайтам в конфигурации
    public void deleteAllSiteData() throws Exception {
        List<Site> sites = sitesList.getSites().stream()
                .map(this::convertConfigSiteToSite)  // Convert each ConfigSite to Site
                .collect(Collectors.toList());  // Collect the result as List<Site>

        for (Site site : sites) {
            deleteSiteData(site.getUrl());
        }
    }

    // Новый метод для создания нового сайта со статусом INDEXING
    public void createSiteWithIndexingStatus(String siteUrl) {
        try {
            // Создаем новый объект Site
            Site site = new Site();
            site.setUrl(siteUrl);
            site.setName("Default Site Name"); // Укажите имя по умолчанию или получайте его откуда-то
            site.setStatus(IndexingStatus.INDEXING); // Устанавливаем статус INDEXING
            site.setStatusTime(LocalDateTime.now()); // Устанавливаем время текущего статуса

            // Сохраняем сайт в базе данных
            siteRepository.save(site);
            System.out.println("Сайт с URL " + siteUrl + " создан со статусом INDEXING.");
        } catch (Exception e) {
            System.err.println("Ошибка при создании сайта с URL " + siteUrl + ": " + e.getMessage());
        }
    }
}
