package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    @Query(value = "SELECT path FROM page WHERE path = :path AND site_id = :idSiteDB", nativeQuery = true)
    String findByPathAndSiteDBId(String path, int idSiteDB);

    @Query(value = "DELETE FROM page WHERE path = :path AND site_id = :idSiteDB", nativeQuery = true)
    void deleteByPathAndSiteId(String path, int idSiteDB);

}
