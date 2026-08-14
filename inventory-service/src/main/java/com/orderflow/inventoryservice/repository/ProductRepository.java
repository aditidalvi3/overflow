package com.orderflow.inventoryservice.repository;

import com.orderflow.inventoryservice.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    @Query("select p from Product p where "
            + "(:sku is null or lower(p.sku) like lower(concat('%', :sku, '%'))) "
            + "and (:name is null or lower(p.name) like lower(concat('%', :name, '%')))")
    Page<Product> search(@Param("sku") String sku, @Param("name") String name, Pageable pageable);
}
