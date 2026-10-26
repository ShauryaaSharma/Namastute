package com.namastute.users;

import jakarta.persistence.Column;
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

    private String phone;   // E.164 (+91…); null for accounts made by email (V12)

    private String email;   // trimmed, lower-case; null for accounts made by phone

    private String name;

    private String upiId;   // private: only ever shown to the user and their own group members (design 4.4)

    @Column(insertable = false, updatable = false)   // read-only here: the database default 'user'; admins are made with SQL
    private String role;

    protected User() {
        // required by JPA: Hibernate creates the object empty, then fills the fields from the row
    }

    User(String phone, String email) {
        this.phone = phone;
        this.email = email;
    }

    UUID getId() {
        return id;
    }

    boolean isAdmin() {
        return "admin".equals(role);
    }

    String getPhone() {
        return phone;
    }

    String getEmail() {
        return email;
    }

    String getName() {
        return name;
    }

    String getUpiId() {
        return upiId;
    }

    void changeName(String name) {
        this.name = name;
    }

    void changeUpiId(String upiId) {
        this.upiId = upiId;
    }
}
