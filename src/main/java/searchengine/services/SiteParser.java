package searchengine.services;

import lombok.Getter;
import searchengine.config.SitesList;
import searchengine.model.IndexingStatus;
import searchengine.model.SiteDB;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteDBRepository;

import java.util.concurrent.*;

public class SiteParser extends Thread {
    private final SiteDBRepository siteDBRepository;
    private final PageRepository pageRepository;
    private final SiteDB siteDB;
    private static int countInstances = 0;

    public SiteParser(SiteDBRepository siteDBRepository, PageRepository pageRepository, SiteDB siteDB) {
        this.siteDBRepository = siteDBRepository;
        this.pageRepository = pageRepository;
        this.siteDB = siteDB;
        countInstances++;
    }

    @Override
    public void run() {
        PageParser pageParser = new PageParser( siteDBRepository, pageRepository, siteDB.getUrl(), siteDB);
        try {
            new ForkJoinPool().submit(pageParser).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        countInstances--;
        if (siteDB.getStatus().equals(IndexingStatus.INDEXING)) {
            siteDB.setStatus(IndexingStatus.INDEXED);
            siteDBRepository.save(siteDB);
        }
    }

    public static int getCountInstances() {
        return countInstances;
    }

    public static void setCountInstances(int countInstances) {
        SiteParser.countInstances = countInstances;
    }
}
