package com.orderflow.inventoryservice.service;

import com.orderflow.inventoryservice.dto.CreateProductRequest;
import com.orderflow.inventoryservice.dto.PagedResponse;
import com.orderflow.inventoryservice.dto.ProductResponse;
import com.orderflow.inventoryservice.entity.Inventory;
import com.orderflow.inventoryservice.entity.Product;
import com.orderflow.inventoryservice.exception.ConflictException;
import com.orderflow.inventoryservice.exception.NotFoundException;
import com.orderflow.inventoryservice.repository.InventoryRepository;
import com.orderflow.inventoryservice.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final String PRODUCTS_CACHE = "products";

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    public ProductService(ProductRepository productRepository, InventoryRepository inventoryRepository) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @CacheEvict(value = PRODUCTS_CACHE, allEntries = true)
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("SKU already exists: " + request.sku());
        }
        Product product = productRepository.save(
                new Product(request.sku(), request.name(), request.priceCents()));
        Inventory inventory = inventoryRepository.save(
                new Inventory(product.getId(), request.initialQuantity()));
        return toResponse(product, inventory);
    }

    @Cacheable(value = PRODUCTS_CACHE,
            key = "'list:' + (#sku != null ? #sku : '') + ':' + (#name != null ? #name : '') "
                    + "+ ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public PagedResponse<ProductResponse> listProducts(String sku, String name, Pageable pageable) {
        Page<Product> page = productRepository.search(likePattern(sku), likePattern(name), pageable);
        Page<ProductResponse> mapped = page.map(p -> toResponse(p, inventoryRepository.findById(p.getId()).orElse(null)));
        return PagedResponse.from(mapped);
    }

    @Cacheable(value = PRODUCTS_CACHE, key = "'id:' + #id")
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        Inventory inventory = inventoryRepository.findById(id).orElse(null);
        return toResponse(product, inventory);
    }

    private String likePattern(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : "%" + normalized.toLowerCase() + "%";
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private ProductResponse toResponse(Product product, Inventory inventory) {
        Integer quantity = inventory == null ? 0 : inventory.getQuantityAvailable();
        return new ProductResponse(product.getId(), product.getSku(), product.getName(),
                product.getPriceCents(), quantity);
    }
}
