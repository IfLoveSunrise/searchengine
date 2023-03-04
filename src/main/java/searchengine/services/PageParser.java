package searchengine.services;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ForkJoinPool;
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
    private String url;
    private SiteDB siteDB;
    private final Set<String> linkSet = new CopyOnWriteArraySet<>();
    public static boolean running = true;

    public PageParser(SiteDBRepository siteDBRepository, PageRepository pageRepository, String url, SiteDB siteDB) {
        this.siteDBRepository = siteDBRepository;
        this.pageRepository = pageRepository;
        this.url = url;
        this.siteDB = siteDB;
    }

    @Override
    protected SiteDB compute() {

        if (running) {
            System.out.println(url);

            List<PageParser> pageParserList = new CopyOnWriteArrayList<>();
            linkSet.add(url);

            try {
                Thread.sleep(500);
                Connection connection = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) " +
                                "Gecko/20070725 Firefox/2.0.0.6")
                        .referrer("http://www.google.com");
                Document document = connection.get();

                Page page = new Page();
                page.setSite(siteDB);
                page.setPath(url.replaceFirst(siteDB.getUrl(), "/"));
                page.setCode(connection.response().statusCode());
                page.setContent(document.toString());
                siteDB.setStatusTime(new Timestamp(new Date().getTime()).toString());
                System.out.println(page);
                pageRepository.save(page);

                Elements elements = document.select("a[href]");

                for (Element element : elements) {
                    String link = element.absUrl("href");
                    int pointCount = StringUtils.countMatches(link.replace(url, ""), ".");

                    if (!link.isEmpty() && link.startsWith(url) && !link.contains("#")
                            && !linkSet.contains(link) && pointCount == 0) {
                        linkSet.add(link);
                        PageParser pageParser = new PageParser(siteDBRepository, pageRepository, link, siteDB);
                        pageParser.fork();
                        pageParserList.add(pageParser);
                    }
                }
            } catch (InterruptedException e) {
                running = false;
                System.out.println("Thread was interrupted, Failed to complete operation!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            for (PageParser link : pageParserList) {
                if (!link.siteDB.getUrl().equals(url)) {
                    siteDB = link.join();
                }
            }
        } else {
            siteDB.setLastError("Индексация остановлена пользователем");
            siteDB.setStatus(IndexingStatus.FAILED);
            Thread.currentThread().interrupt();
            ForkJoinPool.commonPool().shutdown();
        }

        siteDBRepository.save(siteDB);

        return siteDB;
    }
}