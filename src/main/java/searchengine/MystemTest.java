package searchengine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

public class MystemTest {
    public static void main(String[] args) {
        String mystemPath = "C:/tools/mystem/mystem.exe"; // Укажите путь к mystem
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(List.of(mystemPath, "-v"));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
