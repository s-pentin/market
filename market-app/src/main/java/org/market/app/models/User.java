    package org.market.app.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
public class User {

    @Id
    private Long id;
    private String username;

    @Column("password_hash")
    private String passwordHash;

    private Role role;
    private boolean enabled;
}
