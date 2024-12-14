package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.util.List;

public class LemmatizationTest {
    public static void main(String[] args) {
        try {
            // Создаем объект LuceneMorphology для русского языка
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();

            // Слово для лемматизации
            String word = "леса";

            // Получаем нормальные формы слова
            List<String> wordBaseForms = luceneMorph.getNormalForms(word);

            // Выводим каждую нормальную форму
            System.out.println("Нормальные формы слова '" + word + "':");
            wordBaseForms.forEach(System.out::println);
        } catch (Exception e) {
            System.err.println("Ошибка при лемматизации: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

