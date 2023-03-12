package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
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
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class LemmaService {
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    public HashMap<String, Integer> getLemmasMap(String text) throws IOException {
        HashMap<String, Integer> lemmasMap = new HashMap<>();
        LuceneMorphology luceneMorph = new RussianLuceneMorphology();

        String[] words = text.replaceAll("[^А-яЁё]+", " ").trim()
                .toLowerCase(Locale.ROOT).split("\\s+");

        for (String word : words) {
            word = word.replaceAll("ё", "е");
            if (word.length() > 1) {
                List<String> wordBaseForms = luceneMorph.getMorphInfo(word);
                for (String wordBaseForm : wordBaseForms) {
                    if (!wordBaseForm.matches(".+[А-Я]$")) {
                        String lemma = word.replace("|.+$", "");
                        lemmasMap.put(lemma, lemmasMap.containsKey(lemma) ? lemmasMap.get(lemma) + 1 : 1);
                    }
                }
            }
        }
        return lemmasMap;
    }

    public void lemmaAndIndexSave(HashMap<String, Integer> lemmaMap, SiteDB siteDB, Page page) {
        Lemma lemma;
        for (String lemmaString : lemmaMap.keySet()) {
            List<Lemma> oldLemmaList = lemmaRepository.getLemmaListByLemmaAndSiteID(lemmaString, siteDB.getId());
            if (oldLemmaList.isEmpty()) {
                lemma = new Lemma();
                lemma.setSite(siteDB);
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
