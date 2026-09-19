package com.planner.photo_calendar.category;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
        this.name = name;
        this.color = color;
        this.displayOrder = displayOrder;
        this.isPrivate = isPrivate;
        this.createdAt = LocalDateTime.now();
        this.deletedAt = null;
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
        this.deletedAt = LocalDateTime.now();
    }
}
