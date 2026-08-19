package com.orderflow.repository;

import com.orderflow.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    // sku/name are pre-formatted %pattern% wildcards (or null), built in ProductService - binding
    // a lowercased pattern directly against lower(p.sku) keeps the parameter unambiguously typed
    // as a String, avoiding a Postgres/pgjdbc quirk where a null parameter wrapped in concat()/
    // lower() gets bound with no type hint and defaults to bytea ("function lower(bytea) does not
    // exist").
    @Query("select p from Product p where "
            + "(:sku is null or lower(p.sku) like :sku) "
            + "and (:name is null or lower(p.name) like :name)")
    Page<Product> search(@Param("sku") String sku, @Param("name") String name, Pageable pageable);
}
