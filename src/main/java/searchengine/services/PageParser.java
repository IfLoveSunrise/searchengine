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
import org.jsoup.select.Elements;
import searchengine.model.IndexingStatus;
import searchengine.model.Page;
import searchengine.model.SiteDB;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteDBRepository;

public class PageParser extends RecursiveTask<SiteDB> {
    private final SiteDBRepository siteDBRepository;
    private final PageRepository pageRepository;
    private final String url;
    private final SiteDB siteDB;
    public static boolean running = true;

    public PageParser(SiteDBRepository siteDBRepository, PageRepository pageRepository, String url, SiteDB siteDB) {
        this.siteDBRepository = siteDBRepository;
        this.pageRepository = pageRepository;
        this.url = url;
        this.siteDB = siteDB;
    }

    @Override
    protected SiteDB compute() {
        System.out.println(System.lineSeparator().concat(url));

        if (!running) stopIndexing();
        if (running && pageRepository.findByPathAndSiteDBId(
                url.replaceFirst(siteDB.getUrl(), "/"), siteDB.getId()) == null) {

            Elements elements = getElementsAndSavePage();
            if (elements == null) {
                return siteDB;
            }

            List<PageParser> pageParserList = new CopyOnWriteArrayList<>();
            Set<String> linkSet = new CopyOnWriteArraySet<>();

            for (Element element : elements) {
                String link = element.absUrl("href");
                int pointCount = StringUtils.countMatches(link.replace(url, ""), ".");
                if (!link.isEmpty() && link.startsWith(url) && !link.contains("#") && pointCount == 0
                        && running && !link.equals(url) && !linkSet.contains(link)) {
                    linkSet.add(link);
                    PageParser pageParser = new PageParser(siteDBRepository, pageRepository, link, siteDB);
                    pageParser.fork();
                    pageParserList.add(pageParser);
                }
            }

            for (PageParser link : pageParserList) {
                link.join();
            }
        }
        return siteDB;
    }

    public Elements getElementsAndSavePage () {
        Document document = null;
        try {
            Thread.sleep(500);
            Connection connection = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) " +
                            "Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com");
            document = connection.get();

            Page page = new Page();
            page.setSite(siteDB);
            page.setPath(url.replaceFirst(siteDB.getUrl(), "/"));
            page.setCode(connection.response().statusCode());
            page.setContent(document.toString());
            siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
            pageRepository.save(page);
        } catch (Exception e) {
            System.out.println(e.getMessage().concat(" -> ").concat(url));
        }

        return document != null ? document.select("a[href]") : null;
    }

    public void stopIndexing() {
        siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
        siteDB.setLastError("Индексация остановлена пользователем");
        siteDB.setStatus(IndexingStatus.FAILED);
        siteDBRepository.save(siteDB);
    }
}