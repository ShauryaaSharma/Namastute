package com.namastute.users;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

// One row of the users table. Only the columns this code uses are mapped; the rest keep their database defaults.
@Entity
@Table(name = "users")
class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String phone;

    private String name;

    protected User() {
        // required by JPA: Hibernate creates the object empty, then fills the fields from the row
    }

    User(String phone) {
        this.phone = phone;
    }

    UUID getId() {
        return id;
    }

    String getPhone() {
        return phone;
    }

    String getName() {
        return name;
    }
}
