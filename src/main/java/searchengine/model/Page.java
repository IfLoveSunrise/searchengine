package searchengine.model;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.util.List;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name="page")
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SiteDB site;

    @Column(columnDefinition = "TEXT NOT NULL, INDEX index_path (path(50))")
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL)
    private List<Index> indexList;

    @Override
    public String toString() {
        return "Page{" + System.lineSeparator() +
                "id=" + id + System.lineSeparator() +
                ", site=" + site + System.lineSeparator() +
                ", path='" + path + '\'' + System.lineSeparator() +
                ", code=" + code + System.lineSeparator() +
                ", content='" +'\'' + System.lineSeparator() +
                '}';
    }
}