package p1.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import p1.model.UserPreferenceEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreferenceEntity, Long> {
    Optional<UserPreferenceEntity> findByAliases(String alias);

    @Query(value = """
            SELECT * FROM user_preference
            WHERE aliases REGEXP '(^|,)' || :query || '(,|$)'
            LIMIT 3
            """, nativeQuery = true)
    List<UserPreferenceEntity> findBySmartMatch(@Param("query") String query);
}
