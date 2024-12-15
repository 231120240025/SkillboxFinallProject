package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    Optional<Lemma> findByLemma(String lemma);

    Lemma findByLemmaAndSiteId(String lemma, Integer siteId);

    @Modifying
    @Transactional
    @Query("UPDATE Lemma l SET l.frequency = l.frequency + :increment WHERE l.lemma = :lemma AND l.siteId = :siteId")
    void updateLemmaFrequency(@Param("lemma") String lemma, @Param("increment") Integer increment, @Param("siteId") Integer siteId);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO lemma (lemma, frequency, site_id) VALUES (:lemma, :frequency, :siteId)", nativeQuery = true)
    void insertLemma(@Param("lemma") String lemma, @Param("frequency") Integer frequency, @Param("siteId") Integer siteId);

    @Query("SELECT l FROM Lemma l WHERE l.lemma IN :lemmas AND l.siteId = :siteId")
    List<Lemma> findByLemmaInAndSiteId(@Param("lemmas") List<String> lemmas, @Param("siteId") Integer siteId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO lemma (lemma, frequency, site_id) 
            VALUES (:lemma, :frequency, :siteId) 
            ON DUPLICATE KEY UPDATE 
            frequency = frequency + VALUES(frequency)
            """, nativeQuery = true)
    void upsertLemma(@Param("lemma") String lemma, @Param("frequency") Integer frequency, @Param("siteId") Integer siteId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO lemma (lemma, frequency, site_id) 
            VALUES (:lemma, :frequency, :siteId)
            """, nativeQuery = true)
    void batchInsertLemmas(@Param("lemma") String lemma, @Param("frequency") Integer frequency, @Param("siteId") Integer siteId);
}
