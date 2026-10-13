package com.namastute.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data writes the implementation at startup, including the SQL for findByPhone from the method name
interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);
}
