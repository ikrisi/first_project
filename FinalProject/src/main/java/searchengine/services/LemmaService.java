package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LemmaService {
    private static final String REGEXP_WORD = "[a-zA-Zа-яА-ЯёЁ]+";
    private static final String[] PARTICLES_NAMES = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ВВОДН", "ЧАСТ", "МС",
            "CONJ", "PART"};

    public ConcurrentHashMap<String, Integer> getLemmas(String text) throws IOException {
        ConcurrentHashMap<String, Integer> lemmas = new ConcurrentHashMap<>();
        LuceneMorphology luceneMorphRus = new RussianLuceneMorphology();
        LuceneMorphology luceneMorphEng = new EnglishLuceneMorphology();
        text = deleteTags(text);
        String morphInfo = "";
        String lemma;
        Pattern pattern = Pattern.compile(REGEXP_WORD);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            lemma = matcher.group().toLowerCase();
            try {
                lemma = luceneMorphRus.getNormalForms(lemma).get(0);
                morphInfo = luceneMorphRus.getMorphInfo(lemma).get(0);
            } catch (WrongCharaterException e) {
                try {
                    lemma = luceneMorphEng.getNormalForms(lemma).get(0);
                    morphInfo = luceneMorphEng.getMorphInfo(lemma).get(0);
                } catch (WrongCharaterException ee) {
                    log.error("Слово " + lemma + " нельзя привести к нормализованной форме");
                }
            }
            if (!isWord(morphInfo)) {
                continue;
            }
            if (lemmas.get(lemma) == null) {
                lemmas.put(lemma, 1);
            } else {
                lemmas.put(lemma, lemmas.get(lemma) + 1);
            }
        }
        return lemmas;
    }

    public String deleteTags(String text) {
        return Jsoup.clean(text, Safelist.none());
    }

    public boolean isWord(String info) {
        for (String particle : PARTICLES_NAMES) {
            if (info.contains(particle)) {
                return false;
            }
        }
        return true;
    }
}
