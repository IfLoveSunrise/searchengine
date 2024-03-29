package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteDB;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<SiteDB, Integer> {
    @Query(value = "SELECT * FROM site WHERE url = :url", nativeQuery = true)
    List<SiteDB> getSiteListByUrl(String url);

    List<SiteDB> findByName(String name);

    @Query(value = "SELECT id FROM site WHERE url IN :urls", nativeQuery = true)
    List<Integer> getSitesByUrls(List<String> urls);
}
