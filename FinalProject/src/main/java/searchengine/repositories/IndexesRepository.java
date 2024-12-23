package searchengine.repositories;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Indexes;

import java.util.List;

@Repository
public interface IndexesRepository extends JpaRepository<Indexes, Integer> {
    @Query(value = "select * from indexes i where i.page_id = :pageId and i.lemma_id = :lemmaId", nativeQuery = true)
    Indexes findIndex(@Param("pageId") Integer pageId, @Param("lemmaId") Integer lemmaId);

    @Query(value = "select * from indexes i where i.page_id = :pageId", nativeQuery = true)
    List<Indexes> findAllByPageId(@Param("pageId") Integer pageId);

    @Query(value = "select * from indexes i " +
            "where i.lemma_id in " +
                "(select l.id from lemma l where l.lemma = :lemma) " +
            "AND i.page_id in " +
                "(select p.id from page p where p.site_id = :siteId)", nativeQuery = true)
    List<Indexes> findBySiteId(String lemma, int siteId);

    @Transactional
    @Modifying
    @Query(value = "delete from indexes i where i.page_id = :pageId", nativeQuery = true)
    void deleteAllByPageId(@Param("pageId") Integer pageId);
}
