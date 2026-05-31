package com.bankops.portal.config;

import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Seeds one demo customer + one funded account on an empty database.
 *
 * The local profile runs an in-memory H2 DB that is wiped on every restart,
 * so without this seed account id=1 never exists after a reboot — which breaks
 * both the Step 4 fraud demo (POST /accounts/1/transactions) and the
 * trifecta-console, which hardcodes releaseTransaction(1, ...). On a fresh H2
 * the first customer and first account both land on id=1.
 *
 * Local profile only (never prod) and idempotent: it no-ops when any customer
 * already exists, so it will not duplicate data or overwrite a manually-created
 * account on an already-populated DB.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class LocalDataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public void run(String... args) {
        long existing = customerRepository.count();
        if (existing > 0) {
            log.info("Local seed skipped — {} customer(s) already present", existing);
            return;
        }

        Customer customer = customerRepository.save(Customer.builder()
                .firstName("Demo")
                .lastName("Customer")
                .email("demo.customer@bankops.local")
                .phone("+1-555-0100")
                .build());

        Account account = accountRepository.save(Account.builder()
                .customer(customer)
                .type(Account.AccountType.CHEQUING)
                .status(Account.AccountStatus.OPEN)
                .balance(new BigDecimal("250000.00"))
                .overdraftEnabled(false)
                .build());

        log.info("Local seed created customer id={} + account id={} (balance {})",
                customer.getId(), account.getId(), account.getBalance());
    }
}
