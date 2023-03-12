package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.IndexingStatus;
import searchengine.model.Lemma;
import searchengine.model.SiteDB;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteDBRepository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class SiteParserService extends Thread {
    private final SiteDBRepository siteDBRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private SiteDB siteDB;
    private static int countInstances = 0;

    @Override
    public void run() {
        PagesParserService pagesParserService = new PagesParserService(siteDBRepository, pageRepository,
                lemmaRepository, indexRepository, siteDB.getUrl(), siteDB);

        try {
            new ForkJoinPool(Runtime.getRuntime().availableProcessors()).submit(pagesParserService).get();
        } catch (Exception e) {
            e.printStackTrace();
            siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
            siteDB.setLastError(e.getMessage());
            siteDB.setStatus(IndexingStatus.FAILED);
        }

        if (siteDB.getStatus().equals(IndexingStatus.INDEXING)) {
            siteDB.setStatus(IndexingStatus.INDEXED);
        }
        siteDBRepository.save(siteDB);

        List<Lemma> lemmaList = lemmaRepository.getLemmaListBySiteID(siteDB.getId());
        lemmaList.forEach(lemma -> System.out.println(lemma.getLemma()));

        decrementCountInstances();
    }

    public SiteDB createSiteDB(String siteName, String siteUrl) {
        siteDB = new SiteDB();
        siteDB.setName(siteName);
        siteDB.setUrl(siteUrl);
        siteDB.setStatus(IndexingStatus.INDEXING);
        siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
        siteDBRepository.save(siteDB);
        return siteDB;
    }

    public static int getCountInstances() {
        return countInstances;
    }

    public static void incrementCountInstances() {
        countInstances++;
    }

    public static void decrementCountInstances() {
        countInstances--;
    }


}
