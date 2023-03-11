package searchengine.model;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name="page")
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(cascade = CascadeType.MERGE)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SiteDB site;

    @Column(columnDefinition = "TEXT NOT NULL, INDEX index_path (path(50))")
    private String path;
    @Column(nullable = false)
    private int code;
    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

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