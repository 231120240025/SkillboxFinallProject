package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {

    // Удалить все записи из таблицы index по ID страницы
    @Modifying
    @Transactional
    @Query("DELETE FROM Index i WHERE i.page.id = :pageId")
    void deleteAllByPageId(int pageId);
}
