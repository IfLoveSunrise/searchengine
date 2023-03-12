package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class LemmaService {
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    public static boolean running = true;

    public HashMap<String, Integer> getLemmasMap(String text) throws IOException {
        HashMap<String, Integer> lemmasMap = new HashMap<>();
        LuceneMorphology luceneMorph = new RussianLuceneMorphology();

        String[] words = text.replaceAll("[^А-яЁё]+", " ").trim()
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

    public void lemmaAndIndexSave(HashMap<String, Integer> lemmaMap, Site site, Page page) {
        Lemma lemma;
        for (String lemmaString : lemmaMap.keySet()) {
            if (!running) {
                break;
            }
            List<Lemma> oldLemmaList = lemmaRepository.getLemmaListByLemmaAndSiteID(lemmaString, site.getId());
            if (oldLemmaList.isEmpty()) {
                lemma = new Lemma();
                lemma.setSite(site);
                lemma.setLemma(lemmaString);
                lemma.setFrequency(1);
                lemmaRepository.saveAndFlush(lemma);
                Index index = new Index();
                index.setPage(page);
                index.setLemma(lemma);
                index.setRank(lemmaMap.get(lemmaString));
                indexRepository.saveAndFlush(index);
            } else if (oldLemmaList.size() == 1) {
                lemma = oldLemmaList.get(0);
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.saveAndFlush(lemma);
            } else {
                lemma = oldLemmaList.get(0);
                AtomicInteger frequency = new AtomicInteger();
                oldLemmaList.forEach(lemmaCopy -> frequency.addAndGet(lemmaCopy.getFrequency()));
                for (int i = 1; i < oldLemmaList.size(); i++) {
                    lemmaRepository.delete(oldLemmaList.get(i));
                }
                lemma.setFrequency(frequency.addAndGet(1));
                lemmaRepository.saveAndFlush(lemma);
            }
        }
    }
}
