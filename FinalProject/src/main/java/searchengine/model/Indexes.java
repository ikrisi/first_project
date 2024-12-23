package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "indexes")
@Getter
@Setter
@NoArgsConstructor
public class Indexes {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false, columnDefinition = "INT")
    private Page page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false, columnDefinition = "INT")
    private Lemma lemma;

    @Column(nullable = false, name = "`quantity`", columnDefinition = "FLOAT")
    private Float quantity;

    public Indexes(Page page, Lemma lemma, Float quantity) {
        this.page = page;
        this.lemma = lemma;
        this.quantity = quantity;
    }
}
