package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteDB;

@Repository
public interface SiteDBRepository extends JpaRepository<SiteDB, Integer> {
    SiteDB findByUrl(String url);
}
