package com.swiftpay.ledger.controller;

import com.swiftpay.ledger.domain.dto.response.AccountBalanceResponse;
import com.swiftpay.ledger.domain.entity.Account;
import com.swiftpay.ledger.exception.AccountNotFoundException;
import com.swiftpay.ledger.repository.AccountRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping("/accounts/{userId}/balance")
    public AccountBalanceResponse getBalance(
            @PathVariable Long userId
    ) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found for userId=" + userId
                ));

        return new AccountBalanceResponse(
                account.getUserId(),
                account.getCurrency(),
                account.getBalance()
        );
    }
}
