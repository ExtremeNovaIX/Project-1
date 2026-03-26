package p1.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "user_preference")
@Data
public class UserPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //模糊匹配别名
    @Column(unique = true, nullable = false)
    private String aliases;

    private String configValue;

    private String description;
}
