/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.mifos.accounting.service.internal.service;

import io.mifos.accounting.api.v1.domain.Account;
import io.mifos.accounting.api.v1.domain.AccountCommand;
import io.mifos.accounting.api.v1.domain.AccountEntry;
import io.mifos.accounting.api.v1.domain.AccountEntryPage;
import io.mifos.accounting.api.v1.domain.AccountPage;
import io.mifos.accounting.service.internal.mapper.AccountCommandMapper;
import io.mifos.accounting.service.internal.mapper.AccountEntryMapper;
import io.mifos.accounting.service.internal.mapper.AccountMapper;
import io.mifos.accounting.service.internal.repository.AccountEntity;
import io.mifos.accounting.service.internal.repository.AccountEntryEntity;
import io.mifos.accounting.service.internal.repository.AccountEntryRepository;
import io.mifos.accounting.service.internal.repository.AccountRepository;
import io.mifos.accounting.service.internal.repository.CommandEntity;
import io.mifos.accounting.service.internal.repository.CommandRepository;
import io.mifos.accounting.service.internal.repository.specification.AccountSpecification;
import io.mifos.core.lang.DateRange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AccountService {

  private final AccountRepository accountRepository;
  private final AccountEntryRepository accountEntryRepository;
  private final CommandRepository commandRepository;

  @Autowired
  public AccountService(final AccountRepository accountRepository,
                        final AccountEntryRepository accountEntryRepository,
                        final CommandRepository commandRepository) {
    super();
    this.accountRepository = accountRepository;
    this.accountEntryRepository = accountEntryRepository;
    this.commandRepository = commandRepository;
  }

  public Optional<Account> findAccount(final String identifier) {
    final AccountEntity accountEntity = this.accountRepository.findByIdentifier(identifier);
    if (accountEntity == null) {
      return Optional.empty();
    } else {
      return Optional.of(AccountMapper.map(accountEntity));
    }
  }

  public AccountPage fetchAccounts(
      final boolean includeClosed, final String term, final String type,
      final boolean includeCustomerAccounts, final Pageable pageable) {

    final Page<AccountEntity> accountEntities = this.accountRepository.findAll(
        AccountSpecification.createSpecification(includeClosed, term, type, includeCustomerAccounts), pageable
    );

    final AccountPage accountPage = new AccountPage();
    accountPage.setTotalPages(accountEntities.getTotalPages());
    accountPage.setTotalElements(accountEntities.getTotalElements());

    if(accountEntities.getSize() > 0){
      final List<Account> accounts = new ArrayList<>(accountEntities.getSize());
      accountEntities.forEach(accountEntity -> accounts.add(AccountMapper.map(accountEntity)));
      accountPage.setAccounts(accounts);
    }

    return accountPage;

  }

  public AccountEntryPage fetchAccountEntries(final String identifier,
                                              final DateRange range,
                                              final @Nullable String message,
                                              final Pageable pageable){

    final AccountEntity accountEntity = this.accountRepository.findByIdentifier(identifier);

    final Page<AccountEntryEntity> accountEntryEntities;
    if (message == null) {
      accountEntryEntities = this.accountEntryRepository.findByAccountAndTransactionDateBetween(
          accountEntity, range.getStartDateTime(), range.getEndDateTime(), pageable);
    }
    else {
      accountEntryEntities = this.accountEntryRepository.findByAccountAndTransactionDateBetweenAndMessageEquals(
          accountEntity, range.getStartDateTime(), range.getEndDateTime(), message, pageable);
    }

    final AccountEntryPage accountEntryPage = new AccountEntryPage();
    accountEntryPage.setTotalPages(accountEntryEntities.getTotalPages());
    accountEntryPage.setTotalElements(accountEntryEntities.getTotalElements());

    if(accountEntryEntities.getSize() > 0){
      final List<AccountEntry> accountEntries = new ArrayList<>(accountEntryEntities.getSize());
      accountEntryEntities.forEach(accountEntryEntity -> accountEntries.add(AccountEntryMapper.map(accountEntryEntity)));
      accountEntryPage.setAccountEntries(accountEntries);
    }

    return accountEntryPage;
  }

  public final List<AccountCommand> fetchCommandsByAccount(final String identifier) {
    final AccountEntity accountEntity = this.accountRepository.findByIdentifier(identifier);
    final List<CommandEntity> commands = this.commandRepository.findByAccount(accountEntity);
    if (commands != null) {
      return commands.stream().map(AccountCommandMapper::map).collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }

  public Boolean hasEntries(final String identifier) {
    final AccountEntity accountEntity = this.accountRepository.findByIdentifier(identifier);
    return this.accountEntryRepository.existsByAccount(accountEntity);
  }

  public Boolean hasReferenceAccounts(final String identifier) {
    final AccountEntity accountEntity = this.accountRepository.findByIdentifier(identifier);
    return this.accountRepository.existsByReference(accountEntity);
  }

  public List<AccountCommand> getActions(final String identifier) {
    final AccountEntity accountEntity = this.accountRepository.findByIdentifier(identifier);
    final ArrayList<AccountCommand> commands = new ArrayList<>();
    final Account.State state = Account.State.valueOf(accountEntity.getState());
    switch (state) {
      case OPEN:
        commands.add(this.buildCommand(AccountCommand.Action.LOCK));
        commands.add(this.buildCommand(AccountCommand.Action.CLOSE));
        break;
      case LOCKED:
        commands.add(this.buildCommand(AccountCommand.Action.UNLOCK));
        commands.add(this.buildCommand(AccountCommand.Action.CLOSE));
        break;
      case CLOSED:
        commands.add(this.buildCommand(AccountCommand.Action.REOPEN));
        break;
    }
    return commands;
  }

  private AccountCommand buildCommand(final AccountCommand.Action action) {
    final AccountCommand accountCommand = new AccountCommand();
    accountCommand.setAction(action.name());
    return accountCommand;
  }
}
