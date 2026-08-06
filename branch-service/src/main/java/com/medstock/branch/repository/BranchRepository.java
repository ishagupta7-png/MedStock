package com.medstock.branch.repository;

import com.medstock.branch.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    /**
     * Case-insensitive because callers pass a city that a human typed or that came from another
     * service's stored value - an exact match quietly returned nothing for "pune" vs "Pune", which
     * reads as "no branches in that city" rather than as a lookup that failed.
     */
    List<Branch> findByCityIgnoreCase(String city);
}