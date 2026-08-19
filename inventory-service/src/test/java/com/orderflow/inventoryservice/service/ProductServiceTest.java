package com.orderflow.inventoryservice.service;

import com.orderflow.inventoryservice.dto.PagedResponse;
import com.orderflow.inventoryservice.dto.ProductResponse;
import com.orderflow.inventoryservice.entity.Inventory;
import com.orderflow.inventoryservice.entity.Product;
import com.orderflow.inventoryservice.repository.InventoryRepository;
import com.orderflow.inventoryservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryRepository inventoryRepository;

    private ProductService productService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, inventoryRepository);
    }

    @Test
    void listProducts_blankFilters_arePassedAsNull_toRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.search(isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        productService.listProducts("  ", "", pageable);

        verify(productRepository).search(isNull(), isNull(), eq(pageable));
    }

    @Test
    void listProducts_mapsProductsAndInventory_intoPagedResponse() {
        Pageable pageable = PageRequest.of(1, 2);
        Product product = new Product("SKU-1", "Widget", 1500L);
        setId(product, 1L);
        Inventory inventory = new Inventory(1L, 5);

        when(productRepository.search(eq("%sku%"), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 5));
        when(inventoryRepository.findById(1L)).thenReturn(Optional.of(inventory));

        PagedResponse<ProductResponse> response = productService.listProducts("SKU", null, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sku()).isEqualTo("SKU-1");
        assertThat(response.items().get(0).quantityAvailable()).isEqualTo(5);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
