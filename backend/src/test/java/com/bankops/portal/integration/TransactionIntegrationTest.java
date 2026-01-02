package com.bankops.portal.integration;

import com.bankops.portal.dto.CreateTransactionRequest;
import com.bankops.portal.dto.TransactionDto;
import com.bankops.portal.entity.Account;
import com.bankops.portal.entity.Customer;
import com.bankops.portal.entity.LogEvent;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TransactionIntegrationTest {

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

        @Autowired
        private LogEventRepository logEventRepository;

        private Customer customer;
        private Account account;

        @BeforeEach
        void setUp() {
                // Clean up
                logEventRepository.deleteAll();
                transactionRepository.deleteAll();
                accountRepository.deleteAll();
                customerRepository.deleteAll();

                // Create test data
                customer = Customer.builder()
                                .firstName("Integration")
                                .lastName("Test")
                                .email("integration.test@example.com")
                                .phone("555-1234")
                                .build();
                customer = customerRepository.save(customer);

                account = Account.builder()
                                .customer(customer)
                                .type(Account.AccountType.CHEQUING)
                                .status(Account.AccountStatus.OPEN)
                                .balance(new BigDecimal("200.00"))
                                .overdraftEnabled(false)
                                .build();
                account = accountRepository.save(account);
        }

        @Test
        @WithMockUser(roles = "USER")
        void testCreateTransaction_ReturnsCorrelationId() throws Exception {
                // Given
                CreateTransactionRequest request = new CreateTransactionRequest();
                request.setType("DEPOSIT");
                request.setAmount(new BigDecimal("50.00"));

                // When
                String response = mockMvc.perform(post("/accounts/{accountId}/transactions", account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.correlationId").exists())
                                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                                .andExpect(jsonPath("$.amount").value(50.00))
                                .andExpect(jsonPath("$.status").value("COMPLETED"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                TransactionDto transactionDto = objectMapper.readValue(response, TransactionDto.class);
                String correlationId = transactionDto.getCorrelationId();

                // Then
                assertNotNull(correlationId);
                assertFalse(correlationId.isEmpty());
                assertEquals(36, correlationId.length()); // UUID format
        }

        @Test
        @WithMockUser(roles = "USER")
        void testCreateTransaction_StoresTransactionInDatabase() throws Exception {
                // Given
                CreateTransactionRequest request = new CreateTransactionRequest();
                request.setType("DEPOSIT");
                request.setAmount(new BigDecimal("75.00"));

                // When
                String response = mockMvc.perform(post("/accounts/{accountId}/transactions", account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                TransactionDto transactionDto = objectMapper.readValue(response, TransactionDto.class);

                // Then
                Transaction savedTransaction = transactionRepository.findById(transactionDto.getId())
                                .orElseThrow(() -> new AssertionError("Transaction not found in database"));

                assertNotNull(savedTransaction);
                assertEquals(Transaction.TransactionType.DEPOSIT, savedTransaction.getType());
                assertEquals(new BigDecimal("75.00"), savedTransaction.getAmount());
                assertEquals(Transaction.TransactionStatus.COMPLETED, savedTransaction.getStatus());
                assertEquals(transactionDto.getCorrelationId(), savedTransaction.getCorrelationId());
        }

        @Test
        @WithMockUser(roles = "USER")
        void testCreateTransaction_CreatesLogEventForCorrelationId() throws Exception {
                // Given
                CreateTransactionRequest request = new CreateTransactionRequest();
                request.setType("DEPOSIT");
                request.setAmount(new BigDecimal("100.00"));

                // When
                String response = mockMvc.perform(post("/accounts/{accountId}/transactions", account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                TransactionDto transactionDto = objectMapper.readValue(response, TransactionDto.class);
                String correlationId = transactionDto.getCorrelationId();

                // Then
                List<LogEvent> logEvents = logEventRepository.findByCorrelationIdOrderByCreatedAtAsc(correlationId);

                assertFalse(logEvents.isEmpty(), "At least one log event should be created");

                // Verify log events contain the correlation ID
                boolean hasLogWithCorrelationId = logEvents.stream()
                                .anyMatch(log -> correlationId.equals(log.getCorrelationId()));
                assertTrue(hasLogWithCorrelationId, "Log event should have the correlation ID");

                // Verify log events have useful information
                LogEvent firstLog = logEvents.get(0);
                assertNotNull(firstLog.getMessage());
                assertNotNull(firstLog.getLevel());
                assertNotNull(firstLog.getCreatedAt());
        }

        @Test
        @WithMockUser(roles = "USER")
        void testCreateTransaction_UpdatesAccountBalance() throws Exception {
                // Given
                BigDecimal initialBalance = account.getBalance();
                CreateTransactionRequest request = new CreateTransactionRequest();
                request.setType("DEPOSIT");
                request.setAmount(new BigDecimal("50.00"));

                // When
                mockMvc.perform(post("/accounts/{accountId}/transactions", account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                // Then
                Account updatedAccount = accountRepository.findById(account.getId())
                                .orElseThrow(() -> new AssertionError("Account not found"));

                BigDecimal expectedBalance = initialBalance.add(new BigDecimal("50.00"));
                assertEquals(0, expectedBalance.compareTo(updatedAccount.getBalance()),
                                "Account balance should be updated");
        }

        @Test
        @WithMockUser(roles = "USER")
        void testCreateTransaction_ValidatesAmount() throws Exception {
                // Given
                CreateTransactionRequest request = new CreateTransactionRequest();
                request.setType("DEPOSIT");
                request.setAmount(new BigDecimal("-10.00")); // Invalid negative amount

                // When/Then
                mockMvc.perform(post("/accounts/{accountId}/transactions", account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }
}




