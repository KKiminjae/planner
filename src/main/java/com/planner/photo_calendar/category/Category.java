package com.planner.photo_calendar.category;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;
import com.planner.photo_calendar.common.time.ApplicationTime;

@Entity
@Getter
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String color;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Category(){
    }

    public Category(String name, String color, Integer displayOrder, Boolean isPrivate) {
        this.ownerId = 1L;
        this.name = name;
        this.color = color;
        this.displayOrder = displayOrder;
        this.isPrivate = isPrivate;
        this.createdAt = ApplicationTime.nowUtc();
        this.deletedAt = null;
    }

    public Category(Long ownerId, String name, String color, Integer displayOrder, Boolean isPrivate) {
        this(name, color, displayOrder, isPrivate);
        this.ownerId = ownerId;
    }

    public void update(
            String name,
            String color,
            Boolean isPrivate
    ) {
        this.name = name;
        this.color = color;
        this.isPrivate = isPrivate;
    }

    public void changeDisplayOrder(Integer displayOrder){
        this.displayOrder = displayOrder;
    }

    public void delete(){
        this.deletedAt = ApplicationTime.nowUtc();
    }
}
