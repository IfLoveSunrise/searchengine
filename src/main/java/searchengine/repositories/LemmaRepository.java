package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    @Query(value = "SELECT * FROM lemma WHERE site_id = :siteId", nativeQuery = true)
    List<Lemma> getLemmaListSiteID(int siteId);

    @Query(value = "SELECT * FROM lemma WHERE `lemma` = :lemma AND site_id = :siteId", nativeQuery = true)
    Lemma getLemmaBySiteID(String lemma, int siteId);

//    @Query(value = "UPDATE lemma SET frequency = frequency - 1 WHERE id = :lemmaId", nativeQuery = true)
//    void decrementsFrequency(int lemmaId);
}
