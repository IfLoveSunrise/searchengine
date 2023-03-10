package searchengine.services;

import lombok.SneakyThrows;
import searchengine.model.IndexingStatus;
import searchengine.model.SiteDB;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteDBRepository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.*;

public class SiteParserService extends Thread {
    private final SiteDBRepository siteDBRepository;
    private final PageRepository pageRepository;
    private SiteDB siteDB;
    private static int countInstances = 0;

    public SiteParserService(SiteDBRepository siteDBRepository, PageRepository pageRepository) {
        this.siteDBRepository = siteDBRepository;
        this.pageRepository = pageRepository;
    }

    @Override
    public void run() {
        PagesParserService pagesParserService = new PagesParserService(siteDBRepository, pageRepository, siteDB.getUrl(), siteDB);

        try {
            new ForkJoinPool().submit(pagesParserService).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        if (siteDB.getStatus().equals(IndexingStatus.INDEXING)) {
            siteDB.setStatus(IndexingStatus.INDEXED);
            siteDBRepository.save(siteDB);
        }

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
