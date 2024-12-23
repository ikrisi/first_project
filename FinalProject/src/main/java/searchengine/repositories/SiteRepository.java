package searchengine.repositories;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.WebSite;

import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<WebSite, Integer>{
    @Query(value = "SELECT * FROM site WHERE url = ?1 LIMIT 1", nativeQuery = true)
    WebSite findByUrl(String url);

    @Query(value = "SELECT * FROM site WHERE status = 'INDEXED'", nativeQuery = true)
    List<WebSite> findIndexed();
}
