package tn.bricocomptoir.content.adapter.out.persistence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.content.domain.HomeContent;
import tn.bricocomptoir.content.application.port.out.HomeContentStore;
@Repository
public class JdbcHomeContentStore implements HomeContentStore {
    private final JdbcTemplate jdbc;
    public JdbcHomeContentStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public HomeContent get() {
        return jdbc.queryForObject("SELECT * FROM content_home WHERE id=1",(r,n)->new HomeContent(
            r.getString("title"),r.getString("accent"),r.getString("description"),r.getString("solution_title"),
            r.getString("solution_description"),r.getString("product_title"),r.getString("product_description"),r.getLong("version")));
    }
    public HomeContent save(HomeContent c, String actor) {
        if(jdbc.update("UPDATE content_home SET title=?,accent=?,description=?,solution_title=?,solution_description=?,product_title=?,product_description=?,version=version+1,updated_at=now(),updated_by=? WHERE id=1 AND version=?",
            c.title(),c.accent(),c.description(),c.solutionTitle(),c.solutionDescription(),c.productTitle(),c.productDescription(),actor,c.version()) != 1)
            throw new IllegalStateException("Version conflict");
        return get();
    }
}
