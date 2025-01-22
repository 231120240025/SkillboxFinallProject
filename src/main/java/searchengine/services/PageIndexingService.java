package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.config.ConfigSite;

import java.util.List;

@Service
public class PageIndexingService {

    private final SitesList sitesList;

    @Autowired
    public PageIndexingService(SitesList sitesList) {
        this.sitesList = sitesList;
    }

    public boolean isUrlWithinConfiguredSites(String url) {
        List<ConfigSite> sites = sitesList.getSites();
        for (ConfigSite site : sites) {
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
}
