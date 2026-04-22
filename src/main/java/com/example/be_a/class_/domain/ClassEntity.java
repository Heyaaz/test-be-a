package com.example.be_a.class_.domain;
import com.example.be_a.global.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "classes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "enrolled_count", nullable = false)
    private int enrolledCount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClassStatus status;

    private ClassEntity(
        Long creatorId,
        String title,
        String description,
        int price,
        int capacity,
        LocalDate startDate,
        LocalDate endDate,
        ClassStatus status
    ) {
        this.creatorId = creatorId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.capacity = capacity;
        this.enrolledCount = 0;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

    public static ClassEntity createDraft(
        Long creatorId,
        String title,
        String description,
        int price,
        int capacity,
        LocalDate startDate,
        LocalDate endDate
    ) {
        return new ClassEntity(creatorId, title, description, price, capacity, startDate, endDate, ClassStatus.DRAFT);
    }

    public void applyUpdate(UpdateClassCommand command) {
        if (command.hasTitle()) {
            this.title = command.title();
        }
        if (command.hasDescription()) {
            this.description = command.description();
        }
        if (command.hasPrice()) {
            this.price = command.price();
        }
        if (command.hasCapacity()) {
            this.capacity = command.capacity();
        }
        if (command.hasStartDate()) {
            this.startDate = command.startDate();
        }
        if (command.hasEndDate()) {
            this.endDate = command.endDate();
        }
    }
}
