package searchengine.services;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RecursiveTask;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import searchengine.model.IndexingStatus;
import searchengine.model.Page;
import searchengine.model.SiteDB;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteDBRepository;

public class PagesParserService extends RecursiveTask<SiteDB> {
    private final SiteDBRepository siteDBRepository;
    private final PageRepository pageRepository;
    private final String url;
    private final SiteDB siteDB;
    private Page page;
    public static boolean running = true;

    public PagesParserService(SiteDBRepository siteDBRepository, PageRepository pageRepository, String url, SiteDB siteDB) {
        this.siteDBRepository = siteDBRepository;
        this.pageRepository = pageRepository;
        this.url = url;
        this.siteDB = siteDB;
    }

    @Override
    protected SiteDB compute() {
        System.out.println(url);

        if (!running) stopIndexing();

        String path = url.replaceFirst(siteDB.getUrl(), "/");
        if (running && pageRepository.findByPathAndSiteDBId(path, siteDB.getId()) == null) {

            List<PagesParserService> pagesParserServiceList = new CopyOnWriteArrayList<>();
            Set<String> linkSet = new CopyOnWriteArraySet<>();

            Document document = getJsoupDocumentAndSavePage();

            if (document != null) {
                for (Element element : document.select("a[href]")) {
                    String link = element.absUrl("href");
                    int pointCount = StringUtils.countMatches(link.replace(url, ""), ".");
                    if (!link.isEmpty() && link.startsWith(url) && !link.contains("#") && pointCount == 0
                            && running && !link.equals(url) && !linkSet.contains(link)) {
                        linkSet.add(link);
                        PagesParserService pagesParserService = new PagesParserService(siteDBRepository, pageRepository, link, siteDB);
                        pagesParserService.fork();
                        pagesParserServiceList.add(pagesParserService);
                    }
                }
            }

            for (PagesParserService link : pagesParserServiceList) {
                link.join();
            }
        }
        return siteDB;
    }

    public Document getJsoupDocumentAndSavePage () {
        page = new Page();
        Document document;
        try {
            Thread.sleep(1000);
            Connection connection = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) " +
                            "Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com");
            document = connection.get();

            page.setSite(siteDB);
            page.setPath(url.replaceFirst(siteDB.getUrl(), "/"));
            page.setCode(connection.response().statusCode());
            page.setContent(document.toString());
            siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
            pageRepository.save(page);
        } catch (Exception e) {
            e.printStackTrace();
            document = null;
        }

        return document;
    }

    public Page getPage() {
        return page;
    }

    public void stopIndexing() {
        siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
        siteDB.setLastError("Индексация остановлена пользователем");
        siteDB.setStatus(IndexingStatus.FAILED);
        siteDBRepository.save(siteDB);
    }
}