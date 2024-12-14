package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    // Методы CRUD предоставляются Spring Data JPA автоматически
}
