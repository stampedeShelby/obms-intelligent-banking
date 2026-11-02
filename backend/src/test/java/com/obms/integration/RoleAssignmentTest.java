package com.obms.integration;

import com.obms.domain.Role;
import com.obms.domain.UserAccount;
import com.obms.repository.RoleRepository;
import com.obms.repository.UserAccountRepository;
import com.obms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
 * <p>Both tests below run the load-modify-save sequence inside a single
 * transaction, via TransactionTemplate. That detail is the whole test: with
 * no surrounding transaction, findById returns a <em>detached</em> entity and
 * merge copies into a freshly loaded managed copy whose collection is a
 * mutable Hibernate PersistentSet - so the bug does not reproduce and the
 * test passes whether or not the fix is present. Inside one transaction the
 * entity stays managed and merge copies into the assigned set itself, which
 * is the path the seeder took.</p>
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

    private TransactionTemplate transactionTemplate;

    @Autowired
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

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
    @DisplayName("roles can be reassigned on a managed user without crashing")
    void reassigningRolesOnPersistentUserSucceeds() {
        UserAccount created = persistUser("merge-ok@test.obms");

        // The exact sequence the seeder performs, in one transaction so the
        // loaded entity stays managed: load, replace roles, save.
        assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
            UserAccount managed = userRepository.findById(created.getId()).orElseThrow();
            managed.setRoles(new HashSet<>(List.of(customerRole(), staffRole())));
            userRepository.saveAndFlush(managed);
        })).doesNotThrowAnyException();

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

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            UserAccount managed = userRepository.findById(created.getId()).orElseThrow();
            managed.setRoles(Set.of(customerRole(), staffRole()));
            userRepository.saveAndFlush(managed);
        }))
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
