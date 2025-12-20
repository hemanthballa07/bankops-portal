package com.bankops.portal.integration;

import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.entity.Transaction;
import com.bankops.portal.repository.AccountRepository;
import com.bankops.portal.repository.CustomerRepository;
import com.bankops.portal.repository.LogEventRepository;
import com.bankops.portal.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionResilienceTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private CustomerRepository customerRepository;

        @Autowired
        private AccountRepository accountRepository;

        @Autowired
        private TransactionRepository transactionRepository;

        @MockBean
        private LogEventRepository logEventRepository;

        private Customer customer;
        private Account account;

        @BeforeEach
        void setUp() {
                // Create test data
                customer = Customer.builder()
                                .firstName("Resilience")
                                .lastName("Test")
                                .email("resilience.test@example.com")
                                .phone("555-9999")
                                .build();
                customer = customerRepository.save(customer);

                account = Account.builder()
                                .customer(customer)
                                .type(Account.AccountType.CHEQUING)
                                .status(Account.AccountStatus.OPEN)
                                .balance(new BigDecimal("1000.00"))
                                .overdraftEnabled(false)
                                .build();
                // Since we are mocking repository in context, we need to be careful.
                // AccountRepository is REAL, so this save works.
                account = accountRepository.save(account);
        }

        @Test
        @WithMockUser(roles = "USER")
        void testTransactionSucceeds_EvenWhenLoggingFails() throws Exception {
                // Given
                CreateTransactionRequest request = new CreateTransactionRequest();
                request.setType("WITHDRAWAL");
                request.setAmount(new BigDecimal("100.00"));

                // Simulate DB failure when saving log event
                doThrow(new RuntimeException("Database connection failed"))
                                .when(logEventRepository).save(any());

                // When
                // NOTE: MOCKMVC usually uses root context, so we remove the /api prefix unless
                // configured otherwise in test
                String response = mockMvc.perform(post("/accounts/{accountId}/transactions", account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated()) // Should still be 201 Created
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                TransactionDto transactionDto = objectMapper.readValue(response, TransactionDto.class);

                // Then
                // Verify transaction is persisted despite logging failure
                Transaction savedTransaction = transactionRepository.findById(transactionDto.getId())
                                .orElseThrow(() -> new AssertionError("Transaction not found in database"));

                assertEquals(Transaction.TransactionStatus.COMPLETED, savedTransaction.getStatus());

                // Verify balance is updated
                Account updatedAccount = accountRepository.findById(account.getId()).orElseThrow();
                assertEquals(0, new BigDecimal("900.00").compareTo(updatedAccount.getBalance()));
        }
}
