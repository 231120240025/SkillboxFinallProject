package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;
import searchengine.model.IndexingStatus;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {
    // Найти сайт по URL
    Site findByUrl(String url);

    // Найти все сайты по статусу
    List<Site> findAllByStatus(IndexingStatus status);
}
