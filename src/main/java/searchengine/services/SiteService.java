package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import searchengine.model.IndexingStatus;
import searchengine.model.SiteDB;
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
    private static final Logger LOGGER = LogManager.getLogger(SiteService.class);
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private SiteDB siteDB;

    @Override
    public void run() {
        PageService pageService = new PageService(siteRepository, pageRepository,
                lemmaRepository, indexRepository, siteDB.getUrl(), siteDB);

        try {
            new ForkJoinPool(Runtime.getRuntime().availableProcessors()).submit(pageService).get();
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("Ошибка индексации сайта: ".concat(siteDB.getUrl())
                    .concat(System.lineSeparator()).concat(e.getMessage()));
            siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
            siteDB.setLastError("Ошибка индексации сайта");
            siteDB.setStatus(IndexingStatus.FAILED);
        }

        if (siteDB.getStatus().equals(IndexingStatus.INDEXING)) {
            siteDB.setStatus(IndexingStatus.INDEXED);
        }
        siteRepository.saveAndFlush(siteDB);
        IndexingServiceImpl.decrementCountInstances();
    }

    public SiteDB createSiteDB(String siteName, String siteUrl) {
        siteDB = new SiteDB();
        siteDB.setName(siteName);
        siteDB.setUrl(siteUrl);
        siteDB.setStatus(IndexingStatus.INDEXING);
        siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
        siteRepository.saveAndFlush(siteDB);
        return siteDB;
    }
}
