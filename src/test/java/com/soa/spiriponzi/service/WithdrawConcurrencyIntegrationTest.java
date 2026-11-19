package com.soa.spiriponzi.service;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.soa.spiriponzi.dto.WithdrawDto;
import com.soa.spiriponzi.model.User;
import com.soa.spiriponzi.model.UserStats;
import com.soa.spiriponzi.repository.UserRepository;
import com.soa.spiriponzi.repository.UserStatsRepository;
import com.soa.spiriponzi.repository.WithdrawRepository;
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

@EnabledIfEnvironmentVariable(named = "WITHDRAW_INTEGRATION_DB", matches = "true")
// This test recreates the schema: run only against a disposable cluster on this dedicated port.
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55439/spiriponzi_withdraw_test",
        "spring.datasource.username=withdraw_test",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(WithdrawServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WithdrawConcurrencyIntegrationTest {
    @Autowired private UserRepository users;
    @Autowired private UserStatsRepository balances;
    @Autowired private WithdrawRepository withdrawals;
    @Autowired private WithdrawServiceImpl service;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void concurrentRequestsCannotSpendAnAlreadyLoadedBalanceTwice() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            UserStats stats = balances.saveAndFlush(UserStats.builder().annualPoints(3000).build());
            users.saveAndFlush(User.builder().email("concurrent@example.com").password("unused").userStats(stats).build());
        });

        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> requestAfterLoadingStats(tx, bothLoaded)),
                    executor.submit(() -> requestAfterLoadingStats(tx, bothLoaded)));
            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(20, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(1, accepted);
            assertEquals(1, withdrawals.count());
            assertEquals(0, balances.findByUserEmail("concurrent@example.com").orElseThrow().getAnnualPoints());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean requestAfterLoadingStats(TransactionTemplate tx, CyclicBarrier bothLoaded) {
        try {
            tx.executeWithoutResult(status -> {
                User user = users.findByEmail("concurrent@example.com");
                assertEquals(3000, user.getUserStats().getAnnualPoints());
                try {
                    bothLoaded.await(10, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new IllegalStateException("Concurrent test did not synchronize", ex);
                }
                service.startRequest(user.getEmail(), WithdrawDto.builder()
                        .amount(3000).currency("PAYPAL").destination("pay@example.com").build());
            });
            return true;
        } catch (ResponseStatusException ex) {
            return false;
        }
    }
}
