package tn.bricocomptoir.content.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tn.bricocomptoir.content.application.port.out.ManagedContentStore;
import tn.bricocomptoir.content.domain.ManagedContent.*;

@Repository
public class JdbcManagedContentStore implements ManagedContentStore {
    private final JdbcTemplate jdbc;
    public JdbcManagedContentStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Hero hero(boolean publicOnly) {
        long version = jdbc.queryForObject("SELECT version FROM content_hero WHERE id=1", Long.class);
        String sql = "SELECT * FROM content_hero_slide" + (publicOnly ? " WHERE visible" : "") + " ORDER BY position";
        return new Hero(version, jdbc.query(sql, (r, n) -> new Slide(
                r.getObject("id", UUID.class), r.getBoolean("visible"), r.getString("image"),
                r.getString("label"), r.getString("title"), r.getString("description"),
                r.getString("alt"), r.getString("link"), r.getString("action"), r.getString("detail"))));
    }

    @Override public Hero saveHero(Hero hero, UUID actor) {
        int changed = jdbc.update("UPDATE content_hero SET version=version+1,updated_at=now(),updated_by=? WHERE id=1 AND version=?",
                "C:" + actor, hero.version());
        if (changed != 1) throw new IllegalStateException("Hero version conflict");
        jdbc.update("DELETE FROM content_hero_slide");
        int position = 0;
        for (Slide slide : hero.slides()) {
            jdbc.update("INSERT INTO content_hero_slide(id,position,visible,image,label,title,description,alt,link,action,detail) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    slide.id(), position++, slide.visible(), slide.image(), slide.label(), slide.title(),
                    slide.description(), slide.alt(), slide.link(), slide.action(), slide.detail());
        }
        return hero(false);
    }

    @Override public Contact contact() {
        return jdbc.queryForObject("SELECT email,phone,address,version FROM content_contact WHERE id=1",
                (r, n) -> new Contact(r.getString(1), r.getString(2), r.getString(3), r.getLong(4)));
    }

    @Override public Contact saveContact(Contact contact, UUID actor) {
        int changed = jdbc.update("UPDATE content_contact SET email=?,phone=?,address=?,version=version+1,updated_at=now(),updated_by=? WHERE id=1 AND version=?",
                contact.email(), contact.phone(), contact.address(), "C:" + actor, contact.version());
        if (changed != 1) throw new IllegalStateException("Contact version conflict");
        return contact();
    }

    @Override public UUID addMessage(MessageInput input) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO content_contact_message(id,name,email,subject,body,status) VALUES (?,?,?,?,?,'NEW')",
                id, input.name().trim(), input.email().trim().toLowerCase(java.util.Locale.ROOT),
                input.subject().trim(), input.body().trim());
        return id;
    }

    @Override public MessagePage messages(int page, int size, String status) {
        long total = jdbc.queryForObject("SELECT count(*) FROM content_contact_message WHERE status=?", Long.class, status);
        List<Message> items = jdbc.query("SELECT * FROM content_contact_message WHERE status=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
                this::message, status, size, page * size);
        return new MessagePage(items, page, size, total);
    }

    @Override public void resolveMessage(UUID id) {
        if (jdbc.update("UPDATE content_contact_message SET status='RESOLVED',resolved_at=now() WHERE id=? AND status='NEW'", id) != 1)
            throw new IllegalStateException("Message already resolved or missing");
    }

    private Message message(ResultSet r, int row) throws SQLException {
        return new Message(r.getObject("id", UUID.class), r.getString("name"), r.getString("email"),
                r.getString("subject"), r.getString("body"), r.getString("status"),
                r.getObject("created_at", OffsetDateTime.class));
    }
}
