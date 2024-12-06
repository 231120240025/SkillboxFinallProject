package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "site")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IndexingStatus status;

    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(length = 255, nullable = false, unique = true)
    private String url;

    @Column(length = 255, nullable = false)
    private String name;

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Page> pages = new ArrayList<>();

    // Добавление страницы
    public void addPage(Page page) {
        pages.add(page);
        page.setSite(this);
    }

    // Удаление страницы
    public void removePage(Page page) {
        pages.remove(page);
        page.setSite(null);
    }

    // Очистка всех страниц
    public void clearPages() {
        for (Page page : new ArrayList<>(pages)) {
            removePage(page);
        }
    }
}
