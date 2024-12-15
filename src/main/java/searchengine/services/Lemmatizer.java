package searchengine.services;

import java.util.*;

public class Lemmatizer {

    private static final Map<String, String> ENDINGS = new HashMap<>();

    static {
        ENDINGS.put("ами", "а");
        ENDINGS.put("ями", "я");
        ENDINGS.put("ов", "");
        ENDINGS.put("ий", "ий");
        ENDINGS.put("ых", "ый");
        ENDINGS.put("ах", "а");
        ENDINGS.put("ам", "а");
        ENDINGS.put("ым", "ый");
        ENDINGS.put("его", "ий");
        ENDINGS.put("ого", "ой");
    }

    public List<String> lemmatize(String text) {
        List<String> lemmas = new ArrayList<>();
        String[] words = text.toLowerCase().split("\\W+");
        for (String word : words) {
            if (!word.isEmpty()) {
                lemmas.add(removeEnding(word));
            }
        }
        return lemmas;
    }

    private String removeEnding(String word) {
        for (Map.Entry<String, String> entry : ENDINGS.entrySet()) {
            if (word.endsWith(entry.getKey())) {
                return word.substring(0, word.length() - entry.getKey().length()) + entry.getValue();
            }
        }
        return word;
    }

    public static void main(String[] args) {
        Lemmatizer lemmatizer = new Lemmatizer();
        String text = "Привет, как дела? Сегодня мы изучаем лемматизацию слов!";
        List<String> lemmas = lemmatizer.lemmatize(text);
        System.out.println("Леммы: " + lemmas);
    }
}
