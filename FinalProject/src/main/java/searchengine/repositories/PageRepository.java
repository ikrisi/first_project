package searchengine.repositories;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Page;

import java.util.List;


@Repository
public interface PageRepository extends JpaRepository<Page, Integer>{
    @Query(value = "SELECT * FROM page WHERE path = ?1 and site_id = ?2 LIMIT 1", nativeQuery = true)
    Page findByPathAndSiteId(String path, int siteId);

    @Query(value = "SELECT * FROM page WHERE site_id = ?1", nativeQuery = true)
    List<Page> findBySiteId(int siteId);

    /*
    @Query(value = "SELECT count(*) FROM page WHERE site_id = ?1", nativeQuery = true)
    int getCountPagesOnSite(int siteId);
     */

    @Query(value = "SELECT count(*) FROM page", nativeQuery = true)
    int getCountPages();
}
