package com.innowise.userservice.repository;

import com.innowise.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    @Query("select u from User u left join fetch u.cards where u.id = :user_id")
    Optional<User> findByIdWithCards(@Param("user_id") UUID user_id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    //сброс изменений несохранненых + сброс кэша в памяти, чтобы данные в ней обновилсь
    @Query("update User u set u.active = :active, u.updatedAt = :now where u.id = :user_id")
    int updateActiveStatusUserById(@Param("user_id") UUID user_id,
                       @Param("now") Instant now,
                       @Param("active") Boolean active);
}
