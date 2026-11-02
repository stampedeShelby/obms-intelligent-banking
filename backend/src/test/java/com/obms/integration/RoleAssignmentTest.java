package com.obms.integration;

import com.obms.domain.Role;
import com.obms.domain.UserAccount;
import com.obms.repository.RoleRepository;
import com.obms.repository.UserAccountRepository;
import com.obms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for the startup crash the first real deployment hit.
 *
 * <p>Running the stack under Docker Compose for the first time, the backend
 * died during demo seeding with {@code UnsupportedOperationException} from
 * {@code ImmutableCollections.clear()}, reached through Hibernate's
 * {@code CollectionType.replaceElements}. The cause: role sets were assigned
 * with {@code Set.of(...)}. When Spring Data's {@code save()} is handed an
 * entity that is already persistent it goes through Hibernate {@code merge},
 * and merge clears the target collection before copying values into it -
 * which an immutable set refuses.</p>
 *
 * <p>Nothing caught it beforehand for two compounding reasons: the demo
 * seeder is disabled in tests ({@code obms.demo.enabled=false}), and
 * registration happens to take the {@code persist} path rather than
 * {@code merge}, so the immutable set there never got cleared. The failure
 * needed a save of an <em>existing</em> user, which only the seeder did.</p>
 *
 * <p>The second test below deliberately asserts that an immutable set still
 * fails, so this file documents the trap rather than just the fix. If a future
 * Hibernate version stops clearing the collection, that test turns red and
 * tells us the constraint has changed.</p>
 */
class RoleAssignmentTest extends IntegrationTestBase {

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Role customerRole() {
        return roleRepository.findByName("CUSTOMER").orElseThrow();
    }

    private Role staffRole() {
        return roleRepository.findByName("BANK_STAFF").orElseThrow();
    }

    private UserAccount persistUser(String username) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash("$2a$12$ignoredhashvalueforthistestonlyxxxxxxxxxxxxxxxxxxxxxxxxx");
        user.setRoles(new HashSet<>(List.of(customerRole())));
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("roles can be reassigned on an existing user without crashing")
    void reassigningRolesOnPersistentUserSucceeds() {
        UserAccount created = persistUser("merge-ok@test.obms");

        UserAccount reloaded = userRepository.findById(created.getId()).orElseThrow();
        // This is the exact sequence the seeder performs and that crashed:
        // load an existing user, replace its roles, save again.
        reloaded.setRoles(new HashSet<>(List.of(customerRole(), staffRole())));

        assertThatCode(() -> userRepository.saveAndFlush(reloaded))
                .doesNotThrowAnyException();

        UserAccount after = userRepository.findById(created.getId()).orElseThrow();
        assertThat(after.getRoles())
                .extracting(Role::getName)
                .containsExactlyInAnyOrder("CUSTOMER", "BANK_STAFF");

        userRepository.deleteById(created.getId());
    }

    @Test
    @DisplayName("an immutable role set still breaks merge, which is why we use HashSet")
    void immutableRoleSetFailsOnMerge() {
        UserAccount created = persistUser("merge-trap@test.obms");

        UserAccount reloaded = userRepository.findById(created.getId()).orElseThrow();
        reloaded.setRoles(Set.of(customerRole(), staffRole()));

        assertThatThrownBy(() -> userRepository.saveAndFlush(reloaded))
                .as("Hibernate merge clears the target collection; Set.of cannot be cleared")
                .isInstanceOf(UnsupportedOperationException.class);

        // No cleanup here on purpose: the failed flush leaves the persistence
        // context unusable, so a delete would raise a second, misleading error.
        // The H2 test database is create-drop, so the row goes with it.
    }

    @Test
    @DisplayName("a brand new user with roles persists cleanly")
    void newUserWithRolesPersists() {
        UserAccount created = persistUser("persist-ok@test.obms");

        assertThat(created.getId()).isNotNull();
        assertThat(userRepository.findById(created.getId()).orElseThrow().getRoles())
                .extracting(Role::getName)
                .containsExactly("CUSTOMER");

        userRepository.deleteById(created.getId());
    }
}
