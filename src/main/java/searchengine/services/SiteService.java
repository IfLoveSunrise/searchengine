package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.IndexingStatus;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class SiteService extends Thread {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private Site site;
    private static int countInstances = 0;

    @Override
    public void run() {
        PageService pageService = new PageService(siteRepository, pageRepository,
                lemmaRepository, indexRepository, site.getUrl(), site);

        try {
            new ForkJoinPool(Runtime.getRuntime().availableProcessors()).submit(pageService).get();
        } catch (Exception e) {
            e.printStackTrace();
            site.setStatusTime(new Timestamp(new Date().getTime()).toString());
            site.setLastError(e.getMessage());
            site.setStatus(IndexingStatus.FAILED);
        }

        if (site.getStatus().equals(IndexingStatus.INDEXING)) {
            site.setStatus(IndexingStatus.INDEXED);
        }
        siteRepository.saveAndFlush(site);
        decrementCountInstances();
    }

    public Site createSiteDB(String siteName, String siteUrl) {
        site = new Site();
        site.setName(siteName);
        site.setUrl(siteUrl);
        site.setStatus(IndexingStatus.INDEXING);
        site.setStatusTime(new Timestamp(new Date().getTime()).toString());
        siteRepository.saveAndFlush(site);
        return site;
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
