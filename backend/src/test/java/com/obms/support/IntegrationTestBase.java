package com.obms.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared setup for integration tests.
 *
 * <p>Note the {@code NOT_SUPPORTED} propagation: these tests deliberately do
 * <em>not</em> run inside a surrounding transaction. The usual
 * {@code @Transactional} test pattern would roll everything back at the end,
 * but it would also mean the service's own transaction boundaries never
 * actually commit - and the behaviour under test here <em>is</em> the
 * transaction boundary. Testing atomicity inside an outer transaction would
 * prove nothing. Tests clean up after themselves instead.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestDataFactory.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class IntegrationTestBase {
}
