package com.cognologix.fpa.budgeting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "overhead_line_item")
@Getter
@Setter
@NoArgsConstructor
public class OverheadLineItem {

    @Id
    @Column(name = "line_code", length = 100)
    private String lineCode;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
