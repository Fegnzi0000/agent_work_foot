package com.hyf.agent_work_foot.admin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class LocalAccountMaintenance {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    public LocalAccountMaintenance(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc=jdbc; this.encoder=encoder; }
    @Transactional
    public String createAdmin(String account, String password) {
        if (account == null || !account.matches("^[A-Za-z][A-Za-z0-9_]{2,31}$") || password == null || !password.matches("^[A-Za-z0-9_]{6,20}$")) throw new IllegalArgumentException("Invalid administrator account/password format");
        String normalized = account.toLowerCase(Locale.ROOT);
        var existing = jdbc.query("SELECT u.id,r.code FROM users u JOIN roles r ON r.id=u.role_id WHERE u.admin_login_name=? FOR UPDATE", (rs,n)->new String[]{rs.getString(1),rs.getString(2)},normalized);
        if (!existing.isEmpty()) {
            if (!"ADMIN".equals(existing.getFirst()[1])) throw new IllegalStateException("Account already used by non-administrator");
            return existing.getFirst()[0]; // Never reset an existing password implicitly.
        }
        String role = jdbc.queryForObject("SELECT id FROM roles WHERE code='ADMIN' AND is_active=1",String.class);
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(id,email,password_hash,nickname,role_id,status,onboarding_completed,must_change_password,admin_login_name) VALUES(?,NULL,?,?,?,'ACTIVE',1,0,?)",id,encoder.encode(password),"管理员",role,normalized);
        return id;
    }
    public Map<String,Long> preview(String id) {
        UUID.fromString(id);
        Map<String,Long> counts = new LinkedHashMap<>();
        for (String table : USER_TABLES) counts.put(table,jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id=?",Long.class,id));
        counts.put("users",jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?",Long.class,id));
        counts.put("food_option_tags",jdbc.queryForObject("SELECT COUNT(*) FROM food_option_tags WHERE food_option_id IN (SELECT id FROM food_options WHERE user_id=?)",Long.class,id));
        return counts;
    }
    private static final List<String> USER_TABLES=List.of("refresh_tokens","temporary_passwords","preference_items","user_budget_histories","slot_spins","diet_records","food_options","user_identities","consent_events","user_consents");
    @Transactional
    public void purgeCancelled(String id, String confirmation) {
        UUID.fromString(id);
        if (!("PURGE "+id).equals(confirmation)) throw new IllegalArgumentException("Exact confirmation required");
        var rows=jdbc.query("SELECT u.status,r.code FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=? FOR UPDATE",(rs,n)->new String[]{rs.getString(1),rs.getString(2)},id);
        if (rows.size()!=1 || !"CANCELLED".equals(rows.getFirst()[0]) || !"USER".equals(rows.getFirst()[1])) throw new IllegalStateException("Only a verified cancelled consumer account may be purged");
        jdbc.update("DELETE FROM food_option_tags WHERE food_option_id IN (SELECT id FROM food_options WHERE user_id=?)",id);
        for (String table : USER_TABLES) jdbc.update("DELETE FROM "+table+" WHERE user_id=?",id);
        jdbc.update("UPDATE admin_audit_logs SET target_user_id=NULL,detail_json=NULL WHERE target_user_id=?",id);
        jdbc.update("DELETE FROM users WHERE id=?",id);
    }
}
