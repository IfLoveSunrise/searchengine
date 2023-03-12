package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.IndexingStatus;
import searchengine.model.SiteDB;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteDBRepository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class SiteService extends Thread {
    private final SiteDBRepository siteDBRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private SiteDB siteDB;
    private static int countInstances = 0;

    @Override
    public void run() {
        PageService pageService = new PageService(siteDBRepository, pageRepository,
                lemmaRepository, indexRepository, siteDB.getUrl(), siteDB);

        try {
            new ForkJoinPool(Runtime.getRuntime().availableProcessors()).submit(pageService).get();
        } catch (Exception e) {
            e.printStackTrace();
            siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
            siteDB.setLastError(e.getMessage());
            siteDB.setStatus(IndexingStatus.FAILED);
        }

        if (siteDB.getStatus().equals(IndexingStatus.INDEXING)) {
            siteDB.setStatus(IndexingStatus.INDEXED);
        }
        siteDBRepository.saveAndFlush(siteDB);
        decrementCountInstances();
    }

    public SiteDB createSiteDB(String siteName, String siteUrl) {
        siteDB = new SiteDB();
        siteDB.setName(siteName);
        siteDB.setUrl(siteUrl);
        siteDB.setStatus(IndexingStatus.INDEXING);
        siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
        siteDBRepository.saveAndFlush(siteDB);
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
