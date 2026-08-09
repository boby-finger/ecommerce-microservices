package com.innowise.orderservice.repository;

import com.innowise.orderservice.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {
    List<Item> findAllByIdIn(List<UUID> ids);
}