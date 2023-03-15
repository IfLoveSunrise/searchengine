package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteDB;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LemmaService {
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    public static boolean running = true;

    public HashMap<String, Integer> getLemmasMap(String text) throws IOException {
        HashMap<String, Integer> lemmasMap = new HashMap<>();
        LuceneMorphology luceneMorph = new RussianLuceneMorphology();

        String clearedText = clearHtmlFromTags(text);

        String[] words = clearedText.replaceAll("[^А-яЁё]+", " ")
                .toLowerCase(Locale.ROOT).split("\\s+");

        for (String word : words) {
            if (!running) {
                break;
            }
            word = word.replaceAll("ё", "е");
            if (isNecessaryWord(word, luceneMorph)) {
                List<String> lemmaList = luceneMorph.getNormalForms(word);
                for (String lemma : lemmaList) {
                    lemmasMap.put(lemma, lemmasMap.containsKey(lemma) ? lemmasMap.get(lemma) + 1 : 1);
                }
            }
        }
        return lemmasMap;
    }

    public boolean isNecessaryWord(String word, LuceneMorphology luceneMorph) {
        if (word.length() > 1) {
            List<String> wordMorphInfoList = luceneMorph.getMorphInfo(word);
            for (String wordMorphInfo : wordMorphInfoList) {
                if (!wordMorphInfo.matches(".+[А-Я]$")) {
                    return true;
                }
            }
        }
        return false;
    }

    public void lemmaAndIndexSave(HashMap<String, Integer> lemmaMap, SiteDB siteDB, Page page) {

        for (String lemmaString : lemmaMap.keySet()) {
            if (!running) {
                break;
            }
            Lemma lemma = lemmaRepository.getLemmaByLemmaAndSiteID(lemmaString, siteDB.getId());
            if (lemma == null) {
                lemma = new Lemma();
                lemma.setSite(siteDB);
                lemma.setLemma(lemmaString);
                lemma.setFrequency(1);
            } else {
                lemma.setFrequency(lemma.getFrequency() + 1);
            }
            lemmaRepository.saveAndFlush(lemma);
            indexSave(page, lemma, lemmaMap.get(lemmaString));
        }
    }

    public void indexSave(Page page, Lemma lemma, float rank) {
        Index index = new Index();
        index.setPage(page);
        index.setLemma(lemma);
        index.setRank(rank);
        indexRepository.saveAndFlush(index);
    }

    public String clearHtmlFromTags(String text) {
        return Jsoup.parse(text).text();
    }
}
