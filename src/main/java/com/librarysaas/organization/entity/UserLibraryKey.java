package com.librarysaas.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserLibraryKey implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "library_id")
    private Long libraryId;

    public UserLibraryKey() {}

    public UserLibraryKey(Long userId, Long libraryId) {
        this.userId = userId;
        this.libraryId = libraryId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(Long libraryId) {
        this.libraryId = libraryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserLibraryKey that = (UserLibraryKey) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(libraryId, that.libraryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, libraryId);
    }
}
