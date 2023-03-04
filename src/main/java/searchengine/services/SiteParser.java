package searchengine.services;

import searchengine.model.IndexingStatus;
import searchengine.model.SiteDB;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteDBRepository;

import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.ForkJoinPool;

public class SiteParser extends Thread {
    private final SiteDBRepository siteDBRepository;
    private final PageRepository pageRepository;
    private SiteDB siteDB;

    public SiteParser(SiteDBRepository siteDBRepository, PageRepository pageRepository, SiteDB siteDB) {
        this.siteDBRepository = siteDBRepository;
        this.pageRepository = pageRepository;
        this.siteDB = siteDB;
    }

    @Override
    public void run() {
        siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
        siteDB.setStatus(IndexingStatus.INDEXING);
        siteDBRepository.save(siteDB);
        PageParser pageParser = new PageParser( siteDBRepository, pageRepository, siteDB.getUrl(), siteDB);
        siteDB = new ForkJoinPool().invoke(pageParser);
        System.out.println("lllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllllll");

    }
}
