package searchengine.model;

import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.*;


@Entity
@Table(name = "page", indexes = {@Index(columnList = "path", name = "path_index")})
@NoArgsConstructor
@Getter
@Setter
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    @JoinColumn(name = "site_id")
    private WebSite webSiteId;

    @Column(columnDefinition = "VARCHAR(255)")
    private String path;

    private int code;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    public Page(WebSite webSiteId, String path, int code, String content) {
        this.webSiteId = webSiteId;
        this.path = path;
        this.code = code;
        this.content = content;
    }
}
