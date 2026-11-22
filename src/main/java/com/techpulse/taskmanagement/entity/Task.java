package com.techpulse.taskmanagement.entity;

import com.techpulse.taskmanagement.enums.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasks", indexes = {
        @Index(name = "idx_tasks_tenant", columnList = "tenant_id"),
        @Index(name = "idx_tasks_tenant_assignee", columnList = "tenant_id, assigned_to")
})
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TaskStatus status;

    @Column(name = "assigned_to")
    private Long assignedToId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "assigned_to", referencedColumnName = "id", insertable = false,
                    updatable = false),
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false,
                    updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_tasks_assignee_tenant"))
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_tasks_tenant"))
    private Tenant tenant;

    protected Task() {
    }

    public Task(String title, String description, TaskStatus status, Tenant tenant) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.tenant = tenant;
    }

    public void assignTo(User user) {
        this.assignedToId = user.getId();
    }
    public void updateStatus(TaskStatus status) { this.status = status; }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public User getAssignedTo() { return assignedTo; }
    public Tenant getTenant() { return tenant; }
}
