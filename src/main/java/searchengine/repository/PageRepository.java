package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import searchengine.model.Page;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {

    @Modifying
    @Transactional
    @Query("DELETE FROM Page p WHERE p.site.url = :siteUrl")  // Используем URL сайта
    int deleteBySiteUrl(String siteUrl);

    @Modifying
    @Transactional
    @Query("DELETE FROM Page p WHERE p.site.id = :siteId")
    int deleteAllBySiteId(int siteId);

    @Query("SELECT COUNT(p) > 0 FROM Page p WHERE p.path = :path AND p.site.id = :siteId")
    boolean existsByPathAndSiteId(String path, int siteId);
}
