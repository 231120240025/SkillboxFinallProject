package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import searchengine.model.Page;
import java.util.Optional;
import java.util.List;
import searchengine.model.Site;
import org.springframework.data.repository.query.Param;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {

    @Modifying
    @Transactional
    @Query("DELETE FROM Page p WHERE p.site.id = :siteId")
    int deleteAllBySiteId(int siteId);

    @Query("SELECT COUNT(p) > 0 FROM Page p WHERE p.path = :path AND p.site.id = :siteId")
    boolean existsByPathAndSiteId(String path, int siteId);

    boolean existsBySiteAndPath(searchengine.model.Site site, String path);

    @Query("SELECT p FROM Page p WHERE p.path = :path AND p.site.id = :siteId")
    Optional<Page> findByPathAndSiteId(String path, int siteId);

    @Query("SELECT p FROM Page p WHERE p.site.id = :siteId")
    List<Page> findAllBySiteId(int siteId);

    @Query("SELECT s FROM Site s WHERE s.url = :url")
    Optional<Site> findByUrl(@Param("url") String url);

    @Query("SELECT p FROM Page p WHERE p.path = :path")
    Optional<Page> findByPath(@Param("path") String path);
}
