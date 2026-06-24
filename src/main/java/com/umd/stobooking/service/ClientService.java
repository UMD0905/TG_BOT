package com.umd.stobooking.service;

import com.umd.stobooking.model.Client;
import com.umd.stobooking.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    public Optional<Client> findByTelegramId(long telegramUserId) {
        return clientRepository.findByTelegramUserId(telegramUserId);
    }

    public boolean exists(long telegramUserId) {
        return clientRepository.findByTelegramUserId(telegramUserId).isPresent();
    }

    @Transactional
    public Client registerOrUpdate(long telegramUserId, String username, String firstName) {
        return clientRepository.findByTelegramUserId(telegramUserId)
                .map(existing -> {
                    existing.setTelegramUsername(username);
                    existing.setFirstName(firstName);
                    return clientRepository.save(existing);
                })
                .orElseGet(() -> {
                    Client c = new Client();
                    c.setTelegramUserId(telegramUserId);
                    c.setTelegramUsername(username);
                    c.setFirstName(firstName);
                    c.setCreatedAt(LocalDateTime.now());
                    return clientRepository.save(c);
                });
    }

    @Transactional
    public Client savePhone(long telegramUserId, String phone) {
        Client client = clientRepository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> new IllegalStateException("Client not found: " + telegramUserId));
        client.setPhone(phone);
        return clientRepository.save(client);
    }
}
