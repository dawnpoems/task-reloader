package com.yegkim.task_reloader_api.auth.repository;

import com.yegkim.task_reloader_api.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
}
