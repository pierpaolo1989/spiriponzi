package com.soa.spiriponzi.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.ClaimRepository;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "GAME_CLAIM_INTEGRATION_DB", matches = "true")
// This test recreates the schema: use only a disposable database on this dedicated port.
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55440/game_claim_test",
        "spring.datasource.username=game_test",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ClaimServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameClaimConcurrencyIntegrationTest {
    @Autowired private UserRepository users;
    @Autowired private UserStatsRepository stats;
    @Autowired private ClaimRepository claims;
    @Autowired private ClaimServiceImpl service;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void simultaneousClaimsCreditOnlyOnce() throws Exception {
        String email = "game-concurrent@example.com";
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UserStats balance = stats.saveAndFlush(UserStats.builder()
                    .lastGameClaimAt(LocalDateTime.now().minusHours(3)).build());
            users.saveAndFlush(User.builder().email(email).password("unused").userStats(balance).build());
        });

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> claimAfterBarrier(email, barrier)),
                    executor.submit(() -> claimAfterBarrier(email, barrier)));
            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(20, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(1, accepted);
            assertEquals(50, stats.findByUserEmail(email).orElseThrow().getAnnualPoints());
            assertEquals(1, claims.findTop10ByUserEmailOrderByDateRequestDesc(email).size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentFirstVisitsShareOneCooldown() throws Exception {
        String email = "game-first-visit@example.com";
        long previousStatsCount = stats.count();
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                users.saveAndFlush(User.builder().email(email).password("unused").build()));

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Long>> results = List.of(
                    executor.submit(() -> statusAfterBarrier(email, barrier)),
                    executor.submit(() -> statusAfterBarrier(email, barrier)));
            for (Future<Long> result : results) {
                long remaining = result.get(20, TimeUnit.SECONDS);
                org.junit.jupiter.api.Assertions.assertTrue(remaining >= 3590 && remaining <= 3600);
            }
            assertEquals(previousStatsCount + 1, stats.count());
            assertNotNull(stats.findByUserEmail(email).orElseThrow().getLastGameClaimAt());
            assertThrows(ResponseStatusException.class, () -> service.startGameClaim(email));
            assertEquals(0, claims.findTop10ByUserEmailOrderByDateRequestDesc(email).size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void directFirstClaimPersistsCooldownDespiteRejection() {
        String email = "game-first-claim@example.com";
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UserStats balance = stats.saveAndFlush(new UserStats());
            users.saveAndFlush(User.builder().email(email).password("unused").userStats(balance).build());
        });

        assertThrows(ResponseStatusException.class, () -> service.startGameClaim(email));
        assertNotNull(stats.findByUserEmail(email).orElseThrow().getLastGameClaimAt());
        assertThrows(ResponseStatusException.class, () -> service.startGameClaim(email));
        assertEquals(0, claims.findTop10ByUserEmailOrderByDateRequestDesc(email).size());
    }

    private boolean claimAfterBarrier(String email, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            service.startGameClaim(email);
            return true;
        } catch (ResponseStatusException ex) {
            assertEquals(429, ex.getStatusCode().value());
            return false;
        }
    }

    private long statusAfterBarrier(String email, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return service.getGameClaimCooldownSeconds(email);
    }
}
