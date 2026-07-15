package com.cognologix.fpa.budgeting.repository;

import com.cognologix.fpa.budgeting.domain.OverheadLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OverheadLineItemRepository extends JpaRepository<OverheadLineItem, String> {

    List<OverheadLineItem> findAllByOrderBySortOrderAsc();
}
