package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    @Query(value = "SELECT * FROM page WHERE path = :path AND site_id = :idSiteDB", nativeQuery = true)
    Page getPageByPathAndSiteDBId(String path, int idSiteDB);

    @Query(value = "SELECT * FROM page WHERE site_id = :idSiteDB", nativeQuery = true)
    List<Page> getPageListByPathAndSiteDBId(int idSiteDB);
}
