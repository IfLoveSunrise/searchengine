package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    @Query(value = "DELETE FROM `index` WHERE page_id = :pageId", nativeQuery = true)
    void deleteAllByPageId(int pageId);
}
